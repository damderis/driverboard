package local.driveboard;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

/** Shared board data. Network requests never run on a drawing thread. */
public final class WidgetData {
    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final String[] FIELDS = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
    private static final String[] NAMES = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};
    private static String inFlight = "";
    private static String lastAttempt = "";
    private static long retryAfter;

    private WidgetData() {}

    public static final class PrayerRow {
        public final String name;
        public final String time;
        public final boolean past;
        public final boolean next;

        public PrayerRow(String name, String time, boolean past, boolean next) {
            this.name = name;
            this.time = time;
            this.past = past;
            this.next = next;
        }
    }

    public static final class PrayerSnapshot {
        public final PrayerRow[] rows;
        public final String zoneName;
        public final String zoneCode;
        public final String countdown;
        public final String status;
        public final String updated;

        public PrayerSnapshot(PrayerRow[] rows, String zoneName, String zoneCode,
                String countdown, String status, String updated) {
            this.rows = rows;
            this.zoneName = zoneName;
            this.zoneCode = zoneCode;
            this.countdown = countdown;
            this.status = status;
            this.updated = updated;
        }
    }

    public static final class BatterySnapshot {
        public final int percent;
        public final int rangeMeters;
        public final String status;
        public final String updated;

        public BatterySnapshot(int percent, int rangeMeters, String status, String updated) {
            this.percent = percent;
            this.rangeMeters = rangeMeters;
            this.status = status;
            this.updated = updated;
        }
    }

    public static boolean hasMediaAccess(Context c) {
        NotificationManager m = c.getSystemService(NotificationManager.class);
        return m != null && m.isNotificationListenerAccessGranted(
                new ComponentName(c, MediaAccessService.class));
    }

    public static final class MediaSnapshot {
        public final String title;
        public final String artist;
        public final Bitmap art;
        public final long duration;
        public final long position;
        public final long updatedAt;
        public final float speed;
        public final boolean playing;

        public MediaSnapshot(String title, String artist, Bitmap art, long duration,
                long position, long updatedAt, float speed, boolean playing) {
            this.title = title;
            this.artist = artist;
            this.art = art;
            this.duration = duration;
            this.position = position;
            this.updatedAt = updatedAt;
            this.speed = speed;
            this.playing = playing;
        }
    }

    public static MediaSnapshot mediaSnapshot(Context c) {
        Context lc = AppLocale.wrap(c);
        if (!hasMediaAccess(c)) {
            return new MediaSnapshot(lc.getString(R.string.media_grant_access),
                    lc.getString(R.string.media_notification_required), null,
                    0, 0, 0, 0, false);
        }
        if (hasMediaAccess(c)) {
            try {
                MediaSessionManager manager = c.getSystemService(MediaSessionManager.class);
                for (MediaController controller : manager.getActiveSessions(
                        new ComponentName(c, MediaAccessService.class))) {
                    PlaybackState state = controller.getPlaybackState();
                    if (state == null || state.getState() != PlaybackState.STATE_PLAYING) {
                        continue;
                    }
                    MediaMetadata data = controller.getMetadata();
                    if (data == null) {
                        return new MediaSnapshot(lc.getString(R.string.media_playing),
                                "\u2014", null, 0, state.getPosition(),
                                state.getLastPositionUpdateTime(), state.getPlaybackSpeed(), true);
                    }
                    Bitmap art = data.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (art == null) {
                        art = data.getBitmap(MediaMetadata.METADATA_KEY_ART);
                    }
                    if (art == null) {
                        art = data.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                    }
                    String title = nonempty(data.getString(MediaMetadata.METADATA_KEY_TITLE),
                            data.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
                    return new MediaSnapshot(nonempty(title, lc.getString(R.string.media_playing)),
                            nonempty(data.getString(MediaMetadata.METADATA_KEY_ARTIST), "\u2014"),
                            art, data.getLong(MediaMetadata.METADATA_KEY_DURATION),
                            state.getPosition(), state.getLastPositionUpdateTime(),
                            state.getPlaybackSpeed(), true);
                }
            } catch (RuntimeException ignored) {
                // Permission or the session may disappear during the snapshot.
            }
        }
        return new MediaSnapshot(lc.getString(R.string.media_nothing_playing), "", null,
                0, 0, 0, 0, false);
    }

    public static String[] media(Context c) {
        Context lc = AppLocale.wrap(c);
        if (!hasMediaAccess(c)) {
            return new String[] {lc.getString(R.string.media_grant_access),
                    lc.getString(R.string.media_notification_required), "state"};
        }
        try {
            MediaSessionManager m = c.getSystemService(MediaSessionManager.class);
            for (MediaController controller : m.getActiveSessions(
                    new ComponentName(c, MediaAccessService.class))) {
                PlaybackState state = controller.getPlaybackState();
                if (state == null || state.getState() != PlaybackState.STATE_PLAYING) {
                    continue;
                }
                MediaMetadata data = controller.getMetadata();
                if (data == null) {
                    return new String[] {lc.getString(R.string.media_playing), "—", "state"};
                }
                String title = data.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.trim().isEmpty()) {
                    title = data.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                }
                boolean hasTitle = title != null && !title.trim().isEmpty();
                return new String[] {nonempty(title, lc.getString(R.string.media_playing)),
                        nonempty(data.getString(MediaMetadata.METADATA_KEY_ARTIST), "—"),
                        hasTitle ? "data" : "state"};
            }
        } catch (RuntimeException ignored) {
            // Permission may change during query.
        }
        return new String[] {lc.getString(R.string.media_nothing_playing),
                lc.getString(R.string.media_start_music), "state"};
    }

    private static String nonempty(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s;
    }

    public static String zone(Context c) {
        String z = Store.prefs(c).getString("zone", "WLY01");
        return z != null && z.matches("[A-Z]{3}[0-9]{2}") ? z : "WLY01";
    }

    public static String zoneName(Context c) {
        String name = Store.prefs(c).getString("zone_name", null);
        return name == null || name.trim().isEmpty() ? ZoneCatalog.name(zone(c)) : name;
    }

    public static void selectZone(Context c, String code, String name) {
        if (code == null || !code.matches("[A-Z]{3}[0-9]{2}")) {
            return;
        }
        SharedPreferences p = Store.prefs(c);
        SharedPreferences.Editor edit = p.edit().putString("zone", code)
                .putString("zone_name", name == null || name.trim().isEmpty()
                        ? ZoneCatalog.name(code) : name.trim());
        if (!code.equals(zone(c))) {
            edit.putLong("prayer_refresh_" + code, System.currentTimeMillis());
        }
        edit.apply();
        checkPrayerRefresh(c);
    }

    /** Call on phone resume; successful fetches are rechecked every twelve hours. */
    public static void checkPrayerRefresh(Context c) {
        String z = zone(c);
        String day = LocalDate.now(MALAYSIA).toString();
        SharedPreferences p = Store.prefs(c);
        long checked = p.getLong("prayer_checked_" + z, 0);
        if (!day.equals(p.getString("prayer_day_" + z, ""))
                || p.getLong("prayer_refresh_" + z, 0) != 0
                || System.currentTimeMillis() - checked >= 12L * 60 * 60 * 1000) {
            fetch(c.getApplicationContext(), z, day);
        }
    }
    public static PrayerSnapshot prayerSnapshot(Context c) {
        checkPrayerRefresh(c);
        SharedPreferences p = Store.prefs(c);
        String z = zone(c);
        String day = LocalDate.now(MALAYSIA).toString();
        String json = p.getString("prayer_cache_" + z, "");
        boolean today = day.equals(p.getString("prayer_day_" + z, ""));
        String updated = formatUpdated(p.getLong("prayer_checked_" + z, 0));
        PrayerRow[] empty = new PrayerRow[5];
        for (int i = 0; i < 5; i++) {
            empty[i] = new PrayerRow(NAMES[i], "—", false, false);
        }
        if (json == null || json.isEmpty()) {
            return new PrayerSnapshot(empty, zoneName(c), z, "—", "waiting", "");
        }
        try {
            LocalTime[] times = parseTimes(
                    new JSONObject(json).getJSONArray("prayerTime").getJSONObject(0));
            PrayerRow[] rows = new PrayerRow[5];
            LocalTime now = ZonedDateTime.now(MALAYSIA).toLocalTime();
            int next = -1;
            if (today) {
                for (int i = 0; i < 5; i++) {
                    if (times[i].isAfter(now)) {
                        next = i;
                        break;
                    }
                }
            }
            for (int i = 0; i < 5; i++) {
                rows[i] = new PrayerRow(NAMES[i],
                        times[i].format(DateTimeFormatter.ofPattern("HH:mm")),
                        today && !times[i].isAfter(now), i == next);
            }
            String countdown = "—";
            if (next >= 0) {
                long seconds = Duration.between(now, times[next]).getSeconds();
                countdown = AppLocale.wrap(c).getString(
                        R.string.prayer_countdown, seconds / 3600, seconds / 60 % 60);
            }
            return new PrayerSnapshot(rows, zoneName(c), z, countdown,
                    today ? (next < 0 ? "tomorrow_waiting" : "live") : "stale", updated);
        } catch (Exception ignored) {
            return new PrayerSnapshot(empty, zoneName(c), z, "—", "unavailable", updated);
        }
    }
    public static String[] prayer(Context c) {
        PrayerSnapshot s = prayerSnapshot(c);
        Context lc = AppLocale.wrap(c);
        for (PrayerRow row : s.rows) {
            if (row.next) {
                return new String[] {row.name + " · " + row.time, s.countdown, s.zoneName};
            }
        }
        int message = "tomorrow_waiting".equals(s.status)
                ? R.string.prayer_tomorrow_waiting
                : "stale".equals(s.status)
                ? R.string.prayer_offline
                : "unavailable".equals(s.status)
                ? R.string.prayer_unavailable : R.string.prayer_waiting;
        return new String[] {"—", lc.getString(message), s.zoneName};
    }

    private static String formatUpdated(long epoch) {
        return epoch <= 0 ? "" : Instant.ofEpochMilli(epoch).atZone(MALAYSIA)
                .format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    static LocalTime[] parseTimes(JSONObject entry) throws Exception {
        LocalTime[] times = new LocalTime[5];
        for (int i = 0; i < 5; i++) {
            times[i] = LocalTime.parse(entry.getString(FIELDS[i]));
        }
        return times;
    }

    private static synchronized void fetch(Context c, String zone, String day) {
        String key = zone + ":" + day;
        long now = android.os.SystemClock.elapsedRealtime();
        if (!inFlight.isEmpty() || (key.equals(lastAttempt) && now < retryAfter)) {
            return;
        }
        inFlight = key;
        lastAttempt = key;
        retryAfter = now + 60_000;
        NETWORK.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(
                        "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=today&zone="
                                + zone);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                if (connection.getResponseCode() != 200) {
                    return;
                }
                String json = readLimited(connection, 128 * 1024);
                JSONObject root = new JSONObject(json);
                if (!"OK!".equals(root.optString("status"))
                        || !zone.equals(root.optString("zone"))) {
                    return;
                }
                JSONArray entries = root.getJSONArray("prayerTime");
                if (entries.length() != 1) {
                    return;
                }
                parseTimes(entries.getJSONObject(0));
                if (!day.equals(LocalDate.now(MALAYSIA).toString())
                        || !root.optString("serverTime").startsWith(day)) {
                    return;
                }
                Store.prefs(c).edit().putString("prayer_cache_" + zone, json)
                        .putString("prayer_day_" + zone, day)
                        .putLong("prayer_checked_" + zone, System.currentTimeMillis())
                        .remove("prayer_refresh_" + zone).apply();
            } catch (Exception ignored) {
                // Preserve the last verified response.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                synchronized (WidgetData.class) {
                    inFlight = "";
                }
            }
        });
    }
    static String readLimited(HttpURLConnection connection, int limit) throws java.io.IOException {
        try (java.io.InputStream stream = connection.getInputStream();
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
                if (bytes.size() > limit) {
                    throw new java.io.IOException("Response too large");
                }
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static BatterySnapshot battery(Context c) {
        SharedPreferences p = Store.prefs(c);
        long at = p.getLong("battery_at", 0);
        if (at <= 0) {
            return new BatterySnapshot(-1, -1,
                    p.getBoolean("battery_unsupported", false) ? "unsupported" : "missing", "");
        }
        int pct = Math.round(p.getFloat("battery_pct", -1f));
        int range = p.getInt("range_m", -1);
        return new BatterySnapshot(pct, range,
                System.currentTimeMillis() - at > 30 * 60 * 1000 ? "stale" : "live",
                formatUpdated(at));
    }
}
