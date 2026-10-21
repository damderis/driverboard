package local.driveboard;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
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
    public static final String MEDIA_PREVIOUS = "previous";
    public static final String MEDIA_PLAY_PAUSE = "play_pause";
    public static final String MEDIA_NEXT = "next";
    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final String[] FIELDS = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
    private static final String[] NAMES = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};
    private static String inFlight = "";
    private static String lastAttempt = "";
    private static long retryAfter;
    private static String cachedArtTrack = "";
    private static MediaSession.Token cachedArtSession;
    private static Bitmap cachedArt;

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



    public static boolean hasMediaAccess(Context c) {
        NotificationManager m = c.getSystemService(NotificationManager.class);
        return m != null && m.isNotificationListenerAccessGranted(
                new ComponentName(c, MediaAccessService.class));
    }

    public static final class MediaSnapshot {
        public final String title;
        public final String artist;
        public final String album;
        public final Bitmap art;
        public final long duration;
        public final long position;
        public final long updatedAt;
        public final float speed;
        public final boolean playing;
        public final boolean hasTrack;
        /** The exact session represented by this snapshot, if one is available. */
        public final MediaController controller;
        public final PlaybackState playbackState;
        public final boolean canPrevious;
        public final boolean canToggle;
        public final boolean canNext;

        public MediaSnapshot(String title, String artist, String album, Bitmap art, long duration,
                long position, long updatedAt, float speed, boolean playing, boolean hasTrack) {
            this(title, artist, album, art, duration, position, updatedAt, speed, playing,
                    hasTrack, null, null);
        }

        public MediaSnapshot(String title, String artist, String album, Bitmap art, long duration,
                long position, long updatedAt, float speed, boolean playing, boolean hasTrack,
                MediaController controller, PlaybackState playbackState) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.art = art;
            this.duration = duration;
            this.position = position;
            this.updatedAt = updatedAt;
            this.speed = speed;
            this.playing = playing;
            this.hasTrack = hasTrack;
            this.controller = controller;
            this.playbackState = playbackState;
            this.canPrevious = supportsMediaAction(playbackState, MEDIA_PREVIOUS);
            this.canToggle = supportsMediaAction(playbackState, MEDIA_PLAY_PAUSE);
            this.canNext = supportsMediaAction(playbackState, MEDIA_NEXT);
        }

        public long estimatedPositionMs() {
            long elapsed = playing && updatedAt > 0
                    ? Math.max(0, SystemClock.elapsedRealtime() - updatedAt) : 0;
            long estimate = Math.max(0, position + Math.round(elapsed * speed));
            return duration > 0 ? Math.min(duration, estimate) : estimate;
        }
    }

    public static MediaSnapshot mediaSnapshot(Context c) {
        Context lc = AppLocale.wrap(c);
        if (!hasMediaAccess(c)) {
            return new MediaSnapshot(lc.getString(R.string.media_grant_access),
                    lc.getString(R.string.media_notification_required), "", null,
                    0, 0, 0, 0, false, false);
        }
        try {
            MediaSessionManager manager = c.getSystemService(MediaSessionManager.class);
            MediaController paused = null;
            PlaybackState pausedState = null;
            for (MediaController controller : manager.getActiveSessions(
                    new ComponentName(c, MediaAccessService.class))) {
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    return snapshotOf(controller, state, lc);
                }
                if (paused == null && state != null
                        && state.getState() == PlaybackState.STATE_PAUSED
                        && controller.getMetadata() != null) {
                    paused = controller;
                    pausedState = state;
                }
            }
            if (paused != null) {
                return snapshotOf(paused, pausedState, lc);
            }
        } catch (RuntimeException ignored) {
            // Permission or the session may disappear during the snapshot.
        }
        return new MediaSnapshot(lc.getString(R.string.media_nothing_playing), "", "", null,
                0, 0, 0, 0, false, false);
    }

    private static MediaSnapshot snapshotOf(MediaController controller, PlaybackState state,
            Context lc) {
        boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
        MediaMetadata data = controller.getMetadata();
        if (data == null) {
            return new MediaSnapshot(lc.getString(R.string.media_playing), "\u2014", "", null,
                    0, state.getPosition(), state.getLastPositionUpdateTime(),
                    state.getPlaybackSpeed(), playing, false, controller, state);
        }
        String title = nonempty(data.getString(MediaMetadata.METADATA_KEY_TITLE),
                data.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
        String artist = nonempty(data.getString(MediaMetadata.METADATA_KEY_ARTIST), "\u2014");
        String album = nonempty(data.getString(MediaMetadata.METADATA_KEY_ALBUM), "");
        long duration = data.getLong(MediaMetadata.METADATA_KEY_DURATION);
        String artKey = title + "\u0000" + artist + "\u0000" + duration;
        Bitmap art = artwork(data, controller.getSessionToken(), artKey);
        return new MediaSnapshot(nonempty(title, lc.getString(R.string.media_playing)),
                artist, album, art, duration, state.getPosition(),
                state.getLastPositionUpdateTime(), state.getPlaybackSpeed(), playing,
                title != null && !title.trim().isEmpty(), controller, state);
    }

    /** Whether the session advertises the requested control in its current playback state. */
    public static boolean supportsMediaAction(PlaybackState state, String action) {
        if (state == null || action == null) {
            return false;
        }
        long actions = state.getActions();
        switch (action) {
            case MEDIA_PREVIOUS:
                return (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0;
            case MEDIA_NEXT:
                return (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0;
            case MEDIA_PLAY_PAUSE:
                long required = state.getState() == PlaybackState.STATE_PLAYING
                        ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
                return (actions & (required | PlaybackState.ACTION_PLAY_PAUSE)) != 0;
            default:
                return false;
        }
    }

    /** Sends a control to the session shown in {@code snapshot}; null means dispatched. */
    public static String performMediaAction(MediaSnapshot snapshot, String action) {
        if (snapshot == null || snapshot.controller == null) {
            return "Media control unavailable";
        }
        try {
            MediaController controller = snapshot.controller;
            PlaybackState state = controller.getPlaybackState();
            if (!supportsMediaAction(state, action)) {
                return "Player does not support this control";
            }
            MediaController.TransportControls controls = controller.getTransportControls();
            switch (action) {
                case MEDIA_PREVIOUS:
                    controls.skipToPrevious();
                    break;
                case MEDIA_NEXT:
                    controls.skipToNext();
                    break;
                case MEDIA_PLAY_PAUSE:
                    boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
                    if (playing) {
                        controls.pause();
                    } else {
                        controls.play();
                    }
                    break;
                default:
                    return "Unknown media control";
            }
            return null;
        } catch (RuntimeException ignored) {
            // A session can disappear between displaying and tapping its controls.
            return "Media control unavailable";
        }
    }

    private static synchronized Bitmap artwork(MediaMetadata data, MediaSession.Token session,
            String trackKey) {
        if (!trackKey.equals(cachedArtTrack)
                || !java.util.Objects.equals(session, cachedArtSession)
                || (cachedArt != null && cachedArt.isRecycled())) {
            cachedArtTrack = trackKey;
            cachedArtSession = session;
            cachedArt = null;
        }
        if (cachedArt != null) {
            return cachedArt;
        }
        Bitmap source = data.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (source == null) {
            source = data.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        if (source == null) {
            source = data.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        if (source == null || source.isRecycled()) {
            return null;
        }
        try {
            cachedArt = source.getConfig() != Bitmap.Config.ARGB_8888
                    ? source.copy(Bitmap.Config.ARGB_8888, false) : source;
        } catch (RuntimeException ignored) {
            cachedArt = null;
        }
        return cachedArt;
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
                long totalMinutes = (seconds + 59) / 60;
                long hours = totalMinutes / 60;
                long minutes = totalMinutes % 60;
                countdown = hours == 0
                        ? AppLocale.wrap(c).getString(R.string.board_imminent_minutes, minutes)
                        : AppLocale.wrap(c).getString(R.string.prayer_countdown, hours, minutes);
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

}
