import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws Sozin's Comet: a hot head with a tail streaking off it.
 *
 * PROGRAMMER ART, like the chi emblem and the armor sheets, and for the same reason — it
 * is this project's own pixels. Everything is a distance test against a point or a line,
 * so re-running reproduces the file byte for byte.
 *
 * DRAWN FOR ADDITIVE BLENDING, which is the one thing to know before editing it. The sky
 * renderer draws this the way vanilla draws the sun and moon: source alpha ONE, so the
 * texture is ADDED to whatever is behind it rather than covering it. That means the alpha
 * channel is really a brightness channel — black is invisible, white is a glow — and there
 * is no need for the quad's transparent parts to be any particular colour. It also means
 * the comet cannot darken the sky, which is what lets it sit over a bright noon sky
 * without punching a hole in it.
 *
 * SMALL ON PURPOSE. The brief was "a few pixels that look like a comet", not a second sun:
 * the head is a handful of pixels across and the rest of the sheet is the tail thinning
 * away to nothing, so on the sky it reads as a bright speck with a streak behind it.
 *
 *   java tools/GenComet.java src/main/resources/assets/atlamod/textures/environment
 */
public final class GenComet {

    private static final int SIZE = 128;
    private static final int SAMPLES = 3;

    /** Where the head sits, as a fraction of the sheet. Off-centre, so the tail has room. */
    private static final double HEAD_X = 0.70;
    private static final double HEAD_Y = 0.30;

    /** Where the tail points, and how far it reaches. */
    private static final double TAIL_X = 0.16;
    private static final double TAIL_Y = 0.84;

    /** The bright core, as a fraction of the sheet. A few pixels at this size. */
    private static final double HEAD_RADIUS = 0.035;

    /** The glow around it, which is what stops the head reading as a hard dot. */
    private static final double GLOW_RADIUS = 0.10;

    /** How wide the tail is where it leaves the head, and where it ends. */
    private static final double TAIL_NEAR = 0.030;
    private static final double TAIL_FAR = 0.085;

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0]
                : "src/main/resources/assets/atlamod/textures/environment");
        out.mkdirs();

        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setRGB(x, y, sample(x, y));
            }
        }

        File file = new File(out, "sozins_comet.png");
        ImageIO.write(image, "png", file);

        System.out.println("wrote " + file.getPath() + "  " + SIZE + "x" + SIZE);
    }

    /** One pixel, averaged over a grid of points inside it. */
    private static int sample(int px, int py) {
        long a = 0, r = 0, g = 0, b = 0;
        int count = SAMPLES * SAMPLES;

        for (int sy = 0; sy < SAMPLES; sy++) {
            for (int sx = 0; sx < SAMPLES; sx++) {
                int argb = at((px + (sx + 0.5) / SAMPLES) / SIZE,
                        (py + (sy + 0.5) / SAMPLES) / SIZE);

                int sa = (argb >>> 24) & 0xFF;
                a += sa;
                r += ((argb >> 16) & 0xFF) * sa;
                g += ((argb >> 8) & 0xFF) * sa;
                b += (argb & 0xFF) * sa;
            }
        }

        if (a == 0) return 0;

        return ((int) (a / count) << 24)
                | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
    }

    /**
     * The comet at one point, in 0..1 sheet coordinates.
     *
     * Built from two pieces added together: the TAIL, which is a line from the head to the
     * far point with a brightness falling off both along it and away from its axis, and
     * the HEAD, a small core with a glow around it. Added rather than layered because the
     * whole sheet is additive anyway — where they overlap, the head simply wins by being
     * far brighter.
     */
    private static int at(double x, double y) {
        double intensity = tail(x, y) + head(x, y);
        if (intensity <= 0.003) return 0;

        intensity = Math.min(1.0, intensity);

        // WHITE AT THE CORE, ORANGE FURTHER OUT. A comet that were one flat colour would
        // read as a smear; the shift from white through yellow to a deep orange is what
        // makes the head look hot and the tail look like it is cooling as it trails.
        double heat = Math.pow(intensity, 0.65);

        int red = 255;
        int green = (int) (110 + 145 * heat);
        int blue = (int) (30 + 215 * Math.pow(heat, 2.4));

        return (clamp((int) (intensity * 255)) << 24)
                | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    /** The streak, thinning and fading as it goes. */
    private static double tail(double x, double y) {
        double dx = TAIL_X - HEAD_X;
        double dy = TAIL_Y - HEAD_Y;
        double lengthSq = dx * dx + dy * dy;

        // How far along the tail this point is, 0 at the head and 1 at the far end.
        double along = ((x - HEAD_X) * dx + (y - HEAD_Y) * dy) / lengthSq;
        if (along < 0.0 || along > 1.0) return 0.0;

        // Distance from the tail's own axis.
        double axisX = HEAD_X + dx * along;
        double axisY = HEAD_Y + dy * along;
        double off = Math.hypot(x - axisX, y - axisY);

        double width = TAIL_NEAR + (TAIL_FAR - TAIL_NEAR) * along;
        if (off > width) return 0.0;

        // Across the tail: bright down the middle, nothing at the edge.
        double across = 1.0 - (off / width);
        across *= across;

        // Along the tail: full at the head, gone at the end, and falling faster than
        // linearly so most of the brightness stays near the head where it belongs.
        double fade = Math.pow(1.0 - along, 2.2);

        return across * fade * 0.85;
    }

    /** The head: a small hard core inside a soft glow. */
    private static double head(double x, double y) {
        double distance = Math.hypot(x - HEAD_X, y - HEAD_Y);

        if (distance <= HEAD_RADIUS) return 1.0;
        if (distance >= GLOW_RADIUS) return 0.0;

        double out = (distance - HEAD_RADIUS) / (GLOW_RADIUS - HEAD_RADIUS);
        return Math.pow(1.0 - out, 2.0);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private GenComet() {
    }
}
