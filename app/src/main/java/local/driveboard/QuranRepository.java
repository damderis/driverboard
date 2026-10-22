package local.driveboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fetches and caches complete surahs in Uthmani Arabic, Malay, and English. */
public final class QuranRepository {
    public static final String QURAN_UTHMANI = "quran-uthmani";
    public static final String MS_BASMEIH = "ms.basmeih";
    public static final String EN_SAHIH = "en.sahih";

    public interface Callback {
        /** Called on the main thread. A null document means the request can be retried. */
        void onResult(Document document);
    }

    public static final class Document {
        public final int surah;
        private final Map<String, String[]> editions;

        private Document(int surah, Map<String, String[]> editions) {
            this.surah = surah;
            this.editions = editions;
        }

        /** One-based ayah lookup; returns null for a missing edition or invalid ayah. */
        public String text(String edition, int ayah) {
            String[] verses = editions.get(edition);
            return verses != null && ayah >= 1 && ayah < verses.length ? verses[ayah] : null;
        }

        public String arabic(int ayah) {
            return text(QURAN_UTHMANI, ayah);
        }

        public String translation(int ayah) {
            return text(EN_SAHIH, ayah);
        }
    }

    private static final String TAG = "QuranRepository";
    private static final String[] EDITIONS = {QURAN_UTHMANI, MS_BASMEIH, EN_SAHIH};
    private static final int MAX_RESPONSE = 2 * 1024 * 1024;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Integer, Document> MEMORY = new LinkedHashMap<Integer, Document>(12, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Document> eldest) {
            return size() > 8;
        }
    };
    private static final Map<Integer, List<Callback>> WAITING = new HashMap<>();

    private QuranRepository() {}

    public static void request(Context context, int surah, Callback callback) {
        if (callback == null) return;
        if (QuranCatalog.get(surah) == null || context == null) {
            MAIN.post(() -> callback.onResult(null));
            return;
        }
        synchronized (QuranRepository.class) {
            Document cached = MEMORY.get(surah);
            if (cached != null) {
                MAIN.post(() -> callback.onResult(cached));
                return;
            }
            List<Callback> callbacks = WAITING.get(surah);
            if (callbacks != null) {
                callbacks.add(callback);
                return;
            }
            callbacks = new ArrayList<>();
            callbacks.add(callback);
            WAITING.put(surah, callbacks);
        }
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            Document result = readCache(app, surah);
            if (result != null) {
                CarDiagnostics.log(app, "quran surah=" + surah + " source=cache editions=3");
            }
            if (result == null) {
                try {
                    String json = fetch(surah);
                    result = parse(json, surah);
                    writeCache(app, surah, json);
                    CarDiagnostics.log(app, "quran surah=" + surah + " source=network editions=3");
                } catch (Exception e) {
                    Log.w(TAG, "Surah " + surah + " unavailable", e);
                    CarDiagnostics.log(app, "quran surah=" + surah + " source=error type="
                            + e.getClass().getSimpleName());
                }
            }
            List<Callback> callbacks;
            synchronized (QuranRepository.class) {
                if (result != null) MEMORY.put(surah, result);
                callbacks = WAITING.remove(surah);
            }
            Document ready = result;
            if (callbacks != null) {
                MAIN.post(() -> {
                    for (Callback waiting : callbacks) waiting.onResult(ready);
                });
            }
        });
    }

    private static String fetch(int surah) throws Exception {
        String address = "https://api.alquran.cloud/v1/surah/" + surah
                + "/editions/quran-uthmani,ms.basmeih,en.sahih";
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestProperty("User-Agent", "DriveBoard (personal Android Auto app)");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("Quran HTTP " + connection.getResponseCode());
            }
            return WidgetData.readLimited(connection, MAX_RESPONSE);
        } finally {
            connection.disconnect();
        }
    }

    /** Accept a response only when every expected edition has every ayah in order. */
    static Document parse(String json, int surah) throws Exception {
        QuranCatalog.Entry chapter = QuranCatalog.get(surah);
        if (chapter == null || json == null) throw new IOException("Invalid surah");
        JSONObject response = new JSONObject(json);
        if (response.optInt("code") != 200) throw new IOException("Quran API error");
        JSONArray data = response.optJSONArray("data");
        if (data == null || data.length() != EDITIONS.length) {
            throw new IOException("Incomplete Quran editions");
        }
        Map<String, String[]> texts = new HashMap<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.optJSONObject(i);
            JSONObject edition = entry == null ? null : entry.optJSONObject("edition");
            String identifier = edition == null ? "" : edition.optString("identifier", "");
            if (!isExpected(identifier) || texts.containsKey(identifier)
                    || entry.optInt("number") != surah
                    || entry.optInt("numberOfAyahs") != chapter.ayahCount) {
                throw new IOException("Wrong Quran edition or surah");
            }
            JSONArray ayahs = entry.optJSONArray("ayahs");
            if (ayahs == null || ayahs.length() != chapter.ayahCount) {
                throw new IOException("Incomplete Quran ayahs");
            }
            String[] verses = new String[chapter.ayahCount + 1];
            for (int ayah = 1; ayah <= chapter.ayahCount; ayah++) {
                JSONObject verse = ayahs.optJSONObject(ayah - 1);
                String text = verse == null ? "" : verse.optString("text", "").trim();
                if (verse == null || verse.optInt("numberInSurah") != ayah || text.isEmpty()) {
                    throw new IOException("Invalid Quran ayah " + ayah);
                }
                verses[ayah] = text;
            }
            texts.put(identifier, verses);
        }
        for (String edition : EDITIONS) {
            if (!texts.containsKey(edition)) throw new IOException("Missing Quran edition " + edition);
        }
        return new Document(surah, texts);
    }

    private static boolean isExpected(String identifier) {
        for (String edition : EDITIONS) if (edition.equals(identifier)) return true;
        return false;
    }

    private static File cacheFile(Context context, int surah, String edition) {
        return new File(new File(context.getCacheDir(), "quran-v1"),
                surah + "-" + edition + ".json");
    }

    private static Document readCache(Context context, int surah) {
        try {
            JSONArray data = new JSONArray();
            for (String edition : EDITIONS) {
                File file = cacheFile(context, surah, edition);
                if (!file.isFile() || file.length() > MAX_RESPONSE) {
                    return null;
                }
                String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                data.put(new JSONObject(json));
            }
            return parse(new JSONObject().put("code", 200).put("data", data).toString(), surah);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeCache(Context context, int surah, String json) {
        List<File> temporaryFiles = new ArrayList<>();
        try {
            File directory = cacheFile(context, surah, QURAN_UTHMANI).getParentFile();
            if (!directory.exists() && !directory.mkdirs()) {
                return;
            }
            JSONArray data = new JSONObject(json).getJSONArray("data");
            Map<String, JSONObject> entries = new HashMap<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject entry = data.getJSONObject(i);
                entries.put(entry.getJSONObject("edition").getString("identifier"), entry);
            }
            for (String edition : EDITIONS) {
                File temporary = File.createTempFile("surah-", ".tmp", directory);
                temporaryFiles.add(temporary);
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    output.write(entries.get(edition).toString().getBytes(StandardCharsets.UTF_8));
                    output.getFD().sync();
                }
            }
            for (int i = 0; i < EDITIONS.length; i++) {
                File target = cacheFile(context, surah, EDITIONS[i]);
                if (!temporaryFiles.get(i).renameTo(target)) {
                    Log.w(TAG, "Could not replace Quran cache " + target.getName());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not cache surah " + surah, e);
        } finally {
            for (File temporary : temporaryFiles) {
                if (temporary.exists()) {
                    temporary.delete();
                }
            }
        }
    }
}
