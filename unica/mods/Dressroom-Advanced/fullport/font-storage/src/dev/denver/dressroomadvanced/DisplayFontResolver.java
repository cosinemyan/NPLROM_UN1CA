/* Native integration of Dressroom Advanced by BlueOutside.
 * Upstream: https://github.com/BlueOutside/Dressroom-Advanced
 * Redistribution is subject to LICENSE.upstream in this mod.
 */
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Exact font source adapter. Never guesses a font by scanning package assets. */
public final class DisplayFontResolver {
    private static final File BANK = new File("/system/etc/unica/font_selector/SamsungSans.apk");
    private static final int LIMIT = 32 * 1024 * 1024;
    private static final java.util.Map<String,Source> sources=new java.util.LinkedHashMap<>(4,.75f,true) {
        protected boolean removeEldestEntry(java.util.Map.Entry<String,Source> entry){return size()>2;}
    };
    public static final class Source {
        public final String identity;
        private final byte[] bytes;
        private final List<FontAxes.Axis> axes;
        private final Typeface fallback;
        private String contentHash;
        private final java.util.Map<String,Typeface> faces=new java.util.LinkedHashMap<>(8,.75f,true) {
            protected boolean removeEldestEntry(java.util.Map.Entry<String,Typeface> entry){return size()>8;}
        };
        Source(String identity, byte[] bytes, Typeface fallback) {
            this.identity = identity; this.bytes = bytes; this.fallback = fallback;
            axes = bytes == null ? Collections.emptyList() : FontAxes.parse(bytes);
        }
        /** Only axes physically present in the selected font are advertised. */
        public Bundle capabilities() {
            Bundle result = new Bundle();
            for (FontAxes.Axis axis : axes) {
                Bundle range = new Bundle(); range.putString("label", axis.label());
                range.putFloat("min", axis.min()); range.putFloat("default", axis.defaultValue());
                range.putFloat("max", axis.max()); result.putBundle(axis.tag(), range);
            }
            return result;
        }
        public Bundle cleanAxes(Bundle requested) {
            Bundle result = new Bundle();
            if (requested == null) return result;
            for (FontAxes.Axis axis : axes) if (requested.containsKey(axis.tag())) {
                Object value = requested.get(axis.tag());
                if (value instanceof Number && Float.isFinite(((Number)value).floatValue()))
                    result.putFloat(axis.tag(), axis.clamp(((Number)value).floatValue()));
            }
            return result;
        }
        public synchronized Typeface typeface(Context context, Bundle requested) {
            if (bytes == null) return fallback;
            File directory = new File(context.getCacheDir(), "dressroom_advanced_fonts");
            if (!directory.isDirectory() && !directory.mkdirs()) return fallback;
            try {
                if(contentHash==null)contentHash=hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                String hash=contentHash;
                File file = new File(directory, hash + ".font");
                synchronized (DisplayFontResolver.class) {
                    if (!file.isFile()) {
                        android.util.AtomicFile atomic = new android.util.AtomicFile(file);
                        FileOutputStream output = atomic.startWrite();
                        try { output.write(bytes); atomic.finishWrite(output); }
                        catch (IOException error) { atomic.failWrite(output); throw error; }
                    }
                }
                Bundle clean = cleanAxes(requested);
                StringBuilder coordinates = new StringBuilder();
                List<String> tags = new ArrayList<>(clean.keySet()); Collections.sort(tags);
                for (String tag : tags) {
                    if (coordinates.length() > 0) coordinates.append(",");
                    coordinates.append("'").append(tag).append("' ").append(clean.getFloat(tag));
                }
                Typeface cached=faces.get(coordinates.toString());if(cached!=null)return cached;
                Typeface.Builder builder = new Typeface.Builder(file);
                if (coordinates.length() != 0) builder.setFontVariationSettings(coordinates.toString());
                Typeface face=builder.build();if(face!=null)faces.put(coordinates.toString(),face);return face;
            } catch (Exception error) { return fallback; }
        }
        private static String hex(byte[] data) {
            StringBuilder out = new StringBuilder();
            for (byte value : data) out.append(String.format(Locale.ROOT, "%02x", value & 255));
            return out.toString();
        }
    }
    /** Display font, deliberately independent of the editor's selected clock font. */
    public static Source currentDisplay(Context context) {
        // Exact Samsung API used by SecFontStylePreferenceFragment.setFontStyleList().
        // One UI 9 Typeface.setFlipFonts() appends DroidSans.ttf to this directory.
        String selected = "";
        try {
            Object value = Typeface.class.getMethod("semGetFontPathOfCurrentFontStyle", Context.class, int.class)
                    .invoke(null, context, 1);
            selected = value instanceof String ? (String)value : "";
            if (!selected.isEmpty() && !"default".equals(selected)) {
                Source applied = resolve(context, new File(selected, "DroidSans.ttf").getPath());
                if (applied != null) return applied;
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) { /* stock fallback below */ }
        String theme = Settings.System.getString(context.getContentResolver(), "theme_font");
        Source source = resolve(context, theme);
        return source != null ? source : new Source("display:default:" + selected + ":" + String.valueOf(theme), null, Typeface.DEFAULT);
    }
    public static synchronized Source resolve(Context context,String path) {
        if(path==null||path.isEmpty())return null;
        File file=path.startsWith("unica_font:")?BANK:new File(path);
        String key=path+":"+file.lastModified()+":"+file.length();
        Source cached=sources.get(key);if(cached!=null)return cached;
        Source found=resolveUncached(context,path);if(found!=null)sources.put(key,found);return found;
    }
    private static Source resolveUncached(Context context, String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            if (path.startsWith("unica_font:")) {
                String xml = path.substring("unica_font:".length());
                if (!xml.matches("[A-Za-z0-9_. -]+\\.xml") || xml.contains("..")) return null;
                try (ZipFile bank = new ZipFile(BANK)) {
                    ZipEntry descriptor = bank.getEntry("assets/xml/" + xml);
                    if (descriptor == null || descriptor.getSize() > 256 * 1024) return null;
                    String font = null;
                    try (InputStream input = bank.getInputStream(descriptor)) {
                        XmlPullParser parser = Xml.newPullParser(); parser.setInput(input, "UTF-8");
                        while (parser.next() != XmlPullParser.END_DOCUMENT)
                            if (parser.getEventType() == XmlPullParser.START_TAG && "filename".equals(parser.getName())) {
                                font = parser.nextText().trim(); break;
                            }
                    }
                    if (font == null || font.contains("/") || font.contains("\\") || font.contains("..")) return null;
                    ZipEntry entry = bank.getEntry("assets/fonts/" + font);
                    if (entry == null) return null;
                    return new Source(path, read(bank.getInputStream(entry)), Typeface.DEFAULT);
                }
            }
            File file = new File(path);
            String canonical = file.getCanonicalPath();
            // File sources are Samsung's existing clock/theme paths, not arbitrary provider input.
            if (!canonical.startsWith("/system/fonts/") && !canonical.startsWith("/data/overlays/")
                    && !canonical.startsWith("/data/app/") && !canonical.startsWith("/data/app_fonts/")) return null;
            if (canonical.endsWith(".apk")) return null; // No arbitrary first-font fallback.
            return new Source("file:" + canonical + ":" + file.lastModified(), read(new FileInputStream(file)), Typeface.DEFAULT);
        } catch (Exception error) { return null; }
    }
    private static byte[] read(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] block = new byte[16384]; int count;
            while ((count = source.read(block)) != -1) {
                if (out.size() > LIMIT - count) throw new IOException("Font too large");
                out.write(block, 0, count);
            }
            return out.toByteArray();
        }
    }
}
