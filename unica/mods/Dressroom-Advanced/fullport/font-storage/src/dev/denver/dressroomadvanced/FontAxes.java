package dev.denver.dressroomadvanced;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** OpenType fvar/name metadata. No Android font initialization is needed to read it. */
final class FontAxes {
    record Axis(String tag, String name, float min, float defaultValue, float max, boolean hidden) {
        float clamp(float value) {
            return Math.max(min, Math.min(max, value));
        }

        String label() {
            return switch (tag) {
                case "wght" -> "Weight";
                case "wdth" -> "Width";
                case "opsz" -> "Optical size";
                case "slnt" -> "Slant";
                case "ital" -> "Italic";
                case "GRAD" -> "Grade";
                case "YTLC" -> "Lowercase height (YTLC)";
                case "BLED" -> "Bleed";
                default -> name == null || name.isBlank() ? tag : name + " (" + tag + ")";
            };
        }
    }

    private record Table(int offset, int length) {}

    private FontAxes() {}

    static boolean validTag(String tag) {
        if (tag == null || tag.length() != 4) return false;
        for (int i = 0; i < 4; i++) {
            char c = tag.charAt(i);
            if (c < 32 || c > 126 || c == '\'' || c == '\\') return false;
        }
        return true;
    }

    static List<Axis> parse(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return List.of();
        try {
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            int base = 0;
            if (b.getInt(0) == 0x74746366) {
                if (bytes.length < 16 || b.getInt(8) <= 0) return List.of();
                base = b.getInt(12); // Typeface.Builder uses collection face zero too.
            }
            if (!range(bytes.length, base, 12)) return List.of();
            int count = u16(b, base + 4);
            if (count > 4096 || !range(bytes.length, (long) base + 12, (long) count * 16))
                return List.of();
            Table fvar = null, names = null;
            for (int i = 0; i < count; i++) {
                int p = base + 12 + i * 16;
                int tag = b.getInt(p), offset = b.getInt(p + 8), length = b.getInt(p + 12);
                if (!range(bytes.length, offset, length)) return List.of();
                if (tag == 0x66766172) fvar = new Table(offset, length);
                if (tag == 0x6e616d65) names = new Table(offset, length);
            }
            if (fvar == null || fvar.length < 16 || u16(b, fvar.offset) != 1) return List.of();
            int offset = u16(b, fvar.offset + 4),
                    axes = u16(b, fvar.offset + 8),
                    stride = u16(b, fvar.offset + 10);
            if (axes == 0
                    || axes > 64
                    || offset < 16
                    || stride < 20
                    || !range(fvar.length, offset, (long) axes * stride)) return List.of();
            Map<Integer, String> labels = names(b, names);
            List<Axis> result = new ArrayList<>(axes);
            HashSet<String> tags = new HashSet<>();
            for (int i = 0; i < axes; i++) {
                int p = fvar.offset + offset + i * stride;
                String tag = new String(bytes, p, 4, StandardCharsets.US_ASCII);
                float min = b.getInt(p + 4) / 65536f,
                        value = b.getInt(p + 8) / 65536f,
                        max = b.getInt(p + 12) / 65536f;
                if (!validTag(tag) || !tags.add(tag) || min > value || value > max)
                    return List.of();
                result.add(
                        new Axis(
                                tag,
                                labels.get(u16(b, p + 18)),
                                min,
                                value,
                                max,
                                (u16(b, p + 16) & 1) != 0));
            }
            return List.copyOf(result);
        } catch (IndexOutOfBoundsException | IllegalArgumentException error) {
            return List.of();
        }
    }

    private static Map<Integer, String> names(ByteBuffer b, Table table) {
        Map<Integer, String> result = new HashMap<>();
        if (table == null || table.length < 6 || u16(b, table.offset) > 1) return result;
        int count = u16(b, table.offset + 2), storage = u16(b, table.offset + 4);
        if (count > 4096 || storage < 6L + count * 12L || !range(table.length, 6, count * 12L))
            return result;
        Map<Integer, Integer> priorities = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int p = table.offset + 6 + i * 12;
            int platform = u16(b, p), language = u16(b, p + 4), id = u16(b, p + 6);
            int length = u16(b, p + 8), offset = u16(b, p + 10);
            if ((platform != 0 && platform != 3)
                    || length == 0
                    || length > 512
                    || (length & 1) != 0
                    || !range(table.length, (long) storage + offset, length)) continue;
            int priority = platform == 3 && language == 0x409 ? 3 : platform == 0 ? 2 : 1;
            if (priorities.getOrDefault(id, 0) >= priority) continue;
            String name =
                    new String(
                                    b.array(),
                                    table.offset + storage + offset,
                                    length,
                                    StandardCharsets.UTF_16BE)
                            .replaceAll("[\\p{Cntrl}]", " ")
                            .strip();
            if (name.length() > 80) name = name.substring(0, 80);
            result.put(id, name);
            priorities.put(id, priority);
        }
        return result;
    }

    private static int u16(ByteBuffer b, int p) {
        return b.getShort(p) & 0xffff;
    }

    private static boolean range(int size, long offset, long length) {
        return offset >= 0 && length >= 0 && offset <= size && length <= size - offset;
    }
}
