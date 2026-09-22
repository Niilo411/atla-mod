import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Recolours vanilla textures into this mod's spirit textures. Run by hand; see tools/README.
 *
 * Reads STRAIGHT OUT OF THE VANILLA JAR rather than out of a folder somebody extracted, so
 * the whole thing is one command and there is no half-unpacked copy of Mojang's assets
 * lying around to be committed by accident. Nothing vanilla is ever written back out — only
 * the recoloured results, which are this project's own files.
 *
 * TWO OPERATIONS, because the three sources needed different treatment. Which one each
 * texture gets was decided by MEASURING its hue histogram, not by eye:
 *
 *   shift — SELECTIVE HUE REPLACEMENT. Only pixels whose hue already falls inside a
 *           measured band are touched; their hue is set to {@link #TARGET_HUE} and their
 *           saturation and brightness are kept EXACTLY. Everything else — the grey stone
 *           matrix, black outlines, highlights — is copied through byte for byte.
 *
 *           emerald_ore    71% of it is grey stone; colour only at 116-148 deg.
 *           amethyst_shard not green at all: 260-279 deg with a pink tail at 320-329.
 *
 *   tint  — COLOURISE A GREYSCALE. Chainmail's armor has no hue whatever (measured: 100%
 *           of its opaque pixels are within 0.12 saturation of grey), so a hue replacement
 *           has nothing to find and would be a no-op. Brightness is preserved exactly, so
 *           all the shading and every outline survive; only hue and saturation are imposed.
 *
 *           NOT DIAMOND, because DIAMOND ARMOR IS ALREADY TEAL — measured at 167-177 deg —
 *           so shifting it to 180 would have produced a suit nobody could tell from a
 *           diamond one.
 *
 * Alpha is always copied through untouched, so cutouts stay exactly as vanilla drew them.
 */
public final class SpiritTextures {

    /** Teal. */
    private static final float TARGET_HUE = 180f / 360f;

    /**
     * How colourful a colourised greyscale becomes.
     *
     * Higher than it would need to be for a bright source, because CHAINMAIL IS DARK —
     * mean brightness 0.65 against iron's 0.79, and it never exceeds 0.80. The same
     * saturation reads as far more muted over a dark texture, so 0.45 (which suited iron)
     * came out washed and grey here.
     */
    private static final float TINT_SATURATION = 0.55f;

    /**
     * Above this brightness colourisation fades out, so specular highlights stay white.
     *
     * INERT FOR CHAINMAIL, whose brightest pixel is 0.80 — it is kept because it costs
     * nothing and is what stops a brighter source losing its white glints.
     */
    private static final float HIGHLIGHT_FLOOR = 0.85f;

    /** Below this saturation a pixel counts as grey and has no hue worth replacing. */
    private static final float GREY = 0.12f;

    private static final String VANILLA = "assets/minecraft/textures/";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: java tools/SpiritTextures.java <minecraft-resources.jar> "
                    + "<src/main/resources/assets/atlamod/textures>");
            System.err.println("the jar is under build/moddev/artifacts/, named "
                    + "neoforge-<version>-client-extra-aka-minecraft-resources.jar");
            System.exit(2);
        }

        try (ZipFile jar = new ZipFile(args[0])) {
            File out = new File(args[1]);

            shift(jar, out, "block/emerald_ore.png", "block/spirit_ore.png", 100, 160);
            shift(jar, out, "item/amethyst_shard.png", "item/spirit_shard.png", 230, 340);

            tint(jar, out, "models/armor/chainmail_layer_1.png", "models/armor/spirit_armor_layer_1.png");
            tint(jar, out, "models/armor/chainmail_layer_2.png", "models/armor/spirit_armor_layer_2.png");

            for (String piece : new String[]{ "helmet", "chestplate", "leggings", "boots" }) {
                tint(jar, out, "item/chainmail_" + piece + ".png", "item/spirit_" + piece + ".png");
            }
        }
    }

    /** Sets the hue of every pixel already inside [loDeg, hiDeg], keeping saturation and value. */
    private static void shift(ZipFile jar, File root, String from, String to, float loDeg, float hiDeg)
            throws Exception {

        BufferedImage img = read(jar, from);
        int changed = 0, kept = 0;

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) continue;

                float[] hsv = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
                float deg = hsv[0] * 360f;

                // Grey has no meaningful hue, and is exactly what has to survive.
                if (hsv[1] < GREY || deg < loDeg || deg > hiDeg) {
                    kept++;
                    continue;
                }

                img.setRGB(x, y, (alpha << 24) | (Color.HSBtoRGB(TARGET_HUE, hsv[1], hsv[2]) & 0xFFFFFF));
                changed++;
            }
        }

        write(img, root, to);
        System.out.printf("shift  %-24s -> %-36s %4d recoloured, %4d untouched%n",
                name(from), name(to), changed, kept);
    }

    /** Imposes hue and saturation on a greyscale, keeping brightness exactly. */
    private static void tint(ZipFile jar, File root, String from, String to) throws Exception {
        BufferedImage img = read(jar, from);
        int changed = 0, kept = 0;

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) continue;

                float[] hsv = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);

                float strength = hsv[2] <= HIGHLIGHT_FLOOR ? 1f
                        : Math.max(0f, 1f - (hsv[2] - HIGHLIGHT_FLOOR) / (1f - HIGHLIGHT_FLOOR));

                float saturation = TINT_SATURATION * strength;
                if (saturation <= 0f) {
                    kept++;
                    continue;
                }

                img.setRGB(x, y, (alpha << 24) | (Color.HSBtoRGB(TARGET_HUE, saturation, hsv[2]) & 0xFFFFFF));
                changed++;
            }
        }

        write(img, root, to);
        System.out.printf("tint   %-24s -> %-36s %4d recoloured, %4d untouched%n",
                name(from), name(to), changed, kept);
    }

    /** Always redrawn as ARGB, so alpha survives whatever the source was stored as. */
    private static BufferedImage read(ZipFile jar, String path) throws Exception {
        ZipEntry entry = jar.getEntry(VANILLA + path);
        if (entry == null) throw new IllegalStateException("not in the jar: " + VANILLA + path);

        try (InputStream in = jar.getInputStream(entry)) {
            BufferedImage source = ImageIO.read(in);
            BufferedImage argb = new BufferedImage(source.getWidth(), source.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            argb.getGraphics().drawImage(source, 0, 0, null);
            return argb;
        }
    }

    private static void write(BufferedImage img, File root, String path) throws Exception {
        File file = new File(root, path);
        file.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", file);
    }

    private static String name(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
