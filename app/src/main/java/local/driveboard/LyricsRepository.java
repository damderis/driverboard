package local.driveboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One off-main LRCLIB lookup per track, with bounded app cache. */
public final class LyricsRepository {
    public interface Callback {
        void onResult(LyricsDocument document);
    }

    private static final String TAG = "LyricsRepository";
    private static final String BASE = "https://lrclib.net/api/";
    private static final int MAX_FILES = 24;
    private static final int MAX_RESPONSE = 256 * 1024;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, LyricsDocument> MEMORY = new LinkedHashMap<String, LyricsDocument>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, LyricsDocument> entry) {
            return size() > MAX_FILES;
        }
    };
    private static final Map<String, List<Callback>> WAITING = new HashMap<>();

    private static final class ProviderFailure extends Exception {
        final LyricsDocument available;
        final String source;

        ProviderFailure(LyricsDocument available, Exception cause) {
            this(available, cause, "lrclib");
        }

        ProviderFailure(LyricsDocument available, Exception cause, String source) {
            super(cause);
            this.available = available;
            this.source = source;
        }
    }

    private LyricsRepository() {}

    public static String key(WidgetData.MediaSnapshot media) {
        // v3: normalized metadata and the second provider change previous misses.
        return "v3\u0000" + media.title.trim().toLowerCase(Locale.ROOT) + "\u0000"
                + media.artist.trim().toLowerCase(Locale.ROOT) + "\u0000"
                + Math.max(0, Math.round(media.duration / 1000f));
    }

    public static void request(Context context, WidgetData.MediaSnapshot media, Callback callback) {
        if (media == null || !media.hasTrack) {
            MAIN.post(() -> callback.onResult(null));
            return;
        }
        String key = key(media);
        synchronized (LyricsRepository.class) {
            if (MEMORY.containsKey(key)) {
                LyricsDocument cached = MEMORY.get(key);
                MAIN.post(() -> callback.onResult(cached));
                return;
            }
            List<Callback> callbacks = WAITING.get(key);
            if (callbacks != null) {
                callbacks.add(callback);
                return;
            }
            callbacks = new ArrayList<>();
            callbacks.add(callback);
            WAITING.put(key, callbacks);
        }
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            LyricsDocument result = null;
            boolean failed = false;
            try {
                result = readCache(app, key);
                if (result == null) {
                    result = lookup(app, media);
                    writeCache(app, key, result);
                } else {
                    logResult(app, "cache", "stored", result);
                }
            } catch (ProviderFailure e) {
                result = e.available;
                failed = true;
                CarDiagnostics.log(app, "lyrics source=" + e.source + " match=partial timed="
                        + (result != null && result.isTimed()) + " fallback=error");
            } catch (Exception e) {
                // Network or "server busy" errors are retried next time, not remembered as no lyrics.
                failed = true;
                Log.w(TAG, "Lyrics lookup unavailable", e);
                CarDiagnostics.log(app, "lyrics source=network match=error timed=false "
                        + e.getClass().getSimpleName());
            }
            LyricsDocument finalResult = result == null || result.isEmpty() ? null : result;
            List<Callback> callbacks;
            synchronized (LyricsRepository.class) {
                if (!failed) {
                    MEMORY.put(key, finalResult);
                }
                callbacks = WAITING.remove(key);
            }
            if (callbacks != null) {
                MAIN.post(() -> {
                    for (Callback waiting : callbacks) {
                        waiting.onResult(finalResult);
                    }
                });
            }
        });
    }

    private static LyricsDocument lookup(Context context, WidgetData.MediaSnapshot media) throws Exception {
        String title = LyricsMatch.title(media.title);
        String artist = LyricsMatch.leadArtist(media.artist);
        // Players' album names rarely match LRCLIB, so exact lookups skip them.
        String query = "track_name=" + encode(title) + "&artist_name=" + encode(artist);
        String[] exactQueries = media.duration > 0
                ? new String[] {query + "&duration=" + Math.round(media.duration / 1000f), query}
                : new String[] {query};
        LyricsDocument best = null;
        int bestScore = Integer.MIN_VALUE;
        String bestMatch = "none";
        Exception lookupFailure = null;
        List<JSONObject> matches = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        java.util.HashSet<String> exactIds = new java.util.HashSet<>();
        for (String exactQuery : exactQueries) {
            JSONObject exact = null;
            try {
                exact = getObject(BASE + "get?" + exactQuery);
            } catch (Exception e) {
                Log.w(TAG, "Exact lyrics lookup failed; trying search", e);
                lookupFailure = e;
            }
            if (exact != null && LyricsMatch.sameTitle(exact.optString("trackName"), title)
                    && LyricsMatch.sameArtist(exact.optString("artistName"), artist)
                    && seen.add(exact.optString("id", "") + "\u0000"
                            + exact.optString("duration", ""))) {
                matches.add(exact);
                exactIds.add(exact.optString("id", ""));
            }
        }
        String[] searches = {
                BASE + "search?q=" + encode(title + " " + artist),
                BASE + "search?track_name=" + encode(title)
        };
        for (String search : searches) {
            JSONArray candidates;
            try {
                candidates = getArray(search);
            } catch (Exception e) {
                Log.w(TAG, "Lyrics search failed; trying remaining sources", e);
                lookupFailure = e;
                continue;
            }
            if (candidates == null) continue;
            for (int i = 0; i < Math.min(candidates.length(), 50); i++) {
                JSONObject entry = candidates.optJSONObject(i);
                if (entry != null && LyricsMatch.sameTitle(entry.optString("trackName"), title)
                        && LyricsMatch.sameArtist(entry.optString("artistName"), artist)
                        && seen.add(entry.optString("id", "") + "\u0000"
                                + entry.optString("duration", ""))) {
                    matches.add(entry);
                }
            }
        }
        for (JSONObject entry : matches) {
            double duration = entry.optDouble("duration", 0);
            if (!LyricsMatch.durationMatches(media.duration, duration, matches.size())) continue;
            LyricsDocument parsed = fromJson(entry);
            if (parsed == null) continue;
            int score = parsed.isTimed() ? 100 : 0;
            if (media.duration > 0 && duration > 0) {
                score -= Math.round(Math.abs(media.duration / 1000f - (float) duration));
            }
            if (entry.optString("albumName", "").equalsIgnoreCase(media.album)) score += 3;
            if (score > bestScore) {
                best = parsed;
                bestScore = score;
                bestMatch = exactIds.contains(entry.optString("id", "")) ? "exact" : "search";
            }
        }
        if (best == null || !best.isTimed()) {
            LyricsDocument fallback;
            try {
                fallback = lookupNetEase(media, title, artist);
            } catch (ProviderFailure e) {
                logResult(context, e.available == null ? "lrclib" : "netease",
                        "search-partial", e.available == null ? best : e.available);
                throw new ProviderFailure(e.available != null ? e.available : best, e,
                        e.available != null ? e.source : "lrclib");
            } catch (Exception e) {
                // Return any LRCLIB plain text, but don't cache while the fallback is down.
                throw new ProviderFailure(best, e);
            }
            if (fallback != null && (best == null || fallback.isTimed())) {
                logResult(context, "netease", "search", fallback);
                if (lookupFailure != null) {
                    throw new ProviderFailure(fallback, lookupFailure, "netease");
                }
                return fallback;
            }
        }
        logResult(context, best == null ? "none" : "lrclib", bestMatch, best);
        if (lookupFailure != null) {
            throw new ProviderFailure(best, lookupFailure);
        }
        return best;
    }

    private static LyricsDocument lookupNetEase(WidgetData.MediaSnapshot media,
            String title, String artist) throws Exception {
        JSONObject response = getObject("https://music.163.com/api/search/get?s="
                + encode(title + " " + artist) + "&type=1&limit=10");
        if (response == null || response.optInt("code", -1) != 200) {
            throw new java.io.IOException("NetEase search unavailable");
        }
        JSONObject result = response == null ? null : response.optJSONObject("result");
        JSONArray songs = result == null ? null : result.optJSONArray("songs");
        if (songs == null) throw new java.io.IOException("NetEase search missing songs");
        List<JSONObject> matches = new ArrayList<>();
        for (int i = 0; i < songs.length(); i++) {
            JSONObject song = songs.optJSONObject(i);
            if (song == null || !LyricsMatch.sameTitle(song.optString("name"), title)) continue;
            JSONArray artists = song.optJSONArray("artists");
            JSONObject first = artists == null ? null : artists.optJSONObject(0);
            if (first != null && LyricsMatch.sameArtist(first.optString("name"), artist)) {
                matches.add(song);
            }
        }
        LyricsDocument best = null;
        double bestDiff = Double.MAX_VALUE;
        Exception candidateFailure = null;
        for (JSONObject song : matches) {
            double seconds = song.optDouble("duration", 0) / 1000.0;
            if (seconds <= 0 || (media.duration > 0
                    && Math.abs(media.duration / 1000.0 - seconds) > 12)) continue;
            long id = song.optLong("id", 0);
            if (id <= 0) continue;
            JSONObject lyric;
            try {
                lyric = getObject("https://music.163.com/api/song/lyric?id=" + id
                        + "&lv=1");
                if (lyric == null || lyric.optInt("code", -1) != 200) {
                    throw new java.io.IOException("NetEase lyric unavailable");
                }
            } catch (Exception e) {
                candidateFailure = e;
                continue;
            }
            JSONObject lrc = lyric == null ? null : lyric.optJSONObject("lrc");
            LyricsDocument parsed = LyricsDocument.parse(lrc == null ? "" : lrc.optString("lyric"), "");
            if (parsed.isEmpty() || !parsed.isTimed()) continue;
            double diff = media.duration <= 0 ? 0 : Math.abs(media.duration / 1000.0 - seconds);
            if (best == null || (parsed.isTimed() && !best.isTimed()) || diff < bestDiff) {
                best = parsed;
                bestDiff = diff;
            }
        }
        if (candidateFailure != null) {
            throw new ProviderFailure(best, candidateFailure, "netease");
        }
        return best;
    }

    private static void logResult(Context context, String source, String match,
            LyricsDocument document) {
        CarDiagnostics.log(context, "lyrics source=" + source + " match=" + match
                + " timed=" + (document != null && document.isTimed()));
    }

    private static LyricsDocument fromJson(JSONObject object) {
        if (object == null || object.optBoolean("instrumental", false)) {
            return null;
        }
        LyricsDocument result = LyricsDocument.parse(nullableString(object, "syncedLyrics"),
                nullableString(object, "plainLyrics"));
        return result.isEmpty() ? null : result;
    }

    private static String nullableString(JSONObject object, String field) {
        return object.isNull(field) ? "" : object.optString(field, "");
    }

    private static JSONObject getObject(String url) throws Exception {
        String json = fetch(url);
        return json == null ? null : new JSONObject(json);
    }

    private static JSONArray getArray(String url) throws Exception {
        String json = fetch(url);
        return json == null ? null : new JSONArray(json);
    }

    private static String fetch(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        boolean netease = address.startsWith("https://music.163.com/");
        connection.setRequestProperty("User-Agent", netease
                ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36"
                : "DriveBoard (personal Android Auto app)");
        if (netease) connection.setRequestProperty("Referer", "https://music.163.com/");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        try {
            int status = connection.getResponseCode();
            if (status == 404 && address.startsWith(BASE + "get?")) {
                return null;
            }
            if (status != 200) {
                throw new java.io.IOException("Lyrics HTTP " + status);
            }
            return WidgetData.readLimited(connection, MAX_RESPONSE);
        } finally {
            connection.disconnect();
        }
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static File cacheDir(Context context) {
        return new File(context.getCacheDir(), "lyrics");
    }

    private static File cacheFile(Context context, String key) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte b : digest) {
            name.append(String.format(Locale.ROOT, "%02x", b & 255));
        }
        return new File(cacheDir(context), name + ".json");
    }

    private static LyricsDocument readCache(Context context, String key) {
        try {
            File file = cacheFile(context, key);
            if (!file.isFile() || file.length() > MAX_RESPONSE) {
                return null;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            JSONObject entry = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            file.setLastModified(System.currentTimeMillis());
            if (entry.optBoolean("negative", false)) {
                return LyricsDocument.parse("", "");
            }
            return LyricsDocument.parse(nullableString(entry, "synced"),
                    nullableString(entry, "plain"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeCache(Context context, String key, LyricsDocument document) {
        try {
            File dir = cacheDir(context);
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            // Timed lines are reconstructed as LRC to keep the cache independent of API payloads.
            StringBuilder synced = new StringBuilder();
            if (document != null) {
                for (LyricsDocument.Line line : document.lines) {
                    synced.append(String.format(Locale.ROOT, "[%02d:%02d.%03d]",
                            line.timeMs / 60_000, line.timeMs / 1000 % 60,
                            line.timeMs % 1000)).append(line.text).append('\n');
                }
            }
            JSONObject entry = document == null
                    ? new JSONObject().put("negative", true)
                    : new JSONObject().put("synced", synced.toString())
                            .put("plain", document.plainText);
            byte[] bytes = entry.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_RESPONSE) {
                return;
            }
            try (FileOutputStream out = new FileOutputStream(cacheFile(context, key))) {
                out.write(bytes);
            }
            File[] files = dir.listFiles((parent, name) -> name.endsWith(".json"));
            if (files == null) {
                return;
            }
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = MAX_FILES; i < files.length; i++) {
                files[i].delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not cache lyrics", e);
        }
    }
}
