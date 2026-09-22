import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the No bending emblem: a black and white chi symbol.
 *
 * PROGRAMMER ART ON PURPOSE, like the armor sheets GenArmor makes, and for the same
 * reason — it is this project's own pixels rather than anyone else's. Everything here is
 * pure arithmetic over a circle, so re-running it reproduces the file byte for byte.
 *
 * WHY A TAIJITU. "The chi symbol" has no single agreed drawing, and the brief asked for
 * black and white; the taijitu is the one symbol that is READ as chi by almost everyone
 * and is black and white by definition rather than by choice. It also survives being
 * shrunk to a 28 pixel button, which a more literal calligraphic mark would not.
 *
 * THE WHITE RING IS LOAD-BEARING, not decoration. The emblem is drawn over the selection
 * screen's dark 0xFF222222 box, so the black half of a bare taijitu would simply vanish
 * into the background and the symbol would read as a white crescent. The ring gives the
 * black side an edge to end at.
 *
 * ANTI-ALIASED BY SUPERSAMPLING rather than by Graphics2D, because every shape here is a
 * distance test against a circle centre: asking the same test at several points inside a
 * pixel and averaging is both shorter than setting up rendering hints and exactly
 * reproducible across JDKs, which the hint-driven path is not.
 *
 *   java tools/GenChiIcon.java src/main/resources/assets/atlamod/textures/gui/elements
 */
public final class GenChiIcon {

    /** Matches every other element emblem — see ElementIcons.SOURCE_SIZE. */
    private static final int SIZE = 256;

    /** Samples per axis inside one pixel. 4 means 16 tests, which is plenty at this size. */
    private static final int SAMPLES = 4;

    private static final int CLEAR = 0x00000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0]
                : "src/main/resources/assets/atlamod/textures/gui/elements");
        out.mkdirs();

        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setRGB(x, y, sample(x, y));
            }
        }

        File file = new File(out, "nobending_icon.png");
        ImageIO.write(image, "png", file);

        System.out.println("wrote " + file.getPath() + "  " + SIZE + "x" + SIZE);
    }

    /**
     * One pixel, averaged over a grid of points inside it.
     *
     * Averaged in STRAIGHT (un-premultiplied) alpha would darken the edge where a white
     * area meets transparency, so the colour channels are weighted by each sample's own
     * alpha and divided by the total alpha at the end. That is premultiplied averaging,
     * and it is what keeps the outer rim white rather than grey.
     */
    private static int sample(int px, int py) {
        long a = 0, r = 0, g = 0, b = 0;
        int count = SAMPLES * SAMPLES;

        for (int sy = 0; sy < SAMPLES; sy++) {
            for (int sx = 0; sx < SAMPLES; sx++) {
                double x = px + (sx + 0.5) / SAMPLES;
                double y = py + (sy + 0.5) / SAMPLES;

                int argb = at(x, y);
                int sa = (argb >>> 24) & 0xFF;

                a += sa;
                r += ((argb >> 16) & 0xFF) * sa;
                g += ((argb >> 8) & 0xFF) * sa;
                b += (argb & 0xFF) * sa;
            }
        }

        if (a == 0) return CLEAR;

        int alpha = (int) (a / count);
        return (alpha << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
    }

    /**
     * The symbol at one exact point.
     *
     * Read outside in: the outer ring, then which half of the taijitu the point falls in,
     * then the two lobes that bulge across the dividing line, then the two dots. Each
     * later test overrides the earlier one, which is precisely how the shape is built —
     * the small circles are cut out of whichever half they sit in.
     */
    private static int at(double x, double y) {
        double centre = SIZE / 2.0;
        double dx = x - centre;
        double dy = y - centre;
        double distance = Math.sqrt(dx * dx + dy * dy);

        double outer = SIZE * 0.47;
        double ring = SIZE * 0.035;

        if (distance > outer) return CLEAR;
        if (distance > outer - ring) return WHITE;

        double body = outer - ring;

        // The two lobes sit half way up the vertical axis and are half the body across,
        // which is what makes their edges meet the rim exactly and the S read as one
        // continuous curve rather than two circles stuck together.
        double half = body / 2.0;
        double topY = centre - half;
        double bottomY = centre + half;

        double toTop = Math.sqrt(dx * dx + (y - topY) * (y - topY));
        double toBottom = Math.sqrt(dx * dx + (y - bottomY) * (y - bottomY));

        // The straight split first: left half white, right half black.
        int colour = dx < 0 ? WHITE : BLACK;

        // Then the lobes, each carrying the OPPOSITE colour across the split.
        if (toTop <= half) colour = WHITE;
        if (toBottom <= half) colour = BLACK;

        // Then the eyes, each the colour of the half it is NOT in.
        double eye = body * 0.16;
        if (toTop <= eye) colour = BLACK;
        if (toBottom <= eye) colour = WHITE;

        return colour;
    }

    private GenChiIcon() {
    }
}
