package local.driveboard;

import java.util.ArrayDeque;

/** Pixel-only car paint selection and shading; independent of Android. */
public final class CarPaint {
    private CarPaint() {}

    public static int sample(int[] pixels, int width, int height, int x, int y) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (int py = Math.max(0, y - 2); py <= Math.min(height - 1, y + 2); py++) {
            for (int px = Math.max(0, x - 2); px <= Math.min(width - 1, x + 2); px++) {
                int color = pixels[py * width + px];
                if ((color >>> 24) < 128) {
                    continue;
                }
                red += (color >>> 16) & 255;
                green += (color >>> 8) & 255;
                blue += color & 255;
                count++;
            }
        }
        if (count == 0) {
            return pixels[y * width + x];
        }
        return 0xFF000000 | ((red / count) << 16) | ((green / count) << 8) | (blue / count);
    }

    public static int[] recolor(int[] pixels, int width, int height, int x, int y,
            int tolerance, int target, float brightness) {
        if (width <= 0 || height <= 0 || pixels.length != width * height
                || x < 0 || x >= width || y < 0 || y >= height) {
            throw new IllegalArgumentException("Invalid image or tap");
        }
        int source = sample(pixels, width, height, x, y);
        int[] result = pixels.clone();
        if ((pixels[y * width + x] >>> 24) < 128) {
            return result;
        }
        byte[] mask = new byte[pixels.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = y * width + x;
        int limit = Math.max(1, Math.min(100, tolerance));
        if (distance(source, pixels[start]) > limit) {
            return result;
        }
        queue.add(start);
        mask[start] = 2;
        while (!queue.isEmpty()) {
            int at = queue.removeFirst();
            int px = at % width;
            int py = at / width;
            if (px > 0) {
                visit(pixels, mask, queue, at - 1, source, limit);
            }
            if (px + 1 < width) {
                visit(pixels, mask, queue, at + 1, source, limit);
            }
            if (py > 0) {
                visit(pixels, mask, queue, at - width, source, limit);
            }
            if (py + 1 < height) {
                visit(pixels, mask, queue, at + width, source, limit);
            }
        }
        float[] targetHsl = hsl(target);
        float sourceLight = hsl(source)[2];
        for (int index = 0; index < pixels.length; index++) {
            if (mask[index] != 2) {
                continue;
            }
            float light = hsl(pixels[index])[2];
            float shaded = clamp(targetHsl[2] * brightness + light - sourceLight);
            int painted = rgb(targetHsl[0], targetHsl[1], shaded);
            int px = index % width;
            int py = index / width;
            boolean edge = px == 0 || py == 0 || px == width - 1 || py == height - 1;
            edge |= px > 0 && mask[index - 1] != 2;
            edge |= px + 1 < width && mask[index + 1] != 2;
            edge |= py > 0 && mask[index - width] != 2;
            edge |= py + 1 < height && mask[index + width] != 2;
            result[index] = edge ? blend(pixels[index], painted, 0.55f)
                    : (pixels[index] & 0xFF000000) | (painted & 0x00FFFFFF);
        }
        return result;
    }

    private static void visit(int[] pixels, byte[] mask, ArrayDeque<Integer> queue,
            int index, int source, int tolerance) {
        if (mask[index] != 0) {
            return;
        }
        if ((pixels[index] >>> 24) < 128 || distance(source, pixels[index]) > tolerance) {
            mask[index] = 1;
            return;
        }
        mask[index] = 2;
        queue.add(index);
    }

    private static double distance(int first, int second) {
        int dr = ((first >>> 16) & 255) - ((second >>> 16) & 255);
        int dg = ((first >>> 8) & 255) - ((second >>> 8) & 255);
        int db = (first & 255) - (second & 255);
        double luminance = 0.2126 * dr + 0.7152 * dg + 0.0722 * db;
        double chroma = 0.2126 * dr * dr + 0.7152 * dg * dg + 0.0722 * db * db;
        return Math.sqrt(0.55 * chroma + 0.45 * luminance * luminance);
    }

    private static int blend(int old, int fresh, float weight) {
        int red = Math.round(((old >>> 16) & 255) * (1 - weight) + ((fresh >>> 16) & 255) * weight);
        int green = Math.round(((old >>> 8) & 255) * (1 - weight) + ((fresh >>> 8) & 255) * weight);
        int blue = Math.round((old & 255) * (1 - weight) + (fresh & 255) * weight);
        return (old & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static float[] hsl(int color) {
        float red = ((color >>> 16) & 255) / 255f;
        float green = ((color >>> 8) & 255) / 255f;
        float blue = (color & 255) / 255f;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        float light = (max + min) / 2f;
        float saturation = delta == 0 ? 0 : delta / (1 - Math.abs(2 * light - 1));
        float hue = 0;
        if (delta != 0 && max == red) {
            hue = ((green - blue) / delta) % 6;
        } else if (delta != 0 && max == green) {
            hue = (blue - red) / delta + 2;
        } else if (delta != 0) {
            hue = (red - green) / delta + 4;
        }
        return new float[]{(hue * 60 + 360) % 360, saturation, light};
    }

    public static int rgb(float hue, float saturation, float light) {
        float chroma = (1 - Math.abs(2 * light - 1)) * saturation;
        float x = chroma * (1 - Math.abs((hue / 60f) % 2 - 1));
        float middle = light - chroma / 2;
        float red = 0;
        float green = 0;
        float blue = 0;
        if (hue < 60) {
            red = chroma;
            green = x;
        } else if (hue < 120) {
            red = x;
            green = chroma;
        } else if (hue < 180) {
            green = chroma;
            blue = x;
        } else if (hue < 240) {
            green = x;
            blue = chroma;
        } else if (hue < 300) {
            red = x;
            blue = chroma;
        } else {
            red = chroma;
            blue = x;
        }
        return 0xFF000000 | (Math.round((red + middle) * 255) << 16)
                | (Math.round((green + middle) * 255) << 8) | Math.round((blue + middle) * 255);
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }
}

