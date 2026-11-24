package local.driveboard;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/** One worker owns the detached BoardView, its software Canvas, and PNG encoding. */
final class BoardArtwork {
    interface Callback { void ready(Uri uri, Bitmap fallback); }
    private final Context context;
    private final Callback callback;
    private final HandlerThread thread = new HandlerThread("DriveBoardArtwork");
    private final Handler worker;
    private final Handler main = new Handler(android.os.Looper.getMainLooper());
    private BoardView board;
    private LyricsDocument lyrics;
    private String track = "";
    private byte[] digest;
    private long last = -1, lastMinute = -1;
    private boolean dirty = true;
    private volatile boolean closed;
    private Uri granted;
    private String mode = ArtworkPolicy.AUTO;
    BoardArtwork(Context context, Callback callback) {
        this.context = context; this.callback = callback;
        thread.start(); worker = new Handler(thread.getLooper());
    }
    void request(String selected, boolean changed) {
        worker.post(() -> { mode = selected; dirty |= changed; render(); });
    }
    private void render() {
        if (closed) return;
        WidgetData.MediaSnapshot media = WidgetData.mediaSnapshot(context);
        long now = SystemClock.elapsedRealtime(), minute = System.currentTimeMillis() / 60000;
        boolean moving = media.playing && ArtworkPolicy.lyrics(mode, media.playing,
                media.quran != null || lyrics != null) && (media.quran != null || lyrics != null);
        if (!ArtworkPolicy.shouldRender(now, last, moving, dirty, minute, lastMinute)) return;
        long started = now;
        try {
            if (board == null) {
                board = new BoardView(context, true);
                board.setFrameRequestHook(() -> { dirty = true; });
                board.setMode(Store.SPLIT);
                board.setSurfaceDpi(160);
            }
            String key = media.hasTrack && media.quran == null ? LyricsRepository.key(media) : "";
            if (!key.equals(track)) {
                track = key; lyrics = null;
                if (!key.isEmpty()) LyricsRepository.request(context, media, document -> worker.post(() -> {
                    if (!closed && key.equals(track)) { lyrics = document; dirty = true; render(); }
                }));
            }
            board.refresh();
            board.setQuranTranslation("ms".equals(Store.prefs(context).getString("lang", "en"))
                    ? QuranRepository.MS_BASMEIH : QuranRepository.EN_SAHIH);
            board.setLyrics(lyrics);
            board.setLyricsPosition(media.estimatedPositionMs());
            board.setLyricsVisible(ArtworkPolicy.lyrics(mode, media.playing,
                    media.quran != null && board.hasQuranDocument() || lyrics != null));
            board.cancelLayoutTransition();
            int size = 1024;
            int spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
            board.measure(spec, spec); board.layout(0, 0, size, size);
            Bitmap image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            board.draw(new Canvas(image));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.PNG, 100, bytes);
            byte[] png = bytes.toByteArray();
            byte[] next = MessageDigest.getInstance("SHA-256").digest(png);
            last = now; lastMinute = minute; dirty = false;
            if (ArtworkPolicy.changed(digest, next)) {
                File temporary = new File(context.getCacheDir(), "board-art-next.png");
                try (FileOutputStream output = new FileOutputStream(temporary)) { output.write(png); }
                File target = new File(context.getCacheDir(), "board-art.png");
                java.nio.file.Files.move(temporary.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                digest = next;
                String version = String.valueOf(System.currentTimeMillis());
                BoardArtProvider.latest = version;
                Uri uri = Uri.parse("content://local.driveboard.art/board/" + version + ".png");
                try {
                    context.grantUriPermission("com.google.android.projection.gearhead", uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Uri previous = granted;
                    granted = uri;
                    if (previous != null) main.postDelayed(() -> context.revokeUriPermission(previous,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION), 10000);
                } catch (RuntimeException unavailable) {
                    CarDiagnostics.log(context, "Artwork URI grant unavailable " + unavailable);
                }
                Bitmap small = Bitmap.createScaledBitmap(image, 256, 256, true);
                main.post(() -> { if (!closed) callback.ready(uri, small); });
            }
            image.recycle();
            CarDiagnostics.log(context, "Artwork render " + (SystemClock.elapsedRealtime() - started)
                    + "ms mode=" + mode + " changed=" + BoardArtProvider.latest);
        } catch (Exception error) {
            CarDiagnostics.log(context, "Artwork render failed " + error);
        }
    }
    void close() {
        closed = true;
        worker.post(() -> { if (board != null) board.disposeOffscreen(); thread.quitSafely(); });
    }
}


