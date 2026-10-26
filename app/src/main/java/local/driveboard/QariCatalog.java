package local.driveboard;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionError;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Discovers reciters exposed by Quran for Android's browse service. */
public final class QariCatalog {
    public static final int PLAYABLE = 1;
    public static final int UNPLAYABLE = 2;
    public static final int UNKNOWN = 0;

    private static final String PACKAGE = "com.quran.labs.androidquran";
    private static final String SERVICE =
            "com.quran.labs.feature.autoquran.service.QuranBrowsableAudioPlaybackService";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final int BATCH_SIZE = 8;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(6);
    private static Attempt active;

    private QariCatalog() {}

    public static final class Entry {
        public final int id;
        public final String name;
        public final int status;

        public Entry(int id, String name, int status) {
            this.id = id;
            this.name = name;
            // Quran doesn't expose stream URIs; it labels per-ayah-only reciters "(gapped)" itself.
            this.status = status == UNKNOWN && name != null
                    && name.toLowerCase(java.util.Locale.ROOT).contains("(gapped)")
                    ? UNPLAYABLE : status;
        }
    }

    /** Returns the last successful catalog, or two safe defaults before discovery. */
    public static List<Entry> entries(Context context) {
        List<Entry> saved = read(context).entries;
        if (!saved.isEmpty()) return saved;
        List<Entry> fallback = new ArrayList<>();
        fallback.add(new Entry(1, "Husary", UNKNOWN));
        fallback.add(new Entry(0, "Minshawi", UNKNOWN));
        return Collections.unmodifiableList(fallback);
    }

    /** Refreshes at most once per day, including unsuccessful attempts. Callback runs on main. */
    public static void refresh(Context context, Runnable onUpdated) {
        Context app = context.getApplicationContext();
        MAIN.post(() -> {
            if (active != null) {
                if (onUpdated != null) active.callbacks.add(onUpdated);
                return;
            }
            Snapshot saved = read(app);
            long age = System.currentTimeMillis() - saved.attemptedAt;
            if (saved.attemptedAt > 0 && age >= 0 && age < DAY_MS) {
                if (onUpdated != null) onUpdated.run();
                return;
            }
            Attempt attempt = new Attempt(app);
            if (onUpdated != null) attempt.callbacks.add(onUpdated);
            active = attempt;
            attempt.connect();
        });
    }

    private static final class Snapshot {
        final long attemptedAt;
        final List<Entry> entries;

        Snapshot(long attemptedAt, List<Entry> entries) {
            this.attemptedAt = attemptedAt;
            this.entries = entries;
        }
    }

    private static Snapshot read(Context context) {
        try {
            String json = Store.qariCatalogJson(context);
            if (json == null || json.isEmpty()) return new Snapshot(0, Collections.emptyList());
            JSONObject root = new JSONObject(json);
            JSONArray array = root.optJSONArray("entries");
            List<Entry> found = new ArrayList<>();
            if (array != null) for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                int id = item.getInt("id");
                if (id < 0 || id > QariProbe.MAX_ID) continue;
                found.add(new Entry(id, item.getString("name"), item.optInt("status", UNKNOWN)));
            }
            return new Snapshot(root.optLong("attemptedAt"),
                    Collections.unmodifiableList(found));
        } catch (Exception error) {
            CarDiagnostics.log(context, "Qari catalog read failed " + error);
            return new Snapshot(0, Collections.emptyList());
        }
    }

    private static void save(Context context, List<Entry> entries) {
        try {
            JSONObject root = new JSONObject();
            root.put("attemptedAt", System.currentTimeMillis());
            JSONArray array = new JSONArray();
            for (Entry entry : entries) {
                JSONObject item = new JSONObject();
                item.put("id", entry.id);
                item.put("name", entry.name);
                item.put("status", entry.status);
                array.put(item);
            }
            root.put("entries", array);
            Store.saveQariCatalogJson(context, root.toString());
        } catch (Exception error) {
            CarDiagnostics.log(context, "Qari catalog save failed " + error);
        }
    }

    private static final class Attempt {
        final Context context;
        final List<Runnable> callbacks = new ArrayList<>();
        final Map<Integer, Candidate> found = new TreeMap<>();
        ListenableFuture<MediaBrowser> connection;
        MediaBrowser browser;
        int nextId;
        int highestHit = -1;
        int pending;
        boolean finished;

        Attempt(Context context) { this.context = context; }

        void connect() {
            try {
                SessionToken token = new SessionToken(context, new ComponentName(PACKAGE, SERVICE));
                connection = new MediaBrowser.Builder(context, token).buildAsync();
                ListenableFuture<MediaBrowser> future = connection;
                MAIN.postDelayed(() -> {
                    if (!finished && connection == future && browser == null) fail("connect timeout");
                }, 30000);
                future.addListener(() -> {
                    if (finished) return;
                    try {
                        browser = future.get();
                        scanBatch();
                    } catch (Exception error) {
                        fail("connect " + error);
                    }
                }, context.getMainExecutor());
            } catch (RuntimeException error) {
                fail("connect " + error);
            }
        }

        void scanBatch() {
            if (finished) return;
            if (QariProbe.complete(nextId, highestHit)) {
                if (found.isEmpty()) fail("no items");
                else checkUrls();
                return;
            }
            int end = QariProbe.batchEnd(nextId, highestHit, BATCH_SIZE);
            pending = end - nextId + 1;
            for (int id = nextId; id <= end; id++) {
                final int requestedId = id;
                try {
                    ListenableFuture<LibraryResult<MediaItem>> future =
                            browser.getItem("sura_1_" + id);
                    MAIN.postDelayed(() -> {
                        if (!finished && pending > 0 && !future.isDone()) fail("item timeout " + requestedId);
                    }, 10000);
                    future.addListener(() -> {
                        if (finished) return;
                        try {
                            LibraryResult<MediaItem> result = future.get();
                            if (result.resultCode == LibraryResult.RESULT_SUCCESS
                                    && result.value != null) {
                                MediaItem item = result.value;
                                found.put(requestedId, candidate(requestedId, item));
                                highestHit = Math.max(highestHit, requestedId);
                            } else if (result.resultCode != SessionError.ERROR_BAD_VALUE
                                    && !(result.resultCode == LibraryResult.RESULT_SUCCESS
                                    && result.value == null)) {
                                fail("item " + requestedId + " result " + result.resultCode);
                                return;
                            }
                        } catch (Exception error) {
                            fail("item " + requestedId + " " + error);
                            return;
                        }
                        if (--pending == 0) {
                            nextId = end + 1;
                            scanBatch();
                        }
                    }, context.getMainExecutor());
                } catch (RuntimeException error) {
                    fail("item request " + id + " " + error);
                    return;
                }
            }
        }

        Candidate candidate(int id, MediaItem item) {
            MediaMetadata meta = item.mediaMetadata;
            String artist = string(meta.artist);
            String subtitle = string(meta.subtitle);
            String title = string(meta.title);
            Uri metadataUri = item.requestMetadata.mediaUri;
            Uri localUri = item.localConfiguration == null ? null : item.localConfiguration.uri;
            Uri uri = metadataUri != null ? metadataUri : localUri;
            String source = metadataUri != null ? "requestMetadata" :
                    localUri != null ? "localConfiguration" : "none";
            CarDiagnostics.log(context, "Qari id=" + id + " uriSource=" + source);
            if (found.size() < 4) {
                CarDiagnostics.log(context, "Qari raw id=" + id + " artist=" + artist
                        + " subtitle=" + subtitle + " title=" + title + " uriSource=" + source
                        + " uri=" + uri);
            }
            String name = !artist.isEmpty() ? artist : !subtitle.isEmpty() ? subtitle
                    : !title.isEmpty() ? title : "Reciter " + id;
            return new Candidate(id, name, uri);
        }

        void checkUrls() {
            releaseBrowser();
            pending = found.size();
            for (Candidate candidate : found.values()) {
                NETWORK.execute(() -> {
                    int status = probe(candidate.uri);
                    MAIN.post(() -> {
                        if (finished) return;
                        candidate.status = status;
                        if (--pending == 0) {
                            List<Entry> result = new ArrayList<>();
                            for (Candidate value : found.values()) {
                                result.add(new Entry(value.id, value.name, value.status));
                            }
                            save(context, result);
                            CarDiagnostics.log(context, "Qari catalog found " + result.size());
                            finish();
                        }
                    });
                });
            }
        }

        void fail(String reason) {
            if (finished) return;
            CarDiagnostics.log(context, "Qari catalog failed " + reason);
            save(context, read(context).entries);
            finish();
        }

        void finish() {
            finished = true;
            releaseBrowser();
            if (active == this) active = null;
            for (Runnable callback : callbacks) {
                try { callback.run(); }
                catch (RuntimeException error) {
                    CarDiagnostics.log(context, "Qari callback failed " + error);
                }
            }
        }

        void releaseBrowser() {
            if (connection != null) {
                MediaController.releaseFuture(connection);
                connection = null;
                browser = null;
            }
        }
    }

    private static final class Candidate {
        final int id;
        final String name;
        final Uri uri;
        int status = UNKNOWN;

        Candidate(int id, String name, Uri uri) {
            this.id = id;
            this.name = name;
            this.uri = uri;
        }
    }

    private static String string(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private static int probe(Uri uri) {
        if (uri == null || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))) return UNKNOWN;
        long deadline = System.currentTimeMillis() + 6000;
        try {
            URL url = new URL(uri.toString());
            for (int redirects = 0; redirects <= 5; redirects++) {
                int remaining = (int) (deadline - System.currentTimeMillis());
                if (remaining <= 0) return UNKNOWN;
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                try {
                    connection.setRequestMethod("HEAD");
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(remaining);
                    connection.setReadTimeout(remaining);
                    int code = connection.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String location = connection.getHeaderField("Location");
                        if (location == null) return UNKNOWN;
                        url = new URL(url, location);
                        if (!"https".equalsIgnoreCase(url.getProtocol())
                                && !"http".equalsIgnoreCase(url.getProtocol())) return UNKNOWN;
                    } else if (code >= 200 && code < 300) return PLAYABLE;
                    else if (code >= 400 && code < 500) return UNPLAYABLE;
                    else return UNKNOWN;
                } finally {
                    connection.disconnect();
                }
            }
        } catch (Exception ignored) {
            return UNKNOWN;
        }
        return UNKNOWN;
    }
}
