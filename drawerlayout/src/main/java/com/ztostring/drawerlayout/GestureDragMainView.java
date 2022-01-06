package com.ztostring.drawerlayout;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static java.lang.Math.abs;
import static java.util.Objects.requireNonNull;

/**
 * DrawerLayout主页面View
 *
 * @author zoutong <ztostring@163.com> on 1/5/22.
 */
public class GestureDragMainView extends FrameLayout {

    private DrawerLayout drawerLayout;
    private final Context context;
    private float initialMotionX;
    private float initialMotionY;
    private float lastMotionX;
    private final int touchSlop;
    private final int swipeSlop;
    private final int distanceThreshold;
    private final int xVelocityThreshold;
    private int gravity = Gravity.LEFT;
    private boolean isDraggingDrawer = false;
    private boolean shouldOpenDrawer = false;
    private VelocityTracker velocityTracker = null;
    private float xMoveVelocity; //水平方向速度

    public GestureDragMainView(@NonNull Context context) {
        this(context, null);
    }

    public GestureDragMainView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureDragMainView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        swipeSlop = dipsToPixels(8);
        distanceThreshold = dipsToPixels(80);
        xVelocityThreshold = 50;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        drawerLayout = (DrawerLayout) getParent();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        View drawerView = findDrawerWithGravity(Gravity.LEFT) != null ? findDrawerWithGravity(Gravity.LEFT) : findDrawerWithGravity(Gravity.RIGHT);
        return handleInterceptTouchEvent(event)
                || drawerLayout.isDrawerOpen(Gravity.LEFT)
                || drawerLayout.isDrawerOpen(Gravity.RIGHT)
                || drawerView != null && drawerView.getVisibility() == VISIBLE; // drawerView完全展示时居然有概率出现isDrawerOpen判断为false，这你敢信！双重保险一波！
    }

    private boolean handleInterceptTouchEvent(MotionEvent event) {
        boolean intercepted = false;
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            lastMotionX = initialMotionX = x;
            initialMotionY = y;
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            } else {
                velocityTracker.clear();
            }
            velocityTracker.addMovement(event);
            return false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (canNestedViewScroll(this, false, (int) (x - lastMotionX), (int) x, (int) y)) {
                return false;
            }
            lastMotionX = x;
            float diffX = x - initialMotionX;
            intercepted = abs(diffX) > touchSlop
                    && abs(diffX) > abs(y - initialMotionY)
                    && isDrawerEnabled(diffX);
        }
        return intercepted;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return handleTouchEvent(event);
    }

    private boolean handleTouchEvent(MotionEvent event) {
        float x = event.getRawX();
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                float diffX = x - initialMotionX;
                if (!isDrawerEnabled(diffX)) {
                    return false;
                }
                float absDiffX = abs(diffX);
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(event);
                boolean lastDraggingDrawer = isDraggingDrawer;
                isDraggingDrawer = true;
                // 1. 位移距离判断打开状态
                shouldOpenDrawer = absDiffX > distanceThreshold;
                /* 计算水平移动速度 */
                velocityTracker.computeCurrentVelocity(100);
                float xVelocity = velocityTracker.getXVelocity();
                Log.d("zt- xVelocityMove: ", String.valueOf(xVelocity));
                // 只取第一次触发move时的值作为当前移动过程的速度值
                xMoveVelocity = xMoveVelocity == 0 ? xVelocity : xMoveVelocity;
                // fix 当drawerView处于打开状态，继续在Container范围内反方向滑动，会导致drawerView有轻微抖动~~~
                if (xMoveVelocity > 0 && drawerLayout.isDrawerOpen(Gravity.LEFT) || xMoveVelocity < 0 && drawerLayout.isDrawerOpen(Gravity.RIGHT)) {
                    isDraggingDrawer = false;
                    return false;
                }
                offsetDrawer(gravity, absDiffX - swipeSlop);
                if (!lastDraggingDrawer) {
                    onDrawerDragging();
                }
                return isDraggingDrawer;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if (isDraggingDrawer) {
                    Log.d("zt- xMoveVelocity:", String.valueOf(xMoveVelocity));
                    Log.d("zt- shouldOpenDrawer:", String.valueOf(shouldOpenDrawer));
                    boolean fromLeft = (gravity == Gravity.LEFT);
                    // 2. 速度判断打开状态
                    if (xMoveVelocity > xVelocityThreshold) {
                        shouldOpenDrawer = fromLeft;
                    } else if (xMoveVelocity < -xVelocityThreshold) {
                        shouldOpenDrawer = !fromLeft;
                    }
                    // 3. 速度+位移距离判断打开状态（如速度接近，且位移距离接近，亦可满足打开条件）
                    if (abs(x - initialMotionX) / (distanceThreshold * 1f) > 0.5f && xMoveVelocity / (xVelocityThreshold * 1f) > 0.5f) {
                        shouldOpenDrawer = true;
                    }
                    if (shouldOpenDrawer) {
                        drawerLayout.openDrawer(gravity, true);
                    } else {
                        drawerLayout.closeDrawer(gravity, true);
                    }
                    xMoveVelocity = 0;
                } else { // 由于外部drawerLayout不在进行事件拦截，所以需要我们手动处理点击空白区域收起drawerView的操作
                    drawerLayout.closeDrawer(gravity, true);
                }
                shouldOpenDrawer = false;
                isDraggingDrawer = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            }
        }
        return true;
    }

    /**
     * 核心算法：
     * 遍历当前View树，计算是否存在嵌套的滑动view，如：ScrollView、RecycleView、ViewPager等
     *
     * @param view      root view
     * @param checkSelf 是否检查view本身是否具备水平滑动功能
     * @param dx        位移距离
     * @param x         触点位置x坐标
     * @param y         触点位置y坐标
     * @return
     */
    private boolean canNestedViewScroll(View view, boolean checkSelf, int dx, int x, int y) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int scrollX = view.getScrollX();
            int scrollY = view.getScrollY();
            int count = group.getChildCount();
            for (int i = count - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                if (x + scrollX >= child.getLeft()
                        && x + scrollX < child.getRight()
                        && y + scrollY >= child.getTop()
                        && y + scrollY < child.getBottom()
                        && canNestedViewScroll(child, true, dx, x + scrollX - child.getLeft(), y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }
        return checkSelf && view.canScrollHorizontally(-dx);
    }

    private boolean isDrawerEnabled(float direction) {
        return direction > 0 && hasEnabledDrawer(Gravity.LEFT)
                || direction < 0 && hasEnabledDrawer(Gravity.RIGHT);
    }

    private int dipsToPixels(int dips) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dips * scale + 0.5f);
    }

    public void onDrawerDragging() {
        List<DrawerLayout.DrawerListener> drawerListeners = getDrawerListeners();
        if (drawerListeners != null) {
            int listenerCount = drawerListeners.size();
            for (int i = listenerCount - 1; i >= 0; --i) {
                drawerListeners.get(i).onDrawerStateChanged(DrawerLayout.STATE_DRAGGING);
            }
        }
    }

    protected List<DrawerLayout.DrawerListener> getDrawerListeners() {
        try {
            Field field = DrawerLayout.class.getDeclaredField("mListeners");
            field.setAccessible(true);
            return (List<DrawerLayout.DrawerListener>) field.get(drawerLayout);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasEnabledDrawer(int gravity) {
        return drawerLayout.getDrawerLockMode(gravity) == DrawerLayout.LOCK_MODE_UNLOCKED
                && findDrawerWithGravity(gravity) != null;
    }

    private void offsetDrawer(int gravity, float offset) {
        View drawerView = findDrawerWithGravity(gravity);
        float slideOffsetPercent = MathUtils.clamp(offset / requireNonNull(drawerView).getWidth(), 0f, 1f);
        try {
            Method method = DrawerLayout.class.getDeclaredMethod("moveDrawerToOffset", View.class, float.class);
            method.setAccessible(true);
            method.invoke(drawerLayout, drawerView, slideOffsetPercent);
            drawerView.setVisibility(VISIBLE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        drawerLayout.invalidate();
    }

    private View findDrawerWithGravity(int gravity) {
        final int absHorizontalGravity = GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(drawerLayout)) & Gravity.HORIZONTAL_GRAVITY_MASK;
        final int childCount = drawerLayout.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = drawerLayout.getChildAt(i);
            final int childAbsGravity = getDrawerViewAbsoluteGravity(child);
            if ((childAbsGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == absHorizontalGravity) {
                return child;
            }
        }
        return null;
    }

    private int getDrawerViewAbsoluteGravity(View drawerView) {
        final int gravity = ((DrawerLayout.LayoutParams) drawerView.getLayoutParams()).gravity;
        return GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(drawerLayout));
    }
}
