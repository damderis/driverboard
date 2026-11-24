package local.driveboard;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.media.MediaBrowserServiceCompat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Metadata and transport proxy only. Quran owns playback, focus and notifications. */
public final class QuranMediaService extends MediaBrowserServiceCompat {
    private static final long ACTIONS = PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSessionCompat mirror;
    private MediaController phone;
    private MediaSessionManager manager;
    private boolean listening;
    private boolean bound;
    private String browserKey = "";
    private String metadataKey = "";
    private String currentTitle = "Quran";
    private String currentReciter = "";
    private Bitmap currentArt;
    private WidgetData.PrayerSnapshot prayers;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (bound) {
                refresh();
                handler.postDelayed(this, 2000);
            }
        }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged =
            controllers -> refresh();
    private final MediaController.Callback changes = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { refresh(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
        @Override public void onSessionDestroyed() {
            detach();
            refresh();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        manager = getSystemService(MediaSessionManager.class);
        mirror = new MediaSessionCompat(this, "DriveBoardQuranMirror");
        mirror.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { QuranPhone.play(QuranMediaService.this); }
            @Override public void onPlayFromMediaId(String id, Bundle extras) {
                if ("quran_resume".equals(id)) {
                    onPlay();
                }
            }
            @Override public void onPause() { forward("pause", 0); }
            @Override public void onSkipToNext() { forward("next", 0); }
            @Override public void onSkipToPrevious() { forward("previous", 0); }
        }, handler);
        setSessionToken(mirror.getSessionToken());
        refresh();
    }

    private void ensureListener() {
        if (!listening && WidgetData.hasMediaAccess(this)) {
            try {
                manager.addOnActiveSessionsChangedListener(sessionsChanged,
                        new ComponentName(this, MediaAccessService.class), handler);
                listening = true;
            } catch (RuntimeException error) {
                CarDiagnostics.log(this, "QuranMedia listener unavailable " + error);
            }
        }
    }

    private void forward(String action, long position) {
        try {
            MediaController current = QuranPhone.find(this);
            if (current == null) {
                CarDiagnostics.log(this, "QuranMedia ignored " + action + "; no phone session");
                return;
            }
            MediaController.TransportControls controls = current.getTransportControls();
            switch (action) {
                case "pause": controls.pause(); break;
                case "next": controls.skipToNext(); break;
                case "previous": controls.skipToPrevious(); break;
                default: return;
            }
            CarDiagnostics.log(this, "QuranMedia forwarded " + action + " position=" + position);
        } catch (RuntimeException error) {
            CarDiagnostics.log(this, "QuranMedia transport failed " + error);
        }
    }

    private void detach() {
        if (phone != null) {
            try {
                phone.unregisterCallback(changes);
            } catch (RuntimeException error) {
                CarDiagnostics.log(this, "QuranMedia detach " + error);
            }
        }
        phone = null;
    }

    private boolean malay() {
        return "ms".equals(Store.prefs(this).getString("lang", "en"));
    }

    private void refresh() {
        try {
            refreshSnapshot();
        } catch (RuntimeException error) {
            CarDiagnostics.log(this, "QuranMedia refresh failed " + error);
        }
    }

    private void refreshSnapshot() {
        ensureListener();
        MediaController current = QuranPhone.find(this);
        if (phone == null || current == null
                || !phone.getSessionToken().equals(current.getSessionToken())) {
            detach();
            phone = current;
            if (phone != null) {
                phone.registerCallback(changes, handler);
            }
        }
        SharedPreferences saved = Store.prefs(this);
        MediaMetadata data = phone == null ? null : phone.getMetadata();
        PlaybackState state = phone == null ? null : phone.getPlaybackState();
        String raw = data == null ? saved.getString("quran_last_title", "")
                : nonempty(data.getString(MediaMetadata.METADATA_KEY_TITLE),
                        data.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
        String description = data == null ? saved.getString("quran_last_description", "")
                : nonempty(data.getDescription().getDescription(), "");
        currentTitle = QuranMediaPolicy.title(raw, description, malay());
        currentReciter = data == null ? saved.getString("quran_last_reciter", "")
                : nonempty(data.getString(MediaMetadata.METADATA_KEY_ARTIST),
                        data.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
        if (currentReciter.isEmpty() && data != null) {
            int comma = description.lastIndexOf(',');
            if (comma >= 0) {
                currentReciter = description.substring(comma + 1).trim();
            }
        }
        if (data != null && (!Objects.equals(raw, saved.getString("quran_last_title", ""))
                || !currentReciter.equals(saved.getString("quran_last_reciter", ""))
                || !description.equals(saved.getString("quran_last_description", "")))) {
            saved.edit().putString("quran_last_title", raw)
                    .putString("quran_last_description", description)
                    .putString("quran_last_reciter", currentReciter).apply();
        }
        prayers = WidgetData.prayerSnapshot(this);
        String next = nextPrayer();
        boolean idle = phone == null;
        long duration = data == null ? 0 : data.getLong(MediaMetadata.METADATA_KEY_DURATION);
        String key = currentTitle + "|" + currentReciter + "|" + next + "|" + idle
                + "|" + duration + "|" + malay();
        if (!key.equals(metadataKey)) {
            metadataKey = key;
            QuranMetadata parsed = QuranMetadata.parse(QuranMediaPolicy.PACKAGE, raw, description);
            currentArt = parsed == null ? placeholder()
                    : SurahArt.render(this, parsed.surah.number, 512, 0xFF263C34);
            MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, idle ? "Quran" : currentTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, idle ? next : currentReciter)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                            idle ? "Quran" : currentTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                            idle ? next : currentReciter)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArt);
            mirror.setMetadata(metadata.build());
        }
        PlaybackStateCompat.Builder playback = state == null
                ? new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PAUSED, 0, 0)
                : new PlaybackStateCompat.Builder(PlaybackStateCompat.fromPlaybackState(state));
        mirror.setPlaybackState(playback.setActions(ACTIONS).build());
        mirror.setActive(bound || phone != null);
        String contents = key + "|" + LocalDate.now() + "|" + prayers.zoneCode;
        for (WidgetData.PrayerRow prayer : prayers.rows) {
            contents += "|" + prayer.time + "|" + prayer.next;
        }
        if (!contents.equals(browserKey)) {
            browserKey = contents;
            notifyChildrenChanged("root");
            notifyChildrenChanged("quran");
            notifyChildrenChanged("prayers");
        }
    }

    private Bitmap placeholder() {
        Bitmap artwork = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(artwork);
        canvas.drawColor(0xFF182722);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFD9B45A);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        paint.setTypeface(getResources().getFont(R.font.amiri_quran));
        paint.setTextSize(96);
        canvas.drawText("\u0627\u0644\u0642\u0631\u0622\u0646", 256, 280, paint);
        return artwork;
    }

    private String nextPrayer() {
        if (prayers == null) {
            return AppLocale.wrap(this).getString(R.string.quran_prayer_unavailable);
        }
        for (int i = 0; i < prayers.rows.length; i++) {
            WidgetData.PrayerRow row = prayers.rows[i];
            if (row.next) {
                return QuranMediaPolicy.nextPrayer(i, row.time, prayers.countdown, malay());
            }
        }
        return AppLocale.wrap(this).getString(R.string.quran_prayer_unavailable);
    }

    private static String nonempty(CharSequence value, String fallback) {
        return value == null || value.toString().trim().isEmpty()
                ? (fallback == null ? "" : fallback) : value.toString().trim();
    }

    @Override public IBinder onBind(Intent intent) {
        IBinder binder = super.onBind(intent);
        if (binder != null) {
            bound = true;
            refresh();
            handler.removeCallbacks(poll);
            handler.postDelayed(poll, 2000);
        }
        return binder;
    }

    @Override public void onRebind(Intent intent) {
        super.onRebind(intent);
        bound = true;
        refresh();
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, 2000);
    }

    @Override public boolean onUnbind(Intent intent) {
        bound = false;
        handler.removeCallbacks(poll);
        refresh();
        return super.onUnbind(intent);
    }

    @Override public BrowserRoot onGetRoot(String packageName, int uid, Bundle hints) {
        Bundle extras = new Bundle();
        extras.putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true);
        extras.putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1);
        extras.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1);
        return new BrowserRoot("root", extras);
    }

    private MediaBrowserCompat.MediaItem item(String id, String title, String subtitle,
            int flags, Bitmap artwork) {
        return new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(id).setTitle(title).setSubtitle(subtitle).setIconBitmap(artwork)
                .build(), flags);
    }

    @Override public void onLoadChildren(String parent,
            Result<List<MediaBrowserCompat.MediaItem>> result) {
        refresh();
        ArrayList<MediaBrowserCompat.MediaItem> items = new ArrayList<>();
        if ("root".equals(parent)) {
            items.add(item("quran", "Quran",
                    QuranMediaPolicy.lastRecitation(currentTitle, currentReciter),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE, currentArt));
            items.add(item("prayers", AppLocale.wrap(this).getString(R.string.quran_prayer_times), nextPrayer(),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE, null));
        } else if ("quran".equals(parent)) {
            items.add(item("quran_resume", AppLocale.wrap(this).getString(R.string.quran_resume_recitation),
                    QuranMediaPolicy.lastRecitation(currentTitle, currentReciter),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE, currentArt));
        } else if ("prayers".equals(parent) && prayers != null) {
            for (int i = 0; i < 5; i++) {
                QuranMediaPolicy.PrayerItem prayer = QuranMediaPolicy.prayerItem(i,
                        prayers.rows[i].time, prayers.rows[i].next, prayers.countdown, malay());
                items.add(item("prayer_" + i, prayer.title, prayer.subtitle,
                        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE, null));
            }
        }
        result.sendResult(items);
    }

    @Override public void onDestroy() {
        if (listening) {
            manager.removeOnActiveSessionsChangedListener(sessionsChanged);
        }
        detach();
        handler.removeCallbacksAndMessages(null);
        mirror.setActive(false);
        mirror.release();
        super.onDestroy();
    }
}
