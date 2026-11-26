// Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.fonts.FontVariationAxis;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class VariableFonts {
    interface Profile {
        Bundle settings(View view);
    }

    static final class Axes {
        final List<FontAxes.Axis> values;

        Axes(List<FontAxes.Axis> values) {
            this.values = values;
        }

        boolean hasAny() {
            return !values.isEmpty();
        }
    }

    private static final class Source {
        final String key;
        final File file;
        final AssetManager assets;
        final String asset;
        final Axes axes;
        final android.net.Uri uri;

        Source(String key, File file, AssetManager assets, String asset, Axes axes) {
            this(key, file, assets, asset, axes, null);
        }

        Source(
                String key,
                File file,
                AssetManager assets,
                String asset,
                Axes axes,
                android.net.Uri uri) {
            this.uri = uri;
            this.key = key;
            this.file = file;
            this.assets = assets;
            this.asset = asset;
            this.axes = axes;
        }
    }

    private static final Map<String, Source> sources =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(8, .75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Source> eldest) {
                            return size() > 6;
                        }
                    });
    private static final Map<String, Typeface> typefaces =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(16, .75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Typeface> eldest) {
                            return size() > 24;
                        }
                    });
    private static final Map<View, String> applied =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> importedClocks =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.Set<Method> renderers = new java.util.HashSet<>();
    private static final Map<View, Bundle> configured =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.Set<ClassLoader> installed =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<View, List<java.lang.ref.WeakReference<TextView>>> targets =
            Collections.synchronizedMap(new WeakHashMap<>());

    private VariableFonts() {}



    static Axes axes(Context context, String path, int type) {
        DisplayFontResolver.Source nativeSource = DisplayFontResolver.resolve(context,path);
        if(nativeSource != null) return fromNative(nativeSource);
        if (!eligible(type, path)) return null;
        Source source = source(context, path, type);
        return source == null ? null : source.axes;
    }

    static synchronized void configure(DressroomModule module, View clock, Bundle settings) { configure(clock,settings); }

    static void configure(View clock, Bundle settings) {
        if (settings == null) configured.remove(clock);
        else configured.put(clock, settings);
        if (fileId(settings).isEmpty()) importedClocks.remove(clock);
        else importedClocks.put(clock, true);
    }

    private static Bundle settings(View clock, Profile fallback) {
        Bundle settings = configured.get(clock);
        return settings != null || fallback == null ? settings : fallback.settings(clock);
    }

    static String fileId(Bundle profile) {
        return profile == null ? "" : profile.getString("fontFile", "");
    }

    private static String profileKey(Bundle profile) {
        return (profile != null && profile.getBoolean("followDisplayFont") ? "display:" + profile.getString("fontSourceIdentity", "") : fileId(profile))
                + "|" + FontAxisSettings.key(FontAxisSettings.values(profile));
    }

    static Axes importedAxes(Context context, String id) {
        Source source = imported(context, id);
        return source == null ? null : source.axes;
    }

    private static Source imported(Context context, String id) {
        if (id == null || id.isEmpty()) return null;
        String key = "import|" + id;
        Source cached = sources.get(key);
        if (cached != null) return cached;
        try {
            android.net.Uri uri = FontFiles.uri(id);
            byte[] bytes = read(context.getContentResolver().openInputStream(uri));
            if (bytes == null) return null;
            Source source = new Source(key, null, null, null, parse(bytes), uri);
            sources.put(key, source);
            return source;
        } catch (RuntimeException | java.io.IOException error) {
            return null;
        }
    }

    static Axes fromNative(DisplayFontResolver.Source source) {
        Bundle capabilities=source.capabilities(); java.util.List<FontAxes.Axis> axes=new java.util.ArrayList<>();
        for(String tag:capabilities.keySet()) {Bundle a=capabilities.getBundle(tag);
            axes.add(new FontAxes.Axis(tag,a.getString("label",tag),a.getFloat("min"),a.getFloat("default"),a.getFloat("max"),false));}
        return axes.isEmpty()?null:new Axes(axes);
    }
    static Axes displayAxes(Context context) { return fromNative(DisplayFontResolver.currentDisplay(context)); }
    static Bundle displayValues(Context context, Bundle profile) {
        if (profile == null || !DisplayFontResolver.currentDisplay(context).identity.equals(profile.getString("fontSourceIdentity", ""))) return new Bundle();
        return DisplayFontResolver.currentDisplay(context).cleanAxes(FontAxisSettings.values(profile));
    }
    static Typeface nativeFixed(View clock, Typeface original, TextView[] views) {
        Typeface replacement=nativeTypeface(clock,original);
        remember(clock,views);
        Bundle profile=configured.get(clock); if(profile==null) profile=NativeClockRuntime.profile(clock);
        applied.put(clock,profileKey(profile));
        return replacement;
    }
    static void nativeApplied(TextView[] views,Typeface replacement) { ensureTypeface(views,replacement); }
    static boolean nativeVariable(View clock,TextView[] views) {
        Bundle profile=configured.get(clock); if(profile==null) profile=NativeClockRuntime.profile(clock);
        if(profile==null||(!profile.getBoolean("followDisplayFont")&&fileId(profile).isEmpty())) return false;
        Typeface replacement=nativeTypeface(clock,null);if(replacement==null)return false;
        try { remember(clock,views); FontBinding.of(clock).apply(clock,replacement,views);ensureTypeface(views,replacement);applied.put(clock,profileKey(profile));return true; }
        catch(Exception ignored){return false;}
    }
    static Object nativeRender(View clock,Object requested) {
        Bundle profile=configured.get(clock); if(profile==null) profile=NativeClockRuntime.profile(clock);
        if(profile==null||(FontAxisSettings.nativeStretch(profile)&&!profile.getBoolean("followDisplayFont")))return requested;
        if(!profile.getBoolean("followDisplayFont")&&fileId(profile).isEmpty())return requested;
        try {return FontBinding.of(clock).plain(requested);}catch(Exception ignored){return requested;}
    }
    static Typeface nativeTypeface(View clock, Typeface original) {
        Bundle profile=configured.get(clock);
        if(profile == null) profile=NativeClockRuntime.profile(clock);
        if(profile == null) return original;
        if(profile.getBoolean("followDisplayFont")) return DisplayFontResolver.currentDisplay(clock.getContext()).typeface(clock.getContext(),displayValues(clock.getContext(),profile));
        Typeface result=typeface(clock.getContext(),font(clock),FontAxisSettings.values(profile),fileId(profile));
        return result==null?original:result;
    }
    static Typeface typeface(Context context, Object font, Bundle values, String imported) {
        if (font == null) return null;
        try {
            int type = ((Number) Reflect.call(font, "getFontType")).intValue();
            Object rawPath = Reflect.call(font, "getFontPath");
            String path = rawPath instanceof String ? (String) rawPath : "";
            if(imported.isEmpty()) {DisplayFontResolver.Source selected=DisplayFontResolver.resolve(context,path);
                if(selected!=null && !selected.capabilities().isEmpty()) return selected.typeface(context,values);}

            Object rawWeight = Reflect.call(font, "getFontWeight");
            Integer weight = null;
            if (imported.isEmpty() && rawWeight instanceof Number) {
                float value = ((Number) rawWeight).floatValue();
                if (Float.isFinite(value) && value > 0) weight = Math.round(value);
            }
            if (imported.isEmpty() && !eligible(type, path)) return null;
            Source source =
                    imported.isEmpty() ? source(context, path, type) : imported(context, imported);
            if (source == null || (source.axes == null && imported.isEmpty())) return null;
            List<FontVariationAxis> variations = new ArrayList<>();
            StringBuilder keyParts = new StringBuilder(source.key);
            for (FontAxes.Axis axis :
                    source.axes == null ? List.<FontAxes.Axis>of() : source.axes.values) {
                float value =
                        values != null && values.containsKey(axis.tag())
                                ? values.getFloat(axis.tag())
                                : axis.tag().equals("wght") && weight != null
                                        ? weight
                                        : axis.defaultValue();
                if (!Float.isFinite(value)) value = axis.defaultValue();
                value = axis.clamp(value);
                variations.add(new FontVariationAxis(axis.tag(), value));
                keyParts.append('|')
                        .append(axis.tag())
                        .append('=')
                        .append(Float.floatToIntBits(value));
            }
            if (variations.isEmpty() && imported.isEmpty()) return null;
            String key = keyParts.toString();
            Typeface cached = typefaces.get(key);
            if (cached != null) return cached;
            Typeface result;
            if (source.uri != null) {
                try (android.os.ParcelFileDescriptor fd =
                        context.getContentResolver().openFileDescriptor(source.uri, "r")) {
                    if (fd == null) return null;
                    Typeface.Builder builder = new Typeface.Builder(fd.getFileDescriptor());
                    if (!variations.isEmpty())
                        builder.setFontVariationSettings(
                                variations.toArray(new FontVariationAxis[0]));
                    result = builder.build();
                }
            } else if (source.asset != null) {
                Typeface.Builder builder = new Typeface.Builder(source.assets, source.asset);
                builder.setFontVariationSettings(variations.toArray(new FontVariationAxis[0]));
                result = builder.build();
            } else if (source.file != null) {
                Typeface.Builder builder = new Typeface.Builder(source.file);
                builder.setFontVariationSettings(variations.toArray(new FontVariationAxis[0]));
                result = builder.build();
            } else return null;
            if (result != null) typefaces.put(key, result);
            return result;
        } catch (Throwable error) {
            return null;
        }
    }

    static void preview(View clock, Bundle settings) {
        if (clock == null) return;
        Object font = font(clock);
        Bundle values = FontAxisSettings.values(settings);
        configure(clock, settings);
        Typeface replacement = settings != null && settings.getBoolean("followDisplayFont")
                ? DisplayFontResolver.currentDisplay(clock.getContext()).typeface(clock.getContext(),displayValues(clock.getContext(),settings))
                : typeface(clock.getContext(), font, values, fileId(settings));
        if (replacement == null) return;
        try {
            List<TextView> current = new ArrayList<>();
            List<java.lang.ref.WeakReference<TextView>> remembered = targets.get(clock);
            if (remembered != null)
                for (java.lang.ref.WeakReference<TextView> reference : remembered) {
                    TextView view = reference.get();
                    if (view != null) current.add(view);
                }
            // Go through the native setter so its fixed-typeface cache follows the preview.
            if (current.isEmpty()) ClockBinding.of(clock).render(clock, font);
            else {
                TextView[] views = current.toArray(new TextView[0]);
                FontBinding.of(clock).apply(clock, replacement, views);
                ensureTypeface(views, replacement);
            }
            applied.put(clock, profileKey(settings));
            clock.requestLayout();
            clock.invalidate();
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Variable font preview unavailable", error);
        }
    }

    private static void ensureTypeface(TextView[] views, Typeface typeface) {
        if (views == null) return;
        for (TextView view : views) {
            if (view != null && view.getTypeface() != typeface) view.setTypeface(typeface);
        }
    }

    private static void remember(View clock, TextView[] views) {
        List<java.lang.ref.WeakReference<TextView>> current = targets.get(clock);
        List<TextView> unique = new ArrayList<>();
        if (current != null)
            for (java.lang.ref.WeakReference<TextView> reference : current) {
                TextView view = reference.get();
                if (view != null && view.isAttachedToWindow() && !unique.contains(view))
                    unique.add(view);
            }
        if (views != null)
            for (TextView view : views)
                if (view != null && !unique.contains(view) && unique.size() < 64) unique.add(view);
        List<java.lang.ref.WeakReference<TextView>> remembered = new ArrayList<>();
        for (TextView view : unique) remembered.add(new java.lang.ref.WeakReference<>(view));
        targets.put(clock, remembered);
    }

    static void forget(View clock) {
        if (clock != null) {
            applied.remove(clock);
            targets.remove(clock);
        }
    }

    static boolean needsRefresh(View clock, Bundle profile) {
        Bundle values = FontAxisSettings.values(profile);
        String previous = applied.get(clock);
        return previous == null
                ? !values.isEmpty() || !fileId(profile).isEmpty() || (profile != null && profile.getBoolean("followDisplayFont"))
                : !previous.equals(profileKey(profile));
    }

    private static Object font(View clock) {
        try {
            return ClockBinding.of(clock).font(clock);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private static Source source(Context context, String path, int type) {
        String value = path == null ? "" : path.trim();
        if (value.isEmpty() && (type == 700 || type == 0)) value = themeFont(context);
        if (value.isEmpty()) return null;
        String key = type + "|" + value;
        Source cached = sources.get(key);
        if (cached != null) return cached;
        Source result = resolve(context, value);
        if (result != null) sources.put(key, result);
        return result;
    }

    private static Source resolve(Context context, String value) {
        File file = new File(value);
        // A package can contain several faces. Its first variable asset is not
        // evidence of the selected face. Display mode resolves DroidSans.ttf;
        // UN1CA entries resolve their exact XML mapping before this path.
        if(value.endsWith(".apk")) return null;
        if (isFile(value)) {
            String key =
                    "file|"
                            + file.getAbsolutePath()
                            + "|"
                            + file.length()
                            + "|"
                            + file.lastModified();
            Source cached = sources.get(key);
            if (cached != null) return cached;
            byte[] data = read(file);
            return data == null ? null : new Source(key, file, null, null, parse(data));
        }

        return null;
    }

    private static boolean eligible(int type, String path) {
        return type == 700 || (type == 0 && isFile(path));
    }

    private static boolean isFile(String path) {
        if (path == null || path.isEmpty()) return false;
        try {
            return new File(path).isFile();
        } catch (Throwable error) {
            return false;
        }
    }

    private static String themeFont(Context context) {
        try {
            String path =
                    Settings.System.getString(context.getContentResolver(), "theme_font_clock");
            if (path == null || path.isEmpty())
                path = Settings.System.getString(context.getContentResolver(), "theme_font");
            return path == null ? "" : path;
        } catch (Throwable error) {
            return "";
        }
    }

    private static byte[] read(File file) {
        try (InputStream input = new FileInputStream(file)) {
            return read(input);
        } catch (Throwable error) {
            return null;
        }
    }

    private static byte[] read(InputStream input) {
        try (InputStream source = input;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = source.read(buffer)) != -1) {
                if (output.size() > 64 * 1024 * 1024 - count) return null;
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (Throwable error) {
            return null;
        }
    }

    private static Axes parse(byte[] data) {
        List<FontAxes.Axis> values = FontAxes.parse(data);
        return values.isEmpty() ? null : new Axes(values);
    }
}
