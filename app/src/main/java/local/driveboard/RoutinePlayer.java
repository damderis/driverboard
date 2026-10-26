package local.driveboard;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Application-owned controller: it must survive the phone activity closing. */
public final class RoutinePlayer {
    private static final String QURAN_PACKAGE = "com.quran.labs.androidquran";
    private static RoutinePlayer instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaController controller;
    private ListenableFuture<MediaController> connection;
    private boolean startPending;
    private int carStartGeneration;
    private boolean scheduledConnection;

    private RoutinePlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized RoutinePlayer get(Context context) {
        if (instance == null) {
            instance = new RoutinePlayer(context);
        }
        return instance;
    }

    public void onCarStarted() {
        handler.post(() -> {
            if (startPending) {
                log("car start already pending");
                return;
            }
            startPending = true;
            int generation = carStartGeneration;
            log("car start; waiting 8 seconds");
            handler.postDelayed(() -> {
                if (generation != carStartGeneration) {
                    return;
                }
                startPending = false;
                Routine due = RoutineSchedule.due(Store.routines(context), LocalDateTime.now(),
                        Store.routineLastRuns(context));
                if (due == null) {
                    log("none due");
                } else if (quranPlaying()) {
                    log("skipped; Quran already playing");
                } else {
                    play(due, true);
                }
            }, 8000);
        });
    }

    public void onCarDisconnected() {
        handler.post(() -> {
            carStartGeneration++;
            startPending = false;
            if (scheduledConnection && connection != null) {
                MediaController.releaseFuture(connection);
                connection = null;
                scheduledConnection = false;
            }
            log("car disconnected; pending start cancelled");
        });
    }

    public void playNow(Routine routine) {
        handler.post(() -> play(routine, false));
    }

    private boolean quranPlaying() {
        if (controller != null && controller.isPlaying()) {
            return true;
        }
        try {
            MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
            for (android.media.session.MediaController session : manager.getActiveSessions(
                    new ComponentName(context, MediaAccessService.class))) {
                PlaybackState state = session.getPlaybackState();
                if (QURAN_PACKAGE.equals(session.getPackageName()) && state != null
                        && state.getState() == PlaybackState.STATE_PLAYING) {
                    return true;
                }
            }
        } catch (RuntimeException error) {
            log("playing-session check failed " + error);
        }
        return false;
    }

    private void play(Routine routine, boolean scheduled) {
        routine = new Routine(routine.id, routine.name, routine.enabled, routine.days,
                routine.startTime, routine.endTime, routine.surahs, routine.qariId);
        final Routine selected = routine;
        if (routine.surahs.isEmpty()) {
            log("empty list for " + routine.id);
            return;
        }
        if (connection != null) {
            log("connection already in progress");
            return;
        }
        release(false);
        log("connecting " + routine.id);
        try {
            SessionToken token = new SessionToken(context, new ComponentName(QURAN_PACKAGE,
                    "com.quran.labs.feature.autoquran.service.QuranBrowsableAudioPlaybackService"));
            ListenableFuture<MediaController> future = new MediaController.Builder(context, token)
                    .setListener(new MediaController.Listener() {
                        @Override
                        public void onDisconnected(MediaController disconnected) {
                            if (controller == disconnected) {
                                log("controller disconnected");
                                release(false);
                            }
                        }
                    }).buildAsync();
            connection = future;
            scheduledConnection = scheduled;
            handler.postDelayed(() -> {
                if (connection == future) {
                    log("connection timed out");
                    connection = null;
                    scheduledConnection = false;
                    MediaController.releaseFuture(future);
                }
            }, 30000);
            future.addListener(() -> {
                if (connection != future) {
                    return;
                }
                connection = null;
                scheduledConnection = false;
                try {
                    controller = future.get();
                    MediaController active = controller;
                    log("connected " + selected.id);
                    if (scheduled && quranPlaying()) {
                        log("skipped after connect; Quran already playing");
                        release(false);
                        return;
                    }
                    if (!controller.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                            || !controller.isCommandAvailable(Player.COMMAND_PREPARE)
                            || !controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
                        throw new IllegalStateException("Required playback commands unavailable");
                    }
                    Set<String> allowed = new HashSet<>();
                    List<MediaItem> items = new ArrayList<>();
                    for (int surah : selected.surahs) {
                        String id = "sura_" + surah + "_" + selected.qariId;
                        allowed.add(id);
                        items.add(new MediaItem.Builder().setMediaId(id).build());
                    }
                    controller.addListener(new Player.Listener() {
                        @Override
                        public void onMediaItemTransition(MediaItem item, int reason) {
                            if (controller != active) {
                                return;
                            }
                            log("item " + (item == null ? "null" : item.mediaId));
                            // Only the player's own advance ends the routine; a surah the owner picks keeps playing.
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                                    && item != null && item.mediaId.startsWith("sura_")
                                    && !allowed.contains(item.mediaId)) {
                                log("past routine boundary");
                                release(true);
                            }
                        }

                        @Override
                        public void onPlaybackStateChanged(int state) {
                            if (controller != active) {
                                return;
                            }
                            log("playback state " + state);
                            if (state == Player.STATE_ENDED) {
                                release(true);
                            }
                        }

                        @Override
                        public void onPlayerError(PlaybackException error) {
                            if (controller != active) {
                                return;
                            }
                            log("playback error " + error);
                            release(false);
                        }
                    });
                    log("setMediaItems " + allowed);
                    controller.setMediaItems(items);
                    log("prepare");
                    controller.prepare();
                    log("play");
                    controller.play();
                    if (scheduled) {
                        Store.markRoutineRun(context, selected.id, LocalDate.now());
                        log("marked run " + selected.id);
                    }
                } catch (Exception error) {
                    log("start failed " + error);
                    release(false);
                }
            }, context.getMainExecutor());
        } catch (RuntimeException error) {
            log("connect failed " + error);
            release(false);
        }
    }

    private void release(boolean pause) {
        MediaController current = controller;
        controller = null;
        if (current != null) {
            try {
                if (pause) {
                    log("pause");
                    current.pause();
                }
            } catch (RuntimeException error) {
                log("pause failed " + error);
            } finally {
                try {
                    current.release();
                    log("released");
                } catch (RuntimeException error) {
                    log("release failed " + error);
                }
            }
        }
    }

    private void log(String message) {
        CarDiagnostics.log(context, "Routine " + message);
    }
}
