package local.driveboard;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared data source. All network work is serialized off the UI/surface thread. */
public final class WidgetData {
    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static String inFlight = "", lastAttempt = "";
    private static long retryAfter;
    private WidgetData() {}

    public static boolean hasMediaAccess(Context c) {
        return c.getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(
            new ComponentName(c, MediaAccessService.class));
    }
    public static String[] media(Context c) {
        if (!hasMediaAccess(c)) return new String[]{"Grant access on phone", "Notification access required"};
        try {
            MediaSessionManager manager = c.getSystemService(MediaSessionManager.class);
            for (MediaController controller : manager.getActiveSessions(new ComponentName(c, MediaAccessService.class))) {
                PlaybackState state = controller.getPlaybackState();
                if (state == null || state.getState() != PlaybackState.STATE_PLAYING) continue;
                MediaMetadata m = controller.getMetadata();
                if (m == null) return new String[]{"Playing", "—"};
                String title = m.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.trim().isEmpty()) title = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                return new String[]{nonempty(title, "Playing"), nonempty(artist, "—")};
            }
        } catch (RuntimeException ignored) { /* Permission may be revoked between checks. */ }
        return new String[]{"Nothing playing", "Start music on your phone"};
    }
    private static String nonempty(String s, String fallback) { return s == null || s.trim().isEmpty() ? fallback : s; }

    public static String zone(Context c) {
        String z = Store.prefs(c).getString("zone", "WLY01");
        return z != null && z.matches("[A-Z]{3}[0-9]{2}") ? z : "WLY01";
    }
    public static String[] prayer(Context c) {
        SharedPreferences prefs = Store.prefs(c);
        String zone = zone(c);
        ZonedDateTime now = ZonedDateTime.now(MALAYSIA);
        String day = now.toLocalDate().toString();
        String key = zone + ":" + day + ":" + prefs.getLong("prayer_refresh_" + zone, 0);
        String cached = prefs.getString("prayer_cache_" + zone, "");
        if (!day.equals(prefs.getString("prayer_day_" + zone, "")) || prefs.getLong("prayer_refresh_" + zone, 0) != 0) fetch(c.getApplicationContext(), zone, day, key);
        if (cached.isEmpty()) return new String[]{"—", zone + " · waiting for prayer times", ""};
        try {
            JSONObject entry = new JSONObject(cached).getJSONArray("prayerTime").getJSONObject(0);
            boolean today = day.equals(prefs.getString("prayer_day_" + zone, ""));
            String[] fields = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
            String[] names = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};
            for (int i=0; i<fields.length; i++) {
                LocalTime time = LocalTime.parse(entry.getString(fields[i]));
                if (today && time.isAfter(now.toLocalTime())) {
                    long seconds = java.time.Duration.between(now.toLocalTime(), time).getSeconds();
                    String countdown = String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds/3600, seconds/60%60, seconds%60);
                    return new String[]{names[i] + " · " + time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())), countdown + " remaining", zone + " · Malaysia time"};
                }
            }
            if (!today) return new String[]{"Cached · " + names[0] + " " + entry.getString("fajr").substring(0,5), "Offline · " + prefs.getString("prayer_day_"+zone,""), zone};
            LocalTime fajr = LocalTime.parse(entry.getString("fajr"));
            long seconds = java.time.Duration.between(now, now.toLocalDate().plusDays(1).atTime(fajr).atZone(MALAYSIA)).getSeconds();
            return new String[]{"Subuh · ~" + fajr.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())),
                String.format(Locale.getDefault(), "%02d:%02d:%02d remaining", seconds/3600, seconds/60%60, seconds%60),
                zone + " · tomorrow estimated until midnight"};
        } catch (Exception ignored) { return new String[]{"—", zone + " · prayer times unavailable", ""}; }
    }
    private static synchronized void fetch(Context c, String zone, String day, String key) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (!inFlight.isEmpty() || (key.equals(lastAttempt) && now < retryAfter)) return;
        inFlight = key; lastAttempt = key; retryAfter = now + 60_000;
        NETWORK.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=today&zone=" + zone);
                connection = (HttpURLConnection)url.openConnection();
                connection.setConnectTimeout(10_000); connection.setReadTimeout(10_000);
                if (connection.getResponseCode() != 200) return;
                String json;
                try (java.io.InputStream stream = connection.getInputStream(); java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = stream.read(buffer)) != -1) {
                        bytes.write(buffer, 0, count);
                        if (bytes.size() > 128 * 1024) throw new java.io.IOException("Prayer response too large");
                    }
                    json = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                }
                JSONObject root = new JSONObject(json);
                if (!"OK!".equals(root.optString("status")) || !zone.equals(root.optString("zone"))) return;
                JSONArray times = root.getJSONArray("prayerTime");
                if (times.length() != 1) return;
                JSONObject entry = times.getJSONObject(0);
                for (String field : new String[]{"fajr","dhuhr","asr","maghrib","isha"}) LocalTime.parse(entry.getString(field));
                // A response crossing midnight must be fetched again for the new date.
                if (!LocalDate.now(MALAYSIA).toString().equals(day)) return;
                String serverDay = root.optString("serverTime");
                if (!serverDay.startsWith(day)) return;
                Store.prefs(c).edit().putString("prayer_cache_"+zone,json).putString("prayer_day_"+zone,day).remove("prayer_refresh_"+zone).apply();
            } catch (Exception ignored) { /* Keep the last successful daily response on every network/parse failure. */ }
            finally {
                if (connection != null) connection.disconnect();
                synchronized (WidgetData.class) { inFlight = ""; }
            }
        });
    }
}
