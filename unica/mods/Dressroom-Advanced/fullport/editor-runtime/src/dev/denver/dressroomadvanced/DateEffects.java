// Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class DateEffects {
    private static final Map<View, Boolean> previews =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, int[]> identities =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> originalVisibility =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> weatherMargins =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> tracked = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Method> hooked = Collections.newSetFromMap(new WeakHashMap<>());

    private DateEffects() {}

    static void resetPreviews() {
        previews.clear();
        identities.clear();
    }

    static synchronized void attach(DressroomModule module, View clock) {
        if (tracked.add(clock)) clock.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateDate(clock));
        apply(clock);
    }
    static void nativeChanged(View clock) { updateDate(clock); clock.post(()->updateDate(clock)); }


    static void preview(View clock, DressroomModule module, boolean hide) {
        previews.put(clock, hide);
        try {
            apply(clock);
        } catch (Exception e) {
            module.report("Date preview unavailable", e);
        }
    }

    static void bindIdentity(View clock, int type, int font, int orientation) {
        identities.put(clock, new int[] {type, font, orientation});
        apply(clock);
    }

    static void clearPreview(View clock) {
        previews.remove(clock);
        apply(clock);
    }

    static void apply(View clock) {
        updateDate(clock);
        clock.post(() -> updateDate(clock));
    }

    private static Boolean shouldHide(View clock) {
        Boolean preview = previews.get(clock);
        if (preview != null) return preview;
        try {
            Bundle query = new Bundle();
            int[] identity = identities.get(clock);
            query.putInt(
                    "type",
                    identity == null
                            ? ((Number) Reflect.call(clock, "getClockType")).intValue()
                            : identity[0]);
            query.putInt(
                    "font", identity == null ? ClockBinding.of(clock).fontId(clock) : identity[1]);
            if (identity != null) query.putInt(LayoutOrientation.FIELD, identity[2]);
            Bundle saved = SettingsBridge.read(clock.getContext(), query);
            return saved != null && saved.getBoolean("hideDate");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void updateDate(View clock) {
        boolean hide = Boolean.TRUE.equals(shouldHide(clock));
        updateText(find(clock, "common_date"), hide);
        updateText(find(clock, "common_local_date"), hide);
        View weather = find(clock, "common_weather_container");
        if (weather != null) {
            ViewGroup.LayoutParams params = weather.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                if (hide) {
                    weatherMargins.putIfAbsent(weather, margins.getMarginStart());
                    if (margins.getMarginStart() != 0) {
                        margins.setMarginStart(0);
                        weather.setLayoutParams(margins);
                    }
                } else {
                    Integer original = weatherMargins.remove(weather);
                    if (original != null && margins.getMarginStart() != original) {
                        margins.setMarginStart(original);
                        weather.setLayoutParams(margins);
                    }
                }
            }
        }
        View dateRow = find(clock, "common_date_container");
        if (dateRow != null && weather != null) {
            boolean empty = hide && weather.getVisibility() != View.VISIBLE;
            if (empty) {
                if (!originalVisibility.containsKey(dateRow))
                    originalVisibility.put(dateRow, dateRow.getVisibility());
                if (dateRow.getVisibility() != View.GONE) dateRow.setVisibility(View.GONE);
            } else {
                Integer original = originalVisibility.remove(dateRow);
                if (original != null && dateRow.getVisibility() == View.GONE)
                    dateRow.setVisibility(original);
            }
        }
    }

    private static void updateText(View date, boolean hide) {
        if (date == null) return;
        if (hide) {
            if (!originalVisibility.containsKey(date))
                originalVisibility.put(date, date.getVisibility());
            if (date.getVisibility() != View.GONE) date.setVisibility(View.GONE);
        } else {
            Integer original = originalVisibility.remove(date);
            if (original != null && date.getVisibility() == View.GONE) date.setVisibility(original);
        }
    }

    private static View find(View root, String entry) {
        int id = root.getId();
        if (id != View.NO_ID) {
            try {
                if (entry.equals(root.getResources().getResourceEntryName(id))) return root;
            } catch (android.content.res.Resources.NotFoundException ignored) {
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), entry);
                if (found != null) return found;
            }
        }
        return null;
    }
}
