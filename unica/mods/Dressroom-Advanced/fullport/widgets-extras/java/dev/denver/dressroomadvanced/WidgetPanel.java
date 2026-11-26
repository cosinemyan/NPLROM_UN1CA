// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.lang.reflect.Proxy;

final class WidgetPanel {
    private final Context context;
    private final ClassLoader loader;
    private final EditorWidgets editor;
    private final LinearLayout content;
    private PanelTabs pages;
    private int widgetPage;
    private LinearLayout group;
    private boolean binding;
    private TextView status;
    private boolean extrasSelected;
    private int renderedScreen;
    private AdvancedPanelDismiss outsideDismiss;

    interface Choice {
        void change(Bundle b, int value) throws Exception;
    }

    WidgetPanel(Context context, ClassLoader loader, EditorWidgets editor) {
        this.context = context;
        this.loader = loader;
        this.editor = editor;
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(16));
        PanelKeyboard.attach(content);
    }

    void show(Object ui) throws Exception {
        View tabs = nativeView(EditorBinding.Row.TABS);
        NativeTabs nativeTabs = NativeTabs.of(tabs);
        nativeTabs.add(tabs, "Widgets", true);
        nativeTabs.add(tabs, "Extras", false);
        Class<?> listener = nativeTabs.listener;
        Object events =
                Proxy.newProxyInstance(
                        loader,
                        new Class<?>[] {listener},
                        (proxy, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                if (method.getName().equals("equals")) return proxy == args[0];
                                if (method.getName().equals("hashCode"))
                                    return System.identityHashCode(proxy);
                                return "Advanced panel tabs";
                            }
                            if (method.getName().equals("onTabSelected")
                                    || method.getName().equals("onTabReselected")) {
                                extrasSelected = nativeTabs.position(args[0]) == 1;
                                try {
                                    render();
                                } catch (Exception e) {
                                    error(e);
                                }
                            }
                            return null;
                        });
        nativeTabs.listen(tabs, events);
        render();
        Object nativePanel = Reflect.field(Reflect.field(ui, "a"), "c");
        Reflect.set(nativePanel, editor.legacy() ? "j" : "h", true);
        Reflect.call(nativePanel, "setAnchorStateSupported", false);
        Class<?> menuType = null;
        for (java.lang.reflect.Method m : ui.getClass().getDeclaredMethods()) {
            if (m.getParameterCount() == 10 && m.getParameterTypes()[6].isEnum()) {
                menuType = m.getParameterTypes()[6];
                break;
            }
        }
        if (menuType == null) {
            menuType =
                    Class.forName(
                            editor.legacy()
                                    ? OneUi85WidgetEditor.PANEL_MENU
                                    : OneUi9WidgetEditor.PANEL_MENU,
                            false,
                            loader);
        }
        Object type = WidgetEngine.enumValue(menuType, "WIDGET");
        Reflect.call(
                ui,
                editor.legacy() ? "j" : "d",
                content,
                tabs,
                null,
                dp(376),
                dp(376),
                null,
                type,
                null,
                false,
                null);
        Object mediator=Reflect.field(ui,"a");
        Object owner=Reflect.field(mediator,"b");
        if(owner instanceof android.app.Activity activity) {
            outsideDismiss=new AdvancedPanelDismiss(activity,content,tabs,() -> true,
                    () -> Reflect.call(mediator,"f",false));
            content.post(outsideDismiss::activate);
        }
    }

    private Bundle draft() {
        return extrasSelected
                ? editor.extras().settings(context)
                : new Bundle(editor.settings(context));
    }

    private void render() throws Exception {
        editor.cancelScalePreview();
        binding = true;
        content.removeAllViews();
        group = content;
        Bundle b = draft();
        renderedScreen = ScreenIdentity.current();
        if (extrasSelected) {
            renderExtras(b);
            binding = false;
            content.requestLayout();
            return;
        }
        View pageStrip = nativeView(EditorBinding.Row.TABS);
        content.addView(pageStrip);
        pages =
                new PanelTabs(
                        pageStrip,
                        loader,
                        new String[] {"Layout", "Position", "Effects"},
                        widgetPage,
                        page -> {
                            widgetPage = page;
                            content.requestLayout();
                        });
        section("Layout & size");
        if (editor.customGridAvailable()) {
            toggle(
                    "Custom grid",
                    b.getBoolean("layout"),
                    next -> {
                        next.putBoolean("layout", !b.getBoolean("layout"));
                        if (!next.getBoolean("layout")) {
                            next.putInt("columns", 4);
                            next.putInt("rows", 1);
                            next.putBoolean("vertical", false);
                        }
                    },
                    true);
            if (b.getBoolean("layout")) {
                if (!editor.nativeRotationAvailable()) {
                    TextView rotate =
                            label(
                                    "Arrangement: "
                                            + (editor.vertical() ? "Vertical" : "Horizontal")
                                            + "  •  Rotate");
                    PanelRows.action(
                            rotate,
                            "Rotate arrangement",
                            editor.vertical() ? "Vertical" : "Horizontal");
                    rotate.setOnClickListener(
                            v -> {
                                try {
                                    editor.rotate();
                                    content.postDelayed(
                                            () -> {
                                                try {
                                                    render();
                                                } catch (Exception e) {
                                                    error(e);
                                                }
                                            },
                                            500);
                                } catch (Exception e) {
                                    error(e);
                                }
                            });
                } else note("Rotate using Samsung's layout control.");
                slider(
                        "Columns",
                        1,
                        8,
                        b.getInt("columns"),
                        "",
                        (next, value) -> next.putInt("columns", value),
                        true);
                slider(
                        "Rows",
                        1,
                        Math.min(8, 32 / b.getInt("columns")),
                        b.getInt("rows"),
                        "",
                        (next, value) -> next.putInt("rows", value),
                        true);
                WidgetFormControls.add(context,editor,group);
                note("For a mixed layout, use 5 × 2 or larger. Add Month Calendar and detailed Weather widgets to use their full layouts.");
                note("Grid capacity: " + b.getInt("columns") + " × " + b.getInt("rows") + ".");
                note("Drag an individual widget to an empty cell. Pinch a widget to resize it in whole cells. Increase the grid to make more room.");
            }
        } else note("Custom grid is unavailable for this editor/provider combination.");
        slider(
                "Widget scale",
                50,
                200,
                Math.round(b.getFloat("scale", 1) * 100),
                "%",
                (next, value) -> next.putFloat("scale", value / 100f),
                false);
        section("Placement");
        choice(
                "Attach to clock",
                new String[] {
                    "Samsung default", "Below", "Above", "Left", "Right", "Free position"
                },
                b.getInt("anchor"),
                (next, index) -> next.putInt("anchor", index),
                false);
        int anchor = b.getInt("anchor");
        note("Drag the boundary edge or empty space to move the group. Pinch from the boundary edge to resize the group. Attached groups move along the clock edge.");
        if (anchor >= 1 && anchor <= 4) {
            slider(
                    "Gap from clock",
                    0,
                    64,
                    Math.round(b.getFloat("gap", 8)),
                    " dp",
                    (next, value) -> next.putFloat("gap", value),
                    false);
            slider(
                    "Along clock edge",
                    -600,
                    600,
                    Math.round(b.getFloat("dockOffset")),
                    " dp",
                    (next, value) -> next.putFloat("dockOffset", value),
                    false);
        }
        if (anchor == 5) {
            slider(
                    "Horizontal position",
                    0,
                    100,
                    Math.round(b.getFloat("freeX", .5f) * 100),
                    "%",
                    (next, value) -> next.putFloat("freeX", value / 100f),
                    false);
            slider(
                    "Vertical position",
                    0,
                    100,
                    Math.round(b.getFloat("freeY", .4f) * 100),
                    "%",
                    (next, value) -> next.putFloat("freeY", value / 100f),
                    false);
        }
        if (editor.depthAvailable()) {
            section("Appearance");
            boolean imageAvailable = editor.depthImageAvailable();
            choice(
                    "Subject depth",
                    new String[] {"Follow clock", "On", "Off"},
                    b.getInt("depth"),
                    (next, index) -> next.putInt("depth", index),
                    false,
                    imageAvailable);
            choice(
                    "Depth applies to",
                    new String[] {
                        "Separate per orientation",
                        "Portrait only",
                        "Landscape only",
                        "Both orientations"
                    },
                    b.getInt("depthLayouts"),
                    (next, index) -> next.putInt("depthLayouts", index),
                    false,
                    imageAvailable);
            if (!imageAvailable)
                note(
                        "Depth requires a still-image wallpaper. Gyro, video and live wallpapers"
                                + " are not supported.");

            if (!editor.legacy()) {
                toggle(
                        "Live background blur",
                        !b.getBoolean("blurOff"),
                        next -> next.putBoolean("blurOff", !b.getBoolean("blurOff")),
                        false);
            }
        }
        section("Reset");
        TextView reset = label("Reset widget adjustments");
        PanelRows.action(reset, "Reset widget adjustments", "");
        reset.setOnClickListener(
                v -> {
                    try {
                        checkScreen();
                        editor.resetAdjustments();
                        render();
                    } catch (Exception e) {
                        error(e);
                    }
                });
        group = content;
        status = label("");
        status.setVisibility(View.GONE);
        TextView credit = label("\"Dressroom-Advanced\" by BlueOutside");
        NativeCredits.bind(credit);
        credit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        credit.setAlpha(.72f);
        binding = false;
        content.requestLayout();
    }

    interface Edit {
        void change(Bundle b) throws Exception;
    }

    private void renderExtras(Bundle b) throws Exception {
        if (ScreenIdentity.value(b) != 0)
            label(ScreenIdentity.value(b) == ScreenIdentity.SUB
                    ? "Settings for the cover screen" : "Settings for the inner screen");
        PanelRows.heading(label("Now Bar"));
        toggle(
                "Keep charging card",
                b.getBoolean("keepCharging"),
                next -> next.putBoolean("keepCharging", !b.getBoolean("keepCharging")),
                false);

        toggle(
                "Full-screen album artwork",
                b.getBoolean("fullscreenArt"),
                next -> next.putBoolean("fullscreenArt", !b.getBoolean("fullscreenArt")),
                false);
        if (editor.stackAvailable()) {
            slider(
                    "Visible cards",
                    3,
                    8,
                    b.getInt("stackCards", 3),
                    "",
                    (next, value) -> next.putInt("stackCards", value),
                    false);
        }
        PanelRows.heading(label("Custom message"));
        toggle(
                "Show message",
                b.getBoolean("textCard"),
                next -> next.putBoolean("textCard", !b.getBoolean("textCard")),
                false);
        if (b.getBoolean("textCard")) {
            text("Title", "title", b.getString("title"), 80);
            text("Message", "message", b.getString("message"), 180);
        }
        if (!editor.legacy()) {
            PanelRows.heading(label("Always On Display"));
            toggle(
                    "Keep clock stretched",
                    b.getBoolean("keepStretch"),
                    next -> next.putBoolean("keepStretch", !b.getBoolean("keepStretch")),
                    false);
        }
        TextView reset = label("Reset extra settings");
        PanelRows.action(reset, "Reset extra settings", "");
        reset.setOnClickListener(
                v -> {
                    try {
                        checkScreen();
                        editor.extras().update(context, ExtrasPreferences.clean(null));
                        render();
                    } catch (Exception e) {
                        error(e);
                    }
                });
        status = label("");
        status.setVisibility(View.GONE);
        TextView credit = label("\"Dressroom-Advanced\" by BlueOutside");
        NativeCredits.bind(credit);
        credit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        credit.setAlpha(.72f);
    }

    private void text(String caption, String key, String value, int limit) {
        PanelRows.heading(label(caption));
        android.widget.EditText input = new android.widget.EditText(context);
        input.setText(value);
        input.setHint(caption);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(limit)});
        input.setContentDescription(caption);
        add(input);
        input.addTextChangedListener(
                new android.text.TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        change(b -> b.putString(key, s.toString()), false, false);
                    }

                    public void afterTextChanged(android.text.Editable e) {}
                });
    }

    private void change(Edit edit, boolean structural, boolean rebuild) {
        if (binding) return;
        try {
            checkScreen();
            Bundle next = draft();
            edit.change(next);
            if (extrasSelected) {
                editor.extras().update(context, next);
            } else editor.update(next, structural);
            if (rebuild)
                content.post(
                        () -> {
                            try {
                                render();
                            } catch (Exception e) {
                                error(e);
                            }
                        });

        } catch (Exception e) {
            error(e);
            content.post(
                    () -> {
                        try {
                            render();
                            setStatus(e.getMessage());
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    private void checkScreen() {
        if (renderedScreen != ScreenIdentity.current())
            throw new IllegalStateException("Reopen Advanced after switching screens.");
    }

    private void toggle(String name, boolean checked, Edit edit, boolean structural)
            throws Exception {
        CompoundButton toggle = (CompoundButton) nativeView(EditorBinding.Row.TOGGLE);
        toggle.setText(name);
        toggle.setChecked(checked);
        add(toggle);
        toggle.setOnCheckedChangeListener((v, value) -> change(edit, structural, true));
    }

    private void choice(
            String name, String[] entries, int selected, Choice edit, boolean structural) {
        choice(name, entries, selected, edit, structural, true);
    }

    private void choice(
            String name,
            String[] entries,
            int selected,
            Choice edit,
            boolean structural,
            boolean enabled) {
        LinearLayout parent = group;
        group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = label(name);
        PanelRows.heading(heading);
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, entries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected, false);
        spinner.setContentDescription(name);
        spinner.setMinimumHeight(dp(48));
        spinner.setEnabled(enabled);
        add(spinner);
        group = parent;
        spinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent, View view, int index, long id) {
                        if (index != selected)
                            change(next -> edit.change(next, index), structural, true);
                    }
                });
    }

    private void slider(
            String name,
            int min,
            int max,
            int current,
            String suffix,
            Choice edit,
            boolean structural)
            throws Exception {
        LinearLayout parent = group;
        group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        TextView caption = label(name + "   " + current + suffix);
        View slider = nativeView(EditorBinding.Row.SLIDER);
        Reflect.call(slider, "setMax", max - min);
        Reflect.call(slider, "setProgress", Math.max(0, Math.min(max - min, current - min)));
        slider.setContentDescription(name);
        add(slider);
        PanelRows.slider(group, caption, slider);
        group = parent;
        Class<?> listener =
                Class.forName(
                        "androidx.appcompat.widget.SeslSeekBar$OnSeekBarChangeListener",
                        false,
                        loader);
        int[] pending = {current};
        Object proxy =
                Proxy.newProxyInstance(
                        loader,
                        new Class<?>[] {listener},
                        (p, m, args) -> {
                            if (m.getDeclaringClass() == Object.class) {
                                if (m.getName().equals("equals")) return p == args[0];
                                if (m.getName().equals("hashCode"))
                                    return System.identityHashCode(p);
                                return "Widget slider";
                            }
                            if (m.getName().equals("onProgressChanged")
                                    && Boolean.TRUE.equals(args[2])) {
                                pending[0] = min + ((Number) args[1]).intValue();
                                caption.setText(name + "   " + pending[0] + suffix);
                                if (name.equals("Widget scale")) {
                                    try {
                                        editor.previewScale(pending[0] / 100f);
                                    } catch (Exception e) {
                                        error(e);
                                    }
                                }
                                if (!structural && !name.equals("Widget scale"))
                                    change(next -> edit.change(next, pending[0]), false, false);
                            }
                            if (m.getName().equals("onStopTrackingTouch")) {
                                if (name.equals("Widget scale")) editor.cancelScalePreview();
                                change(
                                        next -> edit.change(next, pending[0]),
                                        structural,
                                        structural);
                            }
                            return null;
                        });
        Reflect.call(slider, "setOnSeekBarChangeListener", proxy);
        if (name.equals("Widget scale"))
            slider.addOnAttachStateChangeListener(
                    new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View view) {}

                        @Override
                        public void onViewDetachedFromWindow(View view) {
                            editor.cancelScalePreview();
                        }
                    });
    }

    private void section(String name) {
        int page =
                switch (name) {
                    case "Placement" -> 1;
                    case "Appearance" -> 2;
                    default -> 0;
                };
        boolean separated = pages.hasGroup(page);
        group = pages.group(content, page);
        int divider =
                context.getResources()
                        .getIdentifier(
                                "clock_panel_item_divider",
                                "drawable",
                                "com.samsung.android.app.dressroom");
        if (separated && divider != 0) {
            View line = new View(context);
            android.graphics.drawable.Drawable background = context.getDrawable(divider);
            line.setBackground(background);
            group.addView(
                    line, PanelRows.divider(context, Math.max(1, background.getIntrinsicHeight())));
        }
        if (!name.equals("Reset")) {
            TextView title = label(name);
            PanelRows.heading(title);
        }
    }

    private TextView label(String value) {
        TextView label = (TextView) nativeView(EditorBinding.Row.LABEL);
        label.setText(value);
        PanelRows.text(label, false);
        add(label);
        return label;
    }

    private void note(String value) {
        TextView v = label(value);
        PanelRows.text(v, true);
    }

    private void add(View v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMarginStart(dp(16));
        p.setMarginEnd(dp(16));
        group.addView(v, p);
    }

    private View nativeView(EditorBinding.Row row) {
        String tag = editor.rowTag(row);
        int id =
                context.getResources()
                        .getIdentifier(
                                "clock_panel_layout",
                                "layout",
                                "com.samsung.android.app.dressroom");
        try (XmlResourceParser xml = context.getResources().getLayout(id)) {
            while (xml.getEventType() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.getEventType() == org.xmlpull.v1.XmlPullParser.START_TAG
                        && tag.equals(
                                xml.getAttributeValue(
                                        "http://schemas.android.com/apk/res/android", "tag"))) {
                    View v =
                            LayoutInflater.from(context)
                                    .createView(
                                            context,
                                            xml.getName(),
                                            null,
                                            android.util.Xml.asAttributeSet(xml));
                    v.setId(View.NO_ID);
                    v.setTag(null);
                    return v;
                }
                xml.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Native widget control unavailable: " + tag, e);
        }
        throw new IllegalStateException("Missing native control " + tag);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void setStatus(String message) {
        if (status == null) return;
        status.setText(message);
        status.setVisibility(message == null || message.isBlank() ? View.GONE : View.VISIBLE);
    }

    private void error(Exception e) {
        android.util.Log.e(DressroomModule.TAG, "Widget control failed", e);
        if (status != null) setStatus(e.getMessage());
        android.widget.Toast.makeText(context, e.getMessage(), android.widget.Toast.LENGTH_LONG)
                .show();
    }
}
