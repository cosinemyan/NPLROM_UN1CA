package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

/** Shared spacing and text hierarchy for rows inside Samsung's editor panels. */
final class PanelRows {
    private PanelRows() {}

    static int color(Context context, int attribute) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) return Color.GRAY;
        return value.resourceId == 0 ? value.data : context.getColor(value.resourceId);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static void text(TextView view, boolean secondary) {
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, secondary ? 12 : 16);
        view.setTextColor(
                color(
                        view.getContext(),
                        secondary
                                ? android.R.attr.textColorSecondary
                                : android.R.attr.textColorPrimary));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPaddingRelative(0, dp(view.getContext(), 8), 0, dp(view.getContext(), 8));
        ViewGroup.LayoutParams layout = view.getLayoutParams();
        if (layout != null) {
            layout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            view.setLayoutParams(layout);
        }
    }

    static void heading(TextView view) {
        text(view, true);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        view.setPaddingRelative(0, dp(view.getContext(), 16), 0, dp(view.getContext(), 8));
    }

    static void slider(
            android.widget.LinearLayout group, TextView caption, android.view.View slider) {
        Context context = group.getContext();
        group.setPaddingRelative(0, dp(context, 12), 0, dp(context, 12));
        caption.setMinHeight(0);
        caption.setMinimumHeight(0);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        caption.setPaddingRelative(0, 0, 0, 0);
        caption.setTextColor(color(context, android.R.attr.textColorSecondary));
        slider.setMinimumHeight(0);
        int inset = dp(context, 16);
        try {
            Object drawable = Reflect.call(slider, "getThumb");
            if (drawable instanceof android.graphics.drawable.Drawable thumb)
                inset =
                        Math.max(
                                inset,
                                Math.max(thumb.getIntrinsicWidth(), thumb.getMinimumWidth()) / 2
                                        + dp(context, 4));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The default inset also leaves room for the native pressed-state ring.
        }
        slider.setPadding(
                Math.max(slider.getPaddingLeft(), inset),
                0,
                Math.max(slider.getPaddingRight(), inset),
                0);
        group.setClipChildren(false);
        group.setClipToPadding(false);
        slider.setClipToOutline(false);
        android.widget.LinearLayout.LayoutParams layout =
                new android.widget.LinearLayout.LayoutParams(slider.getLayoutParams());
        if (slider.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams margins) {
            layout.setMarginStart(margins.getMarginStart());
            layout.setMarginEnd(margins.getMarginEnd());
        }
        layout.height = dp(context, 40);
        layout.topMargin = -dp(context, 4);
        layout.bottomMargin = 0;
        slider.setLayoutParams(layout);
    }

    static android.widget.LinearLayout.LayoutParams divider(Context context, int height) {
        android.widget.LinearLayout.LayoutParams layout =
                new android.widget.LinearLayout.LayoutParams(-1, height);
        layout.topMargin = dp(context, 12);
        layout.bottomMargin = dp(context, 12);
        return layout;
    }

    static void action(TextView view, String title, String summary) {
        text(view, false);
        Context context = view.getContext();
        view.setMinHeight(dp(context, 56));
        view.setPaddingRelative(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        view.setClickable(true);
        view.setFocusable(true);
        view.setAccessibilityDelegate(
                new android.view.View.AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(
                            android.view.View host,
                            android.view.accessibility.AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setClassName(android.widget.Button.class.getName());
                    }
                });
        int foreground = color(context, android.R.attr.textColorPrimary);
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(context, 16));
        fill.setColor((foreground & 0x00ffffff) | 0x10000000);
        fill.setStroke(dp(context, 1), (foreground & 0x00ffffff) | 0x18000000);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(context, 16));
        mask.setColor(Color.WHITE);
        view.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf((foreground & 0x00ffffff) | 0x20000000),
                        fill,
                        mask));
        actionText(view, title, summary);
    }

    static void actionText(TextView view, String title, String summary) {
        if (summary == null || summary.isBlank()) {
            view.setText(title);
            return;
        }
        SpannableString text = new SpannableString(title + "\n" + summary);
        int start = title.length() + 1;
        text.setSpan(
                new RelativeSizeSpan(.75f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
                new ForegroundColorSpan(
                        color(view.getContext(), android.R.attr.textColorSecondary)),
                start,
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(text);
    }
}
