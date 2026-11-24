package local.driveboard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** Controls only the legacy phone session; never connects to Quran's car service. */
public final class QuranPhone {
    private QuranPhone() {}

    public static MediaController find(Context context) {
        ArrayList<MediaController> sessions = new ArrayList<>();
        ArrayList<String> tags = new ArrayList<>();
        ArrayList<WidgetData.MediaCandidate> candidates = new ArrayList<>();
        try {
            MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
            for (MediaController controller : manager.getActiveSessions(
                    new ComponentName(context, MediaAccessService.class))) {
                String tag = controller.getTag();
                if (!QuranMediaPolicy.allowed(controller.getPackageName(), tag)) {
                    continue;
                }
                PlaybackState state = controller.getPlaybackState();
                boolean resumable = state != null && controller.getMetadata() != null
                        && (state.getState() == PlaybackState.STATE_PAUSED
                        || state.getState() == PlaybackState.STATE_STOPPED);
                sessions.add(controller);
                tags.add(tag);
                candidates.add(new WidgetData.MediaCandidate(state == null ? 0 : state.getState(),
                        state == null ? 0 : state.getPosition(),
                        state == null ? 0 : state.getBufferedPosition(),
                        controller.getPackageName(), resumable));
            }
            int index = QuranMediaPolicy.select(tags, candidates);
            return index < 0 ? (sessions.isEmpty() ? null : sessions.get(0)) : sessions.get(index);
        } catch (RuntimeException error) {
            CarDiagnostics.log(context, "QuranMedia session lookup " + error);
            return null;
        }
    }

    public static boolean isPlayingWithProgress(Context context) {
        try {
            MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
            for (MediaController session : manager.getActiveSessions(
                    new ComponentName(context, MediaAccessService.class))) {
                PlaybackState state = session.getPlaybackState();
                if (QuranMediaPolicy.PACKAGE.equals(session.getPackageName()) && state != null
                        && state.getState() == PlaybackState.STATE_PLAYING
                        && (state.getPosition() > 0 || state.getBufferedPosition() > 0)) {
                    return true;
                }
            }
        } catch (RuntimeException error) {
            CarDiagnostics.log(context, "QuranMedia playing check " + error);
        }
        return false;
    }

    public static void play(Context context) {
        try {
            MediaController controller = find(context);
            if (controller != null) {
                controller.getTransportControls().play();
                CarDiagnostics.log(context, "QuranMedia forwarded play to phone session");
                return;
            }
            Intent query = new Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setPackage(QuranMediaPolicy.PACKAGE);
            List<ResolveInfo> receivers = context.getPackageManager()
                    .queryBroadcastReceivers(query, 0);
            if (receivers.isEmpty()) {
                CarDiagnostics.log(context, "QuranMedia media-button receiver unavailable");
                return;
            }
            ResolveInfo receiver = receivers.get(0);
            ComponentName target = new ComponentName(receiver.activityInfo.packageName,
                    receiver.activityInfo.name);
            for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(target)
                        .putExtra(Intent.EXTRA_KEY_EVENT,
                                new KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)));
            }
            CarDiagnostics.log(context, "QuranMedia explicit play broadcast " + target);
        } catch (RuntimeException error) {
            CarDiagnostics.log(context, "QuranMedia resume failed " + error);
        }
    }
}
