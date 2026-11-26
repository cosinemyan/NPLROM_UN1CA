package dev.denver.dressroomadvanced;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/** Reads only the code needed for anchored bindings. Nothing runs in a draw hook. */
final class DexSignatures {
    private static final int MAX_DEX = 128 * 1024 * 1024;

    record Member(String owner, String name, String type) {
        Field field(ClassLoader loader) throws ReflectiveOperationException {
            Field field = load(owner, loader).getDeclaredField(name);
            if (!descriptor(field.getType()).equals(type)
                    || java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                throw new NoSuchFieldException("DEX field contract changed: " + this);
            field.setAccessible(true);
            return field;
        }
    }

    record Read(int offset, Member field) {}

    record Call(
            int offset,
            String owner,
            String name,
            List<String> parameters,
            String result,
            boolean isStatic) {
        Method method(ClassLoader loader) throws ReflectiveOperationException {
            Class<?>[] args = new Class<?>[parameters.size()];
            for (int i = 0; i < args.length; i++) args[i] = load(parameters.get(i), loader);
            Method method = load(owner, loader).getDeclaredMethod(name, args);
            if (!descriptor(method.getReturnType()).equals(result)
                    || java.lang.reflect.Modifier.isStatic(method.getModifiers()) != isStatic)
                throw new NoSuchMethodException("DEX call contract changed: " + owner);
            method.setAccessible(true);
            return method;
        }
    }

    record Body(
            String owner,
            String name,
            List<String> parameters,
            String result,
            int access,
            Map<String, Integer> anchors,
            List<Read> reads,
            List<Call> calls) {
        boolean matches(String... required) {
            if ((access & 8) != 0 || !parameters.isEmpty() || !result.equals("V")) return false;
            for (String anchor : required) if (!anchors.containsKey(anchor)) return false;
            return true;
        }

        Method method(ClassLoader loader) throws ReflectiveOperationException {
            Class<?>[] arguments = new Class<?>[parameters.size()];
            for (int i = 0; i < arguments.length; i++)
                arguments[i] = load(parameters.get(i), loader);
            Method method = load(owner, loader).getDeclaredMethod(name, arguments);
            if (!descriptor(method.getReturnType()).equals(result)
                    || java.lang.reflect.Modifier.isStatic(method.getModifiers())
                            != ((access & 8) != 0))
                throw new NoSuchMethodException("DEX method contract changed: " + owner);
            method.setAccessible(true);
            return method;
        }

        Member uniqueRead(String declaringType, String fieldType, String before)
                throws NoSuchFieldException {
            int limit = before == null ? Integer.MAX_VALUE : anchors.getOrDefault(before, -1);
            Set<Member> candidates = new LinkedHashSet<>();
            for (Read read : reads)
                if (read.offset < limit
                        && read.field.owner.equals(declaringType)
                        && read.field.type.equals(fieldType)) candidates.add(read.field);
            if (candidates.size() != 1)
                throw new NoSuchFieldException(
                        "Expected one "
                                + fieldType
                                + " read in "
                                + owner
                                + ", found "
                                + candidates.size());
            return candidates.iterator().next();
        }
    }

    private record Target(
            String owner, String name, List<String> parameters, String result, boolean callers) {}

    static Body body(Method method) throws ReflectiveOperationException, IOException {
        var args = new ArrayList<String>();
        for (Class<?> parameter : method.getParameterTypes()) args.add(descriptor(parameter));
        Target target =
                new Target(
                        descriptor(method.getDeclaringClass()),
                        method.getName(),
                        List.copyOf(args),
                        descriptor(method.getReturnType()),
                        false);
        List<Body> matches = find(method.getDeclaringClass().getClassLoader(), Set.of(), target);
        if (matches.size() != 1)
            throw new NoSuchMethodException("Missing or duplicate DEX method: " + method);
        return matches.get(0);
    }

    static List<Body> callers(Method method) throws ReflectiveOperationException, IOException {
        var parameters = new ArrayList<String>();
        for (Class<?> type : method.getParameterTypes()) parameters.add(descriptor(type));
        Target target =
                new Target(
                        descriptor(method.getDeclaringClass()),
                        method.getName(),
                        List.copyOf(parameters),
                        descriptor(method.getReturnType()),
                        true);
        return find(method.getDeclaringClass().getClassLoader(), Set.of(), target);
    }

    static List<Body> callers(
            ClassLoader loader,
            Class<?> owner,
            String name,
            Class<?> result,
            Class<?>... parameters)
            throws ReflectiveOperationException, IOException {
        Method method = owner.getMethod(name, parameters);
        if (method.getReturnType() != result)
            throw new NoSuchMethodException("Call result changed: " + method);
        var args = new ArrayList<String>();
        for (Class<?> parameter : parameters) args.add(descriptor(parameter));
        return find(
                loader,
                Set.of(),
                new Target(descriptor(owner), name, List.copyOf(args), descriptor(result), true));
    }

    static List<Body> find(ClassLoader loader, Set<String> anchors)
            throws ReflectiveOperationException, IOException {
        return find(loader, anchors, null);
    }

    private static List<Body> find(ClassLoader loader, Set<String> anchors, Target target)
            throws ReflectiveOperationException, IOException {
        Object elements = Reflect.field(Reflect.field(loader, "pathList"), "dexElements");
        Set<String> sources = new LinkedHashSet<>();
        for (int i = 0; i < Array.getLength(elements); i++) {
            Object dex = Reflect.field(Array.get(elements, i), "dexFile");
            if (dex instanceof dalvik.system.DexFile file) sources.add(file.getName());
        }
        List<Body> result = new ArrayList<>();
        for (String source : sources) result.addAll(read(new File(source), anchors, target));
        return List.copyOf(result);
    }

    static List<Body> read(File source, Set<String> anchors) throws IOException {
        return read(source, anchors, null);
    }

    private static List<Body> read(File source, Set<String> anchors, Target target)
            throws IOException {
        List<Body> result = new ArrayList<>();
        if (source.getName().endsWith(".dex")) {
            if (source.length() > MAX_DEX) throw new IOException("DEX is too large");
            return inspect(Files.readAllBytes(source.toPath()), anchors, target);
        }
        try (ZipFile zip = new ZipFile(source)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                if (entry.getSize() > MAX_DEX) throw new IOException("DEX is too large");
                try (var input = zip.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(MAX_DEX + 1);
                    if (bytes.length > MAX_DEX) throw new IOException("DEX is too large");
                    result.addAll(inspect(bytes, anchors, target));
                }
            }
        }
        return result;
    }

    static List<Body> inspect(byte[] bytes, Set<String> anchors) throws IOException {
        return inspect(bytes, anchors, null);
    }

    private static List<Body> inspect(byte[] bytes, Set<String> anchors, Target target)
            throws IOException {
        try {
            return new Reader(bytes).find(anchors, target);
        } catch (IndexOutOfBoundsException | IllegalArgumentException | ArithmeticException error) {
            throw new IOException("Invalid DEX while resolving editor signatures", error);
        }
    }

    private static final class Reader {
        final byte[] data;
        final int strings, types, protos, fields, methods;

        Reader(byte[] data) {
            this.data = data;
            if (data.length < 112
                    || data[0] != 'd'
                    || data[1] != 'e'
                    || data[2] != 'x'
                    || data[3] != '\n'
                    || u32(40) != 0x12345678)
                throw new IllegalArgumentException("Unsupported DEX header");
            strings = u32(60);
            types = u32(68);
            protos = u32(76);
            fields = u32(84);
            methods = u32(92);
        }

        int u16(int offset) {
            return (data[offset] & 255) | ((data[offset + 1] & 255) << 8);
        }

        int u32(int offset) {
            int value = u16(offset) | (u16(offset + 2) << 16);
            if (value < 0) throw new IllegalArgumentException("DEX offset exceeds limit");
            return value;
        }

        int leb(int[] cursor) {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int next = data[cursor[0]++] & 255;
                if (shift == 28 && (next & 0xf8) != 0)
                    throw new IllegalArgumentException("Invalid DEX ULEB128");
                value |= (next & 127) << shift;
                if ((next & 128) == 0) return value;
            }
            throw new IllegalArgumentException("Invalid DEX ULEB128");
        }

        int textOffset(int index) {
            int[] cursor = {u32(strings + index * 4)};
            leb(cursor);
            return cursor[0];
        }

        String text(int index) {
            int start = textOffset(index), end = start;
            while (data[end] != 0) end++;
            return new String(data, start, end - start, StandardCharsets.UTF_8);
        }

        String type(int index) {
            return text(u32(types + index * 4));
        }

        List<Body> find(Set<String> anchors, Target target) {
            Map<Integer, String> ids = new HashMap<>();
            for (int i = 0, size = u32(56); i < size; i++) {
                int start = textOffset(i);
                for (String anchor : anchors) {
                    int p = 0;
                    while (p < anchor.length() && data[start + p] == anchor.charAt(p)) p++;
                    if (p == anchor.length() && data[start + p] == 0) ids.put(i, anchor);
                }
            }
            List<Body> found = new ArrayList<>();
            Set<Integer> callIds = new LinkedHashSet<>();
            if (target != null && target.callers) {
                for (int i = 0, size = u32(88); i < size; i++) {
                    Call call = call(i, 0, false);
                    if (call.owner.equals(target.owner)
                            && call.name.equals(target.name)
                            && call.parameters.equals(target.parameters)
                            && call.result.equals(target.result)) callIds.add(i);
                }
                if (callIds.isEmpty()) return found;
            }
            if (ids.isEmpty() && target == null) return found;
            for (int i = 0, count = u32(96), base = u32(100); i < count; i++) {
                if (target != null
                        && !target.callers
                        && !type(u32(base + i * 32)).equals(target.owner)) continue;
                int offset = u32(base + i * 32 + 24);
                if (offset == 0) continue;
                int[] cursor = {offset};
                int staticFields = leb(cursor), instanceFields = leb(cursor);
                int direct = leb(cursor), virtual = leb(cursor);
                for (int n = 0; n < staticFields + instanceFields; n++) {
                    leb(cursor);
                    leb(cursor);
                }
                for (int countMethods : new int[] {direct, virtual}) {
                    int index = 0;
                    for (int n = 0; n < countMethods; n++) {
                        index += leb(cursor);
                        int access = leb(cursor), code = leb(cursor);
                        if (code == 0) continue;
                        if (target != null && !target.callers) {
                            int method = methods + index * 8, proto = protos + u16(method + 2) * 12;
                            if (!text(u32(method + 4)).equals(target.name)
                                    || !type(u32(proto + 4)).equals(target.result)) continue;
                            int args = u32(proto + 8), size = args == 0 ? 0 : u32(args);
                            if (size != target.parameters.size()) continue;
                            boolean same = true;
                            for (int a = 0; a < size; a++)
                                if (!type(u16(args + 4 + a * 2)).equals(target.parameters.get(a)))
                                    same = false;
                            if (!same) continue;
                        }
                        if (!callIds.isEmpty()) {
                            boolean calls = false;
                            for (int pc = 0, units = u32(code + 12), instructions = code + 16;
                                    pc < units;
                                    pc += width(instructions, pc)) {
                                int op = u16(instructions + pc * 2) & 255;
                                if ((op >= 0x6e && op <= 0x72 || op >= 0x74 && op <= 0x78)
                                        && callIds.contains(u16(instructions + pc * 2 + 2))) {
                                    calls = true;
                                    break;
                                }
                            }
                            if (!calls) continue;
                        }
                        scan(index, access, code, ids, found, target != null);
                    }
                }
            }
            return found;
        }

        void scan(
                int index,
                int access,
                int code,
                Map<Integer, String> ids,
                List<Body> found,
                boolean unanchored) {
            int units = u32(code + 12), base = code + 16;
            Map<String, Integer> anchors = new HashMap<>();
            for (int pc = 0; pc < units; ) {
                int op = u16(base + pc * 2) & 255;
                if (op == 0x1a || op == 0x1b) {
                    int id = op == 0x1a ? u16(base + pc * 2 + 2) : u32(base + pc * 2 + 2);
                    String anchor = ids.get(id);
                    if (anchor != null) anchors.putIfAbsent(anchor, pc);
                }
                int width = width(base, pc);
                if (width <= 0 || width > units - pc)
                    throw new IllegalArgumentException("Invalid DEX instruction span");
                pc += width;
            }
            if (anchors.isEmpty() && !unanchored) return;
            List<Read> reads = new ArrayList<>();
            List<Call> calls = new ArrayList<>();
            for (int pc = 0; pc < units; pc += width(base, pc)) {
                int op = u16(base + pc * 2) & 255;
                if (op >= 0x6e && op <= 0x72 || op >= 0x74 && op <= 0x78)
                    calls.add(call(u16(base + pc * 2 + 2), pc, op == 0x71 || op == 0x77));
                if (op >= 0x52 && op <= 0x58) {
                    int field = fields + u16(base + pc * 2 + 2) * 8;
                    reads.add(
                            new Read(
                                    pc,
                                    new Member(
                                            type(u16(field)),
                                            text(u32(field + 4)),
                                            type(u16(field + 2)))));
                }
            }
            int method = methods + index * 8, proto = protos + u16(method + 2) * 12;
            List<String> parameters = new ArrayList<>();
            int args = u32(proto + 8);
            if (args != 0)
                for (int p = 0, size = u32(args); p < size; p++)
                    parameters.add(type(u16(args + 4 + p * 2)));
            found.add(
                    new Body(
                            type(u16(method)),
                            text(u32(method + 4)),
                            List.copyOf(parameters),
                            type(u32(proto + 4)),
                            access,
                            Map.copyOf(anchors),
                            List.copyOf(reads),
                            List.copyOf(calls)));
        }

        Call call(int index, int pc, boolean isStatic) {
            int method = methods + index * 8, proto = protos + u16(method + 2) * 12;
            List<String> parameters = new ArrayList<>();
            int args = u32(proto + 8);
            if (args != 0)
                for (int p = 0, size = u32(args); p < size; p++)
                    parameters.add(type(u16(args + 4 + p * 2)));
            return new Call(
                    pc,
                    type(u16(method)),
                    text(u32(method + 4)),
                    List.copyOf(parameters),
                    type(u32(proto + 4)),
                    isStatic);
        }

        int width(int base, int pc) {
            int word = u16(base + pc * 2), op = word & 255;
            if (op == 0 && word != 0) {
                int size = u16(base + pc * 2 + 2);
                return switch (word >>> 8) {
                    case 1 -> Math.addExact(4, Math.multiplyExact(size, 2));
                    case 2 -> Math.addExact(2, Math.multiplyExact(size, 4));
                    case 3 -> Math.toIntExact(4 + ((long) size * u32(base + pc * 2 + 4) + 1) / 2);
                    default -> throw new IllegalArgumentException("Unknown DEX payload");
                };
            }
            if (op == 0x18) return 5;
            if (op == 0xfa || op == 0xfb) return 4;
            if (op == 3
                    || op == 6
                    || op == 9
                    || op == 0x14
                    || op == 0x17
                    || op == 0x1b
                    || op >= 0x24 && op <= 0x26
                    || op >= 0x2a && op <= 0x2c
                    || op >= 0x6e && op <= 0x78
                    || op == 0xfc
                    || op == 0xfd) return 3;
            if (op == 2
                    || op == 5
                    || op == 8
                    || op >= 0x13 && op <= 0x1c
                    || op == 0x1f
                    || op == 0x20
                    || op == 0x22
                    || op == 0x23
                    || op == 0x29
                    || op >= 0x2d && op <= 0x6d
                    || op >= 0x90 && op <= 0xaf
                    || op >= 0xd0 && op <= 0xe2
                    || op >= 0xfe) return 2;
            if (op >= 0xe3) throw new IllegalArgumentException("Unsupported DEX opcode");
            return 1;
        }
    }

    static String descriptor(Class<?> type) {
        if (type == void.class) return "V";
        if (type == int.class) return "I";
        if (type == boolean.class) return "Z";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type == byte.class) return "B";
        if (type == short.class) return "S";
        if (type == char.class) return "C";
        return type.isArray()
                ? type.getName().replace('.', '/')
                : "L" + type.getName().replace('.', '/') + ";";
    }

    private static Class<?> load(String descriptor, ClassLoader loader)
            throws ClassNotFoundException {
        return switch (descriptor) {
            case "V" -> void.class;
            case "I" -> int.class;
            case "Z" -> boolean.class;
            case "J" -> long.class;
            case "F" -> float.class;
            case "D" -> double.class;
            case "B" -> byte.class;
            case "S" -> short.class;
            case "C" -> char.class;
            default ->
                    Class.forName(
                            descriptor.startsWith("[")
                                    ? descriptor.replace('/', '.')
                                    : descriptor
                                            .substring(1, descriptor.length() - 1)
                                            .replace('/', '.'),
                            false,
                            loader);
        };
    }
}
