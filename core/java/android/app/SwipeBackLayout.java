
package android.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.internal.R;

import android.app.ViewDragHelper;

public class SwipeBackLayout extends FrameLayout {
    /**
     * Minimum velocity that will be detected as a fling
     */
    private static final int MIN_FLING_VELOCITY = 400; // dips per second

    private static final int DEFAULT_SCRIM_COLOR = 0x99000000;

    private static final int FULL_ALPHA = 255;

    /**
     * Edge flag indicating that the left edge should be affected.
     */
    public static final int EDGE_LEFT = ViewDragHelper.EDGE_LEFT;

    /**
     * Edge flag indicating that the right edge should be affected.
     */
    public static final int EDGE_RIGHT = ViewDragHelper.EDGE_RIGHT;

    /**
     * Edge flag indicating that the bottom edge should be affected.
     */
    public static final int EDGE_BOTTOM = ViewDragHelper.EDGE_BOTTOM;

    /**
     * Edge flag set indicating all edges should be affected.
     */
    public static final int EDGE_ALL = EDGE_LEFT | EDGE_RIGHT | EDGE_BOTTOM;

    /**
     * A view is not currently being dragged or animating as a result of a
     * fling/snap.
     */
    public static final int STATE_IDLE = ViewDragHelper.STATE_IDLE;

    /**
     * A view is currently being dragged. The position is currently changing as
     * a result of user input or simulated user input.
     */
    public static final int STATE_DRAGGING = ViewDragHelper.STATE_DRAGGING;

    /**
     * A view is currently settling into place as a result of a fling or
     * predefined non-interactive motion.
     */
    public static final int STATE_SETTLING = ViewDragHelper.STATE_SETTLING;

    /**
     * Default threshold of scroll
     */
    private static final float DEFAULT_SCROLL_THRESHOLD = 0.3f;

    private int mEdgeFlag;

    /**
     * Threshold of scroll, we will close the activity, when scrollPercent over
     * this value;
     */
    private float mScrollThreshold = DEFAULT_SCROLL_THRESHOLD;

    private Activity mActivity;

    private boolean mEnable = true;

    private View mContentView;

    private ViewDragHelper mDragHelper;

    private float mScrollPercent;

    private int mContentLeft;

    private int mContentTop;

    private SwipeListener mSwipeListener;

    private Drawable mShadowLeft;

    private Drawable mShadowRight;

    private Drawable mShadowBottom;

    private float mScrimOpacity;

    private int mScrimColor = DEFAULT_SCRIM_COLOR;

    private boolean mInLayout;

    private Rect mTmpRect = new Rect();

    private Region mTmpRegion = new Region();

    /**
     * Edge being dragged
     */
    private int mDraggingEdge;


    public SwipeBackLayout(Context context) {
        this(context, null);
    }

    public SwipeBackLayout(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.SwipeBackLayoutStyle);
    }

    public SwipeBackLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        final float density = getResources().getDisplayMetrics().density;
        final float minVel = MIN_FLING_VELOCITY * density;

        mDragHelper = ViewDragHelper.create(this, new ViewDragCallback());
        mDragHelper.setMinVelocity(minVel);
        setEdgeTrackingEnabled(EDGE_LEFT);

        setShadow(R.drawable.shadow_left, EDGE_LEFT);
        setShadow(R.drawable.shadow_right, EDGE_RIGHT);
        setShadow(R.drawable.shadow_bottom, EDGE_BOTTOM);
    }

    /**
     * Set up contentView which will be moved by user gesture
     * 
     * @param view
     */
    private void setContentView(View view) {
        mContentView = view;
    }

    public void setEnableGesture(boolean enable) {
        mEnable = enable;
    }

    public void setEdgeTrackingEnabled(int edgeFlags) {
        mEdgeFlag = edgeFlags;
        mDragHelper.setEdgeTrackingEnabled(mEdgeFlag);
    }

    public void setScrimColor(int color) {
        mScrimColor = color;
        invalidate();
    }

    public void setEdgeSize(int size) {
        mDragHelper.setEdgeSize(size);
    }

    public void setSwipeListener(SwipeListener listener) {
        mSwipeListener = listener;
    }

    public static interface SwipeListener {

        public void onScrollStateChange(int state, float scrollPercent);

        public void onEdgeTouch(int edgeFlag);

        public void onScrollOverThreshold();
    }

    public void setScrollThresHold(float threshold) {
        if (threshold >= 1.0f || threshold <= 0) {
            throw new IllegalArgumentException("Threshold value should be between 0 and 1.0");
        }
        mScrollThreshold = threshold;
    }

    public void setShadow(Drawable shadow, int edgeFlag) {
        if ((edgeFlag & EDGE_LEFT) != 0) {
            mShadowLeft = shadow;
        } else if ((edgeFlag & EDGE_RIGHT) != 0) {
            mShadowRight = shadow;
        } else if ((edgeFlag & EDGE_BOTTOM) != 0) {
            mShadowBottom = shadow;
        }
        invalidate();
    }

    public void setShadow(int resId, int edgeFlag) {
        setShadow(getResources().getDrawable(resId), edgeFlag);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!mEnable) {
            return false;
        }
        try{
            return mDragHelper.shouldInterceptTouchEvent(event);
        }catch(ArrayIndexOutOfBoundsException e){
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mEnable) {
            return false;
        }
        mDragHelper.processTouchEvent(event);
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mInLayout = true;
        mContentView.layout(mContentLeft, mContentTop,
                mContentLeft + mContentView.getMeasuredWidth(),
                mContentTop + mContentView.getMeasuredHeight());
        mInLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!mInLayout) {
            super.requestLayout();
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final boolean drawContent = child == mContentView;
        drawShadow(canvas, child);

        boolean ret = super.drawChild(canvas, child, drawingTime);
        if (mScrimOpacity > 0 && drawContent
                && mDragHelper.getViewDragState() != ViewDragHelper.STATE_IDLE) {
            drawScrim(canvas, child);
        }

        return ret;
    }

    private void drawScrim(Canvas canvas, View child) {
        final int baseAlpha = (mScrimColor & 0xff000000) >>> 24;
        final int alpha = (int) (baseAlpha * mScrimOpacity);
        final int color = alpha << 24 | (mScrimColor & 0xffffff);
        if ((mDraggingEdge & EDGE_LEFT) != 0) {
            canvas.clipRect(0, 0, child.getLeft(), getHeight());
        } else if ((mDraggingEdge & EDGE_RIGHT) != 0) {
            canvas.clipRect(child.getRight(), 0, getRight(), getHeight());
        } else if ((mDraggingEdge & EDGE_BOTTOM) != 0) {
            canvas.clipRect(child.getLeft(), child.getBottom(), getRight(), getHeight());
        }
        canvas.drawColor(color);
    }

    private void drawShadow(Canvas canvas, View child) {
        final Rect childRect = mTmpRect;
        child.getHitRect(childRect);

        if ((mEdgeFlag & EDGE_LEFT) != 0) {
            mShadowLeft.setBounds(childRect.left - mShadowLeft.getIntrinsicWidth(), childRect.top,
                    childRect.left, childRect.bottom);
            mShadowLeft.setAlpha((int) (mScrimOpacity * FULL_ALPHA));
            mShadowLeft.draw(canvas);
        }

        if ((mEdgeFlag & EDGE_RIGHT) != 0) {
            mShadowRight.setBounds(childRect.right, childRect.top,
                    childRect.right + mShadowLeft.getIntrinsicWidth(), childRect.bottom);
					mShadowRight.setAlpha((int) (mScrimOpacity * FULL_ALPHA));
            mShadowRight.draw(canvas);

        }

        if ((mEdgeFlag & EDGE_BOTTOM) != 0) {
            mShadowBottom.setBounds(childRect.left, childRect.bottom, childRect.right,
                    childRect.bottom + mShadowBottom.getIntrinsicHeight());
            mShadowBottom.draw(canvas);
        }
    }

    public void attachToActivity(Activity activity) {
        mActivity = activity;
        TypedArray a = activity.getTheme().obtainStyledAttributes(new int[] {
            android.R.attr.windowBackground
        });
        int background = a.getResourceId(0, 0);
        a.recycle();

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        ViewGroup decorChild = (ViewGroup) decor.getChildAt(0);
        decorChild.setBackgroundResource(background);
        decor.removeView(decorChild);
        addView(decorChild);
        setContentView(decorChild);
        decor.addView(this);
    }

    @Override
    public void computeScroll() {
        mScrimOpacity = 1 - mScrollPercent;
        if (mDragHelper.continueSettling(true)) {
            SwipeBackLayout.this.postInvalidateOnAnimation();
        }
    }

    private class ViewDragCallback extends ViewDragHelper.Callback {
        private boolean mIsScrollOverValid;

        @Override
        public boolean tryCaptureView(View view, int i) {
            boolean ret = mDragHelper.isEdgeTouched(mEdgeFlag, i);
            if (ret) {
                if (mDragHelper.isEdgeTouched(EDGE_LEFT, i)) {
                    mDraggingEdge = EDGE_LEFT;
                } else if (mDragHelper.isEdgeTouched(EDGE_RIGHT, i)) {
                    mDraggingEdge = EDGE_RIGHT;
                } else if (mDragHelper.isEdgeTouched(EDGE_BOTTOM, i)) {
                    mDraggingEdge = EDGE_BOTTOM;
                }
                if (mSwipeListener != null) {
                    mSwipeListener.onEdgeTouch(mDraggingEdge);
                }
                mIsScrollOverValid = true;
            }
            return ret;
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return 1;
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return 1;
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            if ((mDraggingEdge & (EDGE_LEFT | EDGE_RIGHT)) != 0) {
                mScrollPercent = Math.abs((float) left
                        / (mContentView.getWidth() + mShadowRight.getIntrinsicWidth()));
            } else if ((mDraggingEdge & EDGE_BOTTOM) != 0) {
                mScrollPercent = Math.abs((float) top
                        / (mContentView.getHeight() + mShadowBottom.getIntrinsicHeight()));
            }
            mContentLeft = left;
            mContentTop = top;
            invalidate();
            if (mScrollPercent < mScrollThreshold && !mIsScrollOverValid) {
                mIsScrollOverValid = true;
            }
            if (mSwipeListener != null && mDragHelper.getViewDragState() == STATE_DRAGGING
                    && mScrollPercent >= mScrollThreshold && mIsScrollOverValid) {
                mIsScrollOverValid = false;
                mSwipeListener.onScrollOverThreshold();
            }
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            final int childWidth = releasedChild.getWidth();
            final int childHeight = releasedChild.getHeight();

            int left = 0, top = 0;
            if ((mDraggingEdge & EDGE_LEFT) != 0) {
                left = xvel > 0 || xvel == 0 && mScrollPercent > mScrollThreshold ? childWidth
                        + mShadowLeft.getIntrinsicWidth() : 0;
            } else if ((mDraggingEdge & EDGE_RIGHT) != 0) {
                left = xvel < 0 || xvel == 0 && mScrollPercent > mScrollThreshold ? -(childWidth + mShadowLeft
                        .getIntrinsicWidth()) : 0;
            } else if ((mDraggingEdge & EDGE_BOTTOM) != 0) {
                top = yvel < 0 || yvel == 0 && mScrollPercent > mScrollThreshold ? -(childHeight + mShadowBottom
                        .getIntrinsicHeight()) : 0;
            }

            mDragHelper.settleCapturedViewAt(left, top);
            invalidate();
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            int ret = 0;
            if ((mDraggingEdge & EDGE_LEFT) != 0) {
                ret = Math.min(child.getWidth(), Math.max(left, 0));
            } else if ((mDraggingEdge & EDGE_RIGHT) != 0) {
                ret = Math.min(0, Math.max(left, -child.getWidth()));
            }
            return ret;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            int ret = 0;
            if ((mDraggingEdge & EDGE_BOTTOM) != 0) {
                ret = Math.min(0, Math.max(top, -child.getHeight()));
            }
            return ret;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);
            if (mSwipeListener != null) {
                mSwipeListener.onScrollStateChange(state, mScrollPercent);
            }
            if (state == ViewDragHelper.STATE_IDLE) {
                SwipeBackLayout.this.setLayerType(View.LAYER_TYPE_NONE, null);
                if (mScrollPercent == 1) {
                    mActivity.finish();
                }
            } else if (state == ViewDragHelper.STATE_SETTLING) {
                SwipeBackLayout.this.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
        }
    }
}
