package local.driveboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
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
import java.util.ArrayList;
import java.util.List;

/** Metadata and transport proxy only: source apps retain playback and audio focus. */
public final class DriveBoardMediaService extends MediaBrowserServiceCompat {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSessionCompat mirror;
    private BoardArtwork artwork;
    private boolean bound;
    private WidgetData.MediaSnapshot media;
    private WidgetData.PrayerSnapshot prayers;
    private Uri artUri;
    private Bitmap smallArt;
    private String metadataKey = "", sourceKey = "", prayerKey = "";
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences = (p, k) ->
            handler.post(() -> { refresh(true); notifyChildrenChanged("views"); });
    private final WeatherRepository.Callback weather = value -> refresh(true);
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (bound) { refresh(false); handler.postDelayed(this, 2000); }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        artwork = new BoardArtwork(this, (uri, fallback) -> {
            artUri = uri; smallArt = fallback; metadataKey = ""; publish();
        });
        mirror = new MediaSessionCompat(this, "DriveBoardMirror");
        mirror.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { transport(WidgetData.MEDIA_PLAY_PAUSE, true); }
            @Override public void onPause() { transport(WidgetData.MEDIA_PLAY_PAUSE, false); }
            @Override public void onSkipToNext() { transport(WidgetData.MEDIA_NEXT, null); }
            @Override public void onSkipToPrevious() { transport(WidgetData.MEDIA_PREVIOUS, null); }
            @Override public void onPlayFromMediaId(String id, Bundle extras) {
                if ("quran_resume".equals(id)) QuranPhone.play(DriveBoardMediaService.this);
                else if (id != null && id.startsWith("view_")) selectMode(id.substring(5));
            }
            @Override public void onCustomAction(String action, Bundle extras) {
                if ("view".equals(action)) selectMode(ArtworkPolicy.cycle(mode()));
                if ("audio".equals(action)) {
                    AudioSwitchClient.apply(DriveBoardMediaService.this, "toggle"); refresh(true);
                }
            }
        }, handler);
        setSessionToken(mirror.getSessionToken());
        Store.prefs(this).registerOnSharedPreferenceChangeListener(preferences);
        WeatherRepository.start(this, weather);
        refresh(true);
    }
    private boolean malay() { return "ms".equals(Store.prefs(this).getString("lang", "en")); }
    private String mode() { return ArtworkPolicy.mode(Store.prefs(this).getString("artwork_mode", "auto")); }
    private void selectMode(String value) {
        Store.prefs(this).edit().putString("artwork_mode", ArtworkPolicy.mode(value)).apply();
        refresh(true); notifyChildrenChanged("views");
    }
    private void transport(String action, Boolean playing) {
        WidgetData.MediaSnapshot current = WidgetData.mediaSnapshot(this);
        if (playing == null || current.playing != playing) WidgetData.performMediaAction(current, action);
        refresh(true);
    }
    private void refresh(boolean dirty) {
        try {
            media = WidgetData.mediaSnapshot(this);
            prayers = WidgetData.prayerSnapshot(this);
            if (media.isQuranSession && media.controller != null && media.controller.getMetadata() != null) {
                android.media.MediaMetadata data = media.controller.getMetadata();
                String raw = data.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
                String description = String.valueOf(data.getDescription().getDescription());
                SharedPreferences saved = Store.prefs(this);
                if (!java.util.Objects.equals(raw, saved.getString("quran_last_title", ""))
                        || !media.artist.equals(saved.getString("quran_last_reciter", ""))
                        || !description.equals(saved.getString("quran_last_description", ""))) {
                    saved.edit().putString("quran_last_title", raw).putString("quran_last_description", description)
                            .putString("quran_last_reciter", media.artist).apply();
                }
            }
            String key = media.title + "|" + media.artist + "|" + media.album + "|"
                    + System.identityHashCode(media.art) + "|" + media.playing + "|"
                    + (media.controller == null ? "" : media.controller.getSessionToken());
            boolean changed = !key.equals(sourceKey);
            dirty |= changed; sourceKey = key;
            if (changed) { notifyChildrenChanged("root"); notifyChildrenChanged("quran"); }
            String next = nextPrayer() + "|" + prayers.zoneCode + "|" + malay()
                    + "|" + java.time.LocalDate.now() + "|" + lastQuran();
            for (WidgetData.PrayerRow row : prayers.rows) next += "|" + row.time + "|" + row.next;
            if (!next.equals(prayerKey)) {
                prayerKey = next; notifyChildrenChanged("prayers"); notifyChildrenChanged("root");
                notifyChildrenChanged("quran");
            }
            artwork.request(mode(), dirty);
            publish();
        } catch (RuntimeException error) { CarDiagnostics.log(this, "Mirror refresh failed " + error); }
    }
    private void publish() {
        if (media == null || mirror == null) return;
        String title = media.hasTrack ? media.title : "DriveBoard";
        String artist = media.hasTrack ? media.artist : nextPrayer();
        String key = title + "|" + artist + "|" + media.duration + "|" + artUri;
        if (!key.equals(metadataKey)) {
            metadataKey = key;
            MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, media.duration);
            if (artUri != null) metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri.toString())
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri.toString());
            if (smallArt != null) metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, smallArt);
            mirror.setMetadata(metadata.build());
        }
        long actions = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
        if (media.playbackState != null) actions |= media.playbackState.getActions()
                & (PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
        // Do not inherit source custom actions: those callbacks belong to the original app.
        PlaybackStateCompat.Builder state = new PlaybackStateCompat.Builder().setState(media.playbackState == null
                        ? PlaybackStateCompat.STATE_PAUSED : media.playbackState.getState(),
                media.position, media.speed, media.updatedAt).setActions(actions)
                .setBufferedPosition(media.playbackState == null ? 0 : media.playbackState.getBufferedPosition());
        for (String action : ArtworkPolicy.actions(AudioSwitchClient.read(this).installed)) {
            state.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(action,
                    "view".equals(action) ? (malay() ? "Paparan" : "View")
                            : (malay() ? "Audio kereta" : "Car audio"),
                    "view".equals(action) ? android.R.drawable.ic_menu_view : android.R.drawable.ic_lock_silent_mode_off).build());
        }
        mirror.setPlaybackState(state.build()); mirror.setActive(bound || media.hasTrack);
    }
    private String nextPrayer() {
        if (prayers != null) for (int i = 0; i < prayers.rows.length; i++) {
            WidgetData.PrayerRow row = prayers.rows[i];
            if (row.next) return QuranMediaPolicy.nextPrayer(i, row.time, prayers.countdown, malay());
        }
        return AppLocale.wrap(this).getString(R.string.quran_prayer_unavailable);
    }
    private String lastQuran() {
        SharedPreferences saved = Store.prefs(this);
        return QuranMediaPolicy.lastRecitation(QuranMediaPolicy.title(saved.getString("quran_last_title", ""),
                saved.getString("quran_last_description", ""), malay()), saved.getString("quran_last_reciter", ""));
    }
    private MediaBrowserCompat.MediaItem item(String id, String title, String subtitle, int flags) {
        return new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(id)
                .setTitle(title).setSubtitle(subtitle).build(), flags);
    }
    @Override public BrowserRoot onGetRoot(String packageName, int uid, Bundle hints) {
        return new BrowserRoot("root", null);
    }
    @Override public void onLoadChildren(String parent, Result<List<MediaBrowserCompat.MediaItem>> result) {
        refresh(false);
        ArrayList<MediaBrowserCompat.MediaItem> items = new ArrayList<>();
        int folder = MediaBrowserCompat.MediaItem.FLAG_BROWSABLE;
        if ("root".equals(parent)) {
            items.add(item("quran", "Quran", lastQuran(), folder));
            items.add(item("prayers", AppLocale.wrap(this).getString(R.string.quran_prayer_times), nextPrayer(), folder));
            items.add(item("views", malay() ? "Paparan" : "View", "", folder));
        } else if ("quran".equals(parent)) {
            items.add(item("quran_resume", AppLocale.wrap(this).getString(R.string.quran_resume_recitation),
                    lastQuran(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
        } else if ("views".equals(parent)) {
            String[] ids = {ArtworkPolicy.AUTO, ArtworkPolicy.BOARD, ArtworkPolicy.LYRICS};
            String[] labels = {"Auto", malay() ? "Papan" : "Board", malay() ? "Lirik/Ayat" : "Lyrics/Verses"};
            for (int i = 0; i < ids.length; i++) items.add(item("view_" + ids[i], labels[i],
                    ids[i].equals(mode()) ? (malay() ? "Semasa" : "Current") : "", MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
        } else if ("prayers".equals(parent) && prayers != null) {
            for (int i = 0; i < 5; i++) {
                QuranMediaPolicy.PrayerItem row = QuranMediaPolicy.prayerItem(i, prayers.rows[i].time,
                        prayers.rows[i].next, prayers.countdown, malay());
                items.add(item("prayer_" + i, row.title, row.subtitle, folder));
            }
        }
        result.sendResult(items);
    }
    @Override public IBinder onBind(Intent intent) {
        IBinder binder = super.onBind(intent);
        if (binder != null) { bound = true; refresh(true); handler.removeCallbacks(poll); handler.postDelayed(poll, 2000); }
        return binder;
    }
    @Override public void onRebind(Intent intent) {
        super.onRebind(intent); bound = true; refresh(true); handler.removeCallbacks(poll); handler.postDelayed(poll, 2000);
    }
    @Override public boolean onUnbind(Intent intent) {
        bound = false; handler.removeCallbacks(poll); return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(preferences);
        WeatherRepository.stop(weather); handler.removeCallbacksAndMessages(null); artwork.close();
        mirror.setActive(false); mirror.release(); super.onDestroy();
    }
}





