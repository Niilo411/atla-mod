import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Generates the mod's armor-layer sheets from scratch.
 *
 * Everything here is procedural and seeded, so the output is reproducible and is
 * nobody's art but ours -- which is the whole point: the sheets it replaces were
 * tiled out of vanilla's cobblestone.
 */
public final class GenArmor {

    static final int W = 64, H = 32;

    // ---- helpers -------------------------------------------------------------

    static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    static int argb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    /** Wrapped distance on the sheet, so the pattern tiles instead of seaming. */
    static double wrapDist(double ax, double ay, double bx, double by) {
        double dx = Math.abs(ax - bx); if (dx > W / 2.0) dx = W - dx;
        double dy = Math.abs(ay - by); if (dy > H / 2.0) dy = H - dy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ---- stone ---------------------------------------------------------------

    /**
     * Cellular ("Worley") noise gives genuine cobble: scattered seed points, each
     * pixel taking the brightness of its nearest one, and darkening where two cells
     * meet so the mortar lines fall out of the structure rather than being drawn on.
     */
    static BufferedImage stone(long seed) {
        Random rnd = new Random(seed);
        int cells = 26;
        double[] cx = new double[cells], cy = new double[cells];
        int[] tone = new int[cells];
        for (int i = 0; i < cells; i++) {
            cx[i] = rnd.nextDouble() * W;
            cy[i] = rnd.nextDouble() * H;
            tone[i] = 104 + rnd.nextInt(46);          // per-stone base grey
        }

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
                int best = 0;
                for (int i = 0; i < cells; i++) {
                    double d = wrapDist(x + 0.5, y + 0.5, cx[i], cy[i]);
                    if (d < d1) { d2 = d1; d1 = d; best = i; }
                    else if (d < d2) { d2 = d; }
                }
                int v = tone[best];

                // Mortar: the closer two cells are to equidistant, the darker the seam.
                double edge = d2 - d1;
                if (edge < 1.6) v -= (int) ((1.6 - edge) * 26);

                // Rounded shading inside each stone, so they read as lumps not tiles.
                v += (int) (6 - d1 * 1.1);

                v += rnd.nextInt(13) - 6;             // grain
                img.setRGB(x, y, argb(v, v, v + 2));  // a hair cool, like real stone
            }
        }
        return img;
    }

    // ---- metal ---------------------------------------------------------------

    /**
     * Brushed steel: bright and cool, streaked vertically the way worked metal is,
     * with studs punched across it.
     *
     * Deliberately ISOTROPIC -- no full-width seams. Each region of a 64x32 armor
     * sheet maps to a different body part, so a line drawn across the whole sheet
     * comes out slashing over the helmet and down an arm in unrelated places. Local
     * features tile onto any region without caring where they land.
     */
    static BufferedImage metal(long seed) {
        Random rnd = new Random(seed);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);

        // One brightness offset per column, smoothed -> vertical brushing.
        double[] streak = new double[W];
        for (int x = 0; x < W; x++) streak[x] = rnd.nextGaussian() * 15.0;
        for (int pass = 0; pass < 2; pass++) {
            double[] s = new double[W];
            for (int x = 0; x < W; x++) {
                s[x] = (streak[(x - 1 + W) % W] + streak[x] * 2 + streak[(x + 1) % W]) / 4.0;
            }
            streak = s;
        }

        // Broad plate shading, from a handful of wrapped blobs rather than bands.
        int blobs = 7;
        double[] bx = new double[blobs], by = new double[blobs], bs = new double[blobs];
        for (int i = 0; i < blobs; i++) {
            bx[i] = rnd.nextDouble() * W;
            by[i] = rnd.nextDouble() * H;
            bs[i] = rnd.nextGaussian() * 13.0;
        }

        // Studs, scattered but kept apart so they read as rivets not noise.
        int studs = 14;
        int[] sx = new int[studs], sy = new int[studs];
        for (int i = 0; i < studs; i++) {
            for (int tries = 0; tries < 60; tries++) {
                int px = rnd.nextInt(W), py = rnd.nextInt(H);
                boolean ok = true;
                for (int j = 0; j < i; j++) {
                    if (wrapDist(px, py, sx[j], sy[j]) < 7) { ok = false; break; }
                }
                if (ok) { sx[i] = px; sy[i] = py; break; }
            }
        }

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double v = 150 + streak[x];

                for (int i = 0; i < blobs; i++) {
                    double d = wrapDist(x + 0.5, y + 0.5, bx[i], by[i]);
                    v += bs[i] * Math.exp(-(d * d) / 90.0);      // soft, wrapped falloff
                }

                for (int i = 0; i < studs; i++) {
                    double d = wrapDist(x + 0.5, y + 0.5, sx[i] + 0.5, sy[i] + 0.5);
                    if (d < 0.8)      v += 52;                   // lit crown
                    else if (d < 1.7) v -= 30;                   // shadowed rim
                }

                v += rnd.nextInt(11) - 5;                        // tooling grain
                int iv = (int) Math.round(v);
                img.setRGB(x, y, argb(iv - 6, iv, iv + 14));     // cool steel tint
            }
        }
        return img;
    }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        new File(dir).mkdirs();
        ImageIO.write(stone(20260831L), "png", new File(dir, "stone_layer_1.png"));
        ImageIO.write(stone(70414L),    "png", new File(dir, "stone_layer_2.png"));
        ImageIO.write(metal(31337L),    "png", new File(dir, "metal_layer_1.png"));
        ImageIO.write(metal(90210L),    "png", new File(dir, "metal_layer_2.png"));
        System.out.println("wrote 4 sheets to " + dir);
    }
}
