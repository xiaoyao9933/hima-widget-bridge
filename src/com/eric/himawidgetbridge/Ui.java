package com.eric.himawidgetbridge;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Switch;

/** Shared native UI styling. Original widget content remains untouched. */
final class Ui {
    final Activity activity;
    final boolean dark;
    final int background, surface, text, muted, border, soft, blue, hero;
    @SuppressWarnings("deprecation") // Retain system bar styling on Android 8.0 and later.
    Ui(Activity activity) {
        this.activity = activity;
        dark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        background = color(dark ? "#10151B" : "#F5F4EF");
        surface = color(dark ? "#1B222B" : "#FFFFFF");
        text = color(dark ? "#F4F5F7" : "#202832");
        muted = color(dark ? "#A6B0BE" : "#727C88");
        border = color(dark ? "#2D3744" : "#E6E8EB");
        soft = color(dark ? "#263140" : "#F0F3F7");
        blue = color(dark ? "#93B9FF" : "#325FCC");
        hero = color("#171D26");
        activity.getWindow().setStatusBarColor(background);
        activity.getWindow().setNavigationBarColor(background);
        activity.getWindow().getDecorView().setSystemUiVisibility(dark ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    static int color(String value) { return Color.parseColor(value); }
    int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill); shape.setCornerRadius(dp(radius));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }
    TextView label(String value, int size, int color, boolean medium) {
        TextView result = new TextView(activity);
        result.setText(value); result.setTextSize(size); result.setTextColor(color);
        result.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        result.setIncludeFontPadding(false); result.setLineSpacing(dp(3), 1);
        return result;
    }
    LinearLayout column(Context context) {
        LinearLayout result = new LinearLayout(context);
        result.setOrientation(LinearLayout.VERTICAL); return result;
    }
    LinearLayout card() {
        LinearLayout result = column(activity);
        result.setPadding(dp(16), dp(18), dp(16), dp(18));
        result.setBackground(round(surface, 22, border));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(16); result.setLayoutParams(params); return result;
    }
    Button button(String text, boolean primary, Runnable action) {
        Button result = new Button(activity);
        result.setText(text); result.setTextSize(15); result.setAllCaps(false);
        result.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        result.setTextColor(primary ? (dark ? Ui.color("#172333") : Color.WHITE) : this.text);
        int fill = primary ? (dark ? blue : hero) : soft;
        result.setBackground(new RippleDrawable(ColorStateList.valueOf(
                primary ? color("#306F9BC8") : color("#18727C88")), round(fill, 14, 0), null));
        result.setPadding(dp(12), 0, dp(12), 0);
        result.setMinWidth(0); result.setMinimumWidth(0);
        result.setMinHeight(0); result.setMinimumHeight(0); result.setStateListAnimator(null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.topMargin = dp(10); result.setLayoutParams(params);
        result.setOnClickListener(v -> action.run()); return result;
    }
    Switch switchControl(String description) {
        Switch result = new Switch(activity); result.setContentDescription(description);
        result.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        result.setTrackTintList(new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {blue, dark ? color("#4A5666") : color("#C8D0DA")}));
        return result;
    }
    void space(LinearLayout parent, int height) {
        View gap = new View(activity); parent.addView(gap, new LinearLayout.LayoutParams(1, dp(height)));
    }
}
