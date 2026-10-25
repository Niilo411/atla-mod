import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Rewrites one namespace to another inside gzip-compressed NBT files (structure templates).
 *
 * Parsed tag by tag rather than searched as bytes: every NBT string carries its own length
 * prefix, so editing the raw bytes would corrupt the file. Only STRING values (and string
 * entries of lists) that start with "<from>:" are changed; names of compound keys are left
 * alone. A file is only written back when something actually changed.
 *
 *   java tools/NbtNamespace.java <from> <to> [--dry-run] file.nbt...
 */
public class NbtNamespace {
    private static String from, to;
    private static final List<String> found = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        from = args[0] + ":";
        to = args[1] + ":";
        boolean dry = false;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--dry-run")) { dry = true; continue; }
            Path file = Path.of(args[i]);
            found.clear();
            byte[] out;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                    new GZIPInputStream(Files.newInputStream(file))))) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream dataOut = new DataOutputStream(bytes);
                byte type = in.readByte();
                dataOut.writeByte(type);
                if (type != 0) {
                    dataOut.writeUTF(in.readUTF());
                    copy(type, in, dataOut);
                }
                dataOut.flush();
                out = bytes.toByteArray();
            }
            System.out.println(file + ": " + found.size() + " reference(s)" + (found.isEmpty() ? "" : " " + found));
            if (!found.isEmpty() && !dry) {
                try (OutputStream gz = new GZIPOutputStream(Files.newOutputStream(file))) {
                    gz.write(out);
                }
            }
        }
    }

    private static void copy(byte type, DataInputStream in, DataOutputStream out) throws IOException {
        switch (type) {
            case 1 -> out.writeByte(in.readByte());
            case 2 -> out.writeShort(in.readShort());
            case 3 -> out.writeInt(in.readInt());
            case 4 -> out.writeLong(in.readLong());
            case 5 -> out.writeFloat(in.readFloat());
            case 6 -> out.writeDouble(in.readDouble());
            case 7 -> { int n = in.readInt(); out.writeInt(n); byte[] b = new byte[n]; in.readFully(b); out.write(b); }
            case 8 -> out.writeUTF(rename(in.readUTF()));
            case 9 -> {
                byte inner = in.readByte(); int n = in.readInt();
                out.writeByte(inner); out.writeInt(n);
                for (int i = 0; i < n; i++) copy(inner, in, out);
            }
            case 10 -> {
                while (true) {
                    byte t = in.readByte();
                    out.writeByte(t);
                    if (t == 0) break;
                    out.writeUTF(in.readUTF());
                    copy(t, in, out);
                }
            }
            case 11 -> { int n = in.readInt(); out.writeInt(n); for (int i = 0; i < n; i++) out.writeInt(in.readInt()); }
            case 12 -> { int n = in.readInt(); out.writeInt(n); for (int i = 0; i < n; i++) out.writeLong(in.readLong()); }
            default -> throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static String rename(String value) {
        if (!value.startsWith(from)) return value;
        found.add(value);
        return to + value.substring(from.length());
    }
}
