package dev.denver.dressroomadvanced;

import android.view.View;
import android.widget.LinearLayout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

final class PanelTabs {
    private final Map<View, Integer> groups = new LinkedHashMap<>();
    private int selected;
    private final int pageCount;

    PanelTabs(View strip, ClassLoader loader, String[] names, int initial, IntConsumer changed)
            throws ReflectiveOperationException {
        pageCount = names.length;
        selected = Math.max(0, Math.min(names.length - 1, initial));
        NativeTabs tabs = NativeTabs.of(strip);
        tabs.subTabs(strip);
        Reflect.call(strip, "setTabMode", 1);
        for (int i = 0; i < names.length; i++) {
            tabs.add(strip, names[i], i == selected);
        }
        tabs.listen(
                strip,
                NativeClockPanel.proxy(
                        tabs.listener,
                        (method, args) -> {
                            if (method.getName().equals("onTabSelected")) {
                                selected = tabs.position(args[0]);
                                update();
                                changed.accept(selected);
                            }
                            return null;
                        }));
    }

    void clear() {
        groups.clear();
    }

    boolean hasGroup(int page) {
        return groups.containsValue(page);
    }

    LinearLayout group(LinearLayout parent, int page) {
        if (page < 0 || page >= pageCount)
            throw new IllegalArgumentException("Panel page has no tab: " + page);
        LinearLayout group = new LinearLayout(parent.getContext());
        group.setOrientation(LinearLayout.VERTICAL);
        groups.put(group, page);
        group.setVisibility(page == selected ? View.VISIBLE : View.GONE);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        return group;
    }

    private void update() {
        for (Map.Entry<View, Integer> group : groups.entrySet())
            group.getKey().setVisibility(group.getValue() == selected ? View.VISIBLE : View.GONE);
    }
}
