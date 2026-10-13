package com.eric.himawidgetbridge;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/** Displays RemoteViews while reserving every vehicle operation for the helper's guarded flow. */
final class WidgetPreview extends FrameLayout {
    WidgetPreview(Context context, View content) {
        super(context);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(content, new FrameLayout.LayoutParams(-1, -1));
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }
    @Override public boolean onTouchEvent(MotionEvent event) { return true; }
}
