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

    private LyricsRepository() {}

    public static String key(WidgetData.MediaSnapshot media) {
        // v2: earlier builds cached misses caused by album and artist matching bugs.
        return "v2\u0000" + media.title.trim().toLowerCase(Locale.ROOT) + "\u0000"
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
                    result = lookup(media);
                    writeCache(app, key, result);
                }
            } catch (Exception e) {
                // Network or "server busy" errors are retried next time, not remembered as no lyrics.
                failed = true;
                Log.w(TAG, "Lyrics lookup unavailable", e);
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

    private static LyricsDocument lookup(WidgetData.MediaSnapshot media) throws Exception {
        // Players' album names ("Playback - AH") rarely match LRCLIB, so exact lookups skip them.
        String query = "track_name=" + encode(media.title) + "&artist_name=" + encode(media.artist);
        String[] exactQueries = media.duration > 0
                ? new String[] {query + "&duration=" + Math.round(media.duration / 1000f), query}
                : new String[] {query};
        for (String exactQuery : exactQueries) {
            JSONObject exact = null;
            try {
                exact = getObject(BASE + "get?" + exactQuery);
            } catch (Exception e) {
                Log.w(TAG, "Exact lyrics lookup failed; trying search", e);
            }
            LyricsDocument document = fromJson(exact);
            if (document != null && !document.isEmpty()) {
                return document;
            }
        }

        JSONArray candidates = getArray(BASE + "search?track_name=" + encode(media.title));
        if (candidates == null) {
            return null;
        }
        LyricsDocument best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < Math.min(candidates.length(), 30); i++) {
            JSONObject entry = candidates.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String title = entry.optString("trackName", "").trim();
            String artist = entry.optString("artistName", "").trim();
            if (!title.equalsIgnoreCase(media.title.trim())
                    || !LyricsDocument.sameArtist(artist, media.artist)) {
                continue;
            }
            int score = 0;
            if (media.duration > 0) {
                long difference = Math.abs(Math.round(media.duration / 1000f)
                        - Math.round(entry.optDouble("duration", 0)));
                if (difference > 12) {
                    continue;
                }
                score -= difference;
            }
            LyricsDocument parsed = fromJson(entry);
            if (parsed == null || parsed.isEmpty()) {
                continue;
            }
            if (parsed.isTimed()) {
                score += 20;
            }
            if (entry.optString("albumName", "").equalsIgnoreCase(media.album)) {
                score += 3;
            }
            if (score > bestScore) {
                bestScore = score;
                best = parsed;
            }
        }
        return best;
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
        connection.setRequestProperty("User-Agent", "DriveBoard (personal Android Auto app)");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        try {
            int status = connection.getResponseCode();
            if (status == 404) {
                return null;
            }
            if (status != 200) {
                throw new java.io.IOException("LRCLIB HTTP " + status);
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
