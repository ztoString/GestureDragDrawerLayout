package com.ztostring.drawerlayout;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;

/**
 * 自定义DrawerLayout，针对屏手势拖动，优化事件拦截时机。
 *
 * @author zoutong <ztostring@163.com> on 1/6/22.
 */
public class GestureDragDrawerLayout extends DrawerLayout {

    public GestureDragDrawerLayout(@NonNull Context context) {
        super(context);
    }

    public GestureDragDrawerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public GestureDragDrawerLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        float x = ev.getRawX();
        boolean parentIntercept = super.onInterceptTouchEvent(ev);
        View drawerView = findDrawerWithGravity(Gravity.LEFT) != null ? findDrawerWithGravity(Gravity.LEFT) : findDrawerWithGravity(Gravity.RIGHT);
        return drawerView == null || (isTouchInsideDrawerView(x, drawerView) && parentIntercept);
    }

    /**
     * 只有手势点击位于已展示的drawerView区域内，才进行拦截
     * 点击其他位置及其他时机时均不进行事件拦截，由下层MainView自行处理
     */
    private boolean isTouchInsideDrawerView(float x, View drawerView) {
        return (isDrawerOpen(Gravity.LEFT) || isDrawerOpen(Gravity.RIGHT) || drawerView.getVisibility() == VISIBLE) && x <= drawerView.getWidth();
    }

    @Nullable
    private View findDrawerWithGravity(int gravity) {
        final int absHorizontalGravity = GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this)) & Gravity.HORIZONTAL_GRAVITY_MASK;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final int childAbsGravity = getDrawerViewAbsoluteGravity(child);
            if ((childAbsGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == absHorizontalGravity) {
                return child;
            }
        }
        return null;
    }

    private int getDrawerViewAbsoluteGravity(View drawerView) {
        final int gravity = ((DrawerLayout.LayoutParams) drawerView.getLayoutParams()).gravity;
        return GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this));
    }
}
