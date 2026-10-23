package local.driveboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chapter timestamps refer to quranicaudio.com/qdc recordings, not every recording
 * of the same reciter. Never scale them to another track's duration. */
public final class QuranTimingRepository {
    public interface Callback {
        void onResult(Timing timing);
    }

    public static final class Timing {
        public final String audioUrl;
        private final long[] starts;
        private final long[] ends;

        private Timing(String audioUrl, long[] starts, long[] ends) {
            this.audioUrl = audioUrl;
            this.starts = starts;
            this.ends = ends;
        }

        public int ayahAt(long position) {
            return QuranPosition.lookup(starts, ends, position);
        }
    }

    private static final int MAX_RESPONSE = 4 * 1024 * 1024;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private QuranTimingRepository() {}

    public static void request(Context context, int recitation, int chapter, Callback callback) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            Timing result = null;
            File directory = new File(app.getCacheDir(), "quran-timings-v1");
            File file = new File(directory, recitation + "-" + chapter + ".json");
            try {
                if (file.isFile() && file.length() <= MAX_RESPONSE) {
                    result = parse(new String(Files.readAllBytes(file.toPath()),
                            StandardCharsets.UTF_8), chapter);
                }
            } catch (Exception ignored) {
                // A corrupt cache is retried from the network.
            }
            if (result == null) {
                try {
                    String json = fetch(recitation, chapter);
                    result = parse(json, chapter);
                    if (directory.isDirectory() || directory.mkdirs()) {
                        File temporary = File.createTempFile("timing-", ".tmp", directory);
                        try {
                            try (FileOutputStream output = new FileOutputStream(temporary)) {
                                output.write(json.getBytes(StandardCharsets.UTF_8));
                                output.getFD().sync();
                            }
                            if (!temporary.renameTo(file)) {
                                CarDiagnostics.log(app, "quran timings cache write failed");
                            }
                        } finally {
                            temporary.delete();
                        }
                    }
                } catch (Exception e) {
                    CarDiagnostics.log(app, "quran timings recitation=" + recitation
                            + " chapter=" + chapter + " error=" + e.getClass().getSimpleName());
                }
            }
            Timing ready = result;
            if (ready != null) {
                CarDiagnostics.log(app, "quran timings recitation=" + recitation
                        + " chapter=" + chapter + " audio=" + ready.audioUrl
                        + " recording may differ from player stream");
            }
            MAIN.post(() -> callback.onResult(ready));
        });
    }

    private static String fetch(int recitation, int chapter) throws Exception {
        URL url = new URL("https://api.quran.com/api/v4/chapter_recitations/"
                + recitation + "/" + chapter + "?segments=true");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Timing HTTP error");
            return WidgetData.readLimited(connection, MAX_RESPONSE);
        } finally {
            connection.disconnect();
        }
    }

    static Timing parse(String json, int chapter) throws Exception {
        QuranCatalog.Entry entry = QuranCatalog.get(chapter);
        if (entry == null) throw new IOException("Unknown chapter");
        JSONObject audio = new JSONObject(json).getJSONObject("audio_file");
        if (audio.getInt("chapter_id") != chapter) throw new IOException("Wrong chapter");
        String url = audio.getString("audio_url");
        JSONArray timestamps = audio.getJSONArray("timestamps");
        if (timestamps.length() != entry.ayahCount) throw new IOException("Incomplete timestamps");
        long[] starts = new long[entry.ayahCount];
        long[] ends = new long[entry.ayahCount];
        for (int i = 0; i < starts.length; i++) {
            JSONObject verse = timestamps.getJSONObject(i);
            starts[i] = verse.getLong("timestamp_from");
            ends[i] = verse.getLong("timestamp_to");
            if (!verse.getString("verse_key").equals(chapter + ":" + (i + 1))
                    || starts[i] < 0 || ends[i] <= starts[i]
                    || (i > 0 && (starts[i] < starts[i - 1] || ends[i] < ends[i - 1]))) {
                throw new IOException("Invalid verse timing");
            }
        }
        return new Timing(url, starts, ends);
    }
}
