package local.driveboard;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.graphics.shapes.CornerRounding;
import androidx.graphics.shapes.RoundedPolygon;
import androidx.graphics.shapes.ShapesKt;
import androidx.graphics.shapes.Shapes_androidKt;

/** Static graphics-shapes paths sized to their drawing rectangles. */
final class ExpressiveShapes {
    private static final Path STAR = Shapes_androidKt.toPath(ShapesKt.star(
            RoundedPolygon.Companion, 8, 1f, .78f,
            new CornerRounding(.16f, .2f), new CornerRounding(.08f, .2f),
            null, 0f, 0f), new Path());
    private static final Path COOKIE = Shapes_androidKt.toPath(ShapesKt.star(
            RoundedPolygon.Companion, 9, 1f, .91f,
            new CornerRounding(.18f, .3f), new CornerRounding(.18f, .3f),
            null, 0f, 0f), new Path());

    private ExpressiveShapes() {}

    static Path star(RectF bounds) {
        return placed(STAR, bounds);
    }

    static Path cookie(RectF bounds) {
        return placed(COOKIE, bounds);
    }

    private static Path placed(Path source, RectF bounds) {
        Matrix matrix = new Matrix();
        matrix.setScale(bounds.width() / 2f, bounds.height() / 2f);
        matrix.postTranslate(bounds.centerX(), bounds.centerY());
        Path path = new Path();
        source.transform(matrix, path);
        return path;
    }
}
