package android.app;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.annotation.ColorRes;
import android.annotation.NonNull;
import android.annotation.StyleRes;
import android.app.DialogFragment;
//import android.content.ContextCompat;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.android.internal.util.bluros.Util;
import com.android.internal.util.bluros.Blur;

/**
 * BlurDialogFragmentHelper.java
 *
 * @author Linh Phi.
 */
public class BlurDialogFragmentHelper {

    private final DialogFragment mFragment;

    private ViewGroup mRoot;

    private ViewGroup mBlurContainer;

    private View mBlurBgView;

    private ImageView mBlurImgView;

    private int mAnimDuration;

    //private int mWindowAnimStyle;

    private int mBgColorResId;

    public BlurDialogFragmentHelper(@NonNull DialogFragment fragment) {
        mFragment = fragment;
        mAnimDuration = fragment.getActivity().getResources().getInteger(android.R.integer.config_mediumAnimTime);
        //mWindowAnimStyle = fragment.getActivity().getResources().getStyle(android.R.style.DialogSlideAnimation);
        //mBgColorResId = android.R.color.bg_glass;
    }

    /**
     * Duration of the alpha animation.
     *
     * @param animDuration The length of ensuing property animations, in milliseconds.
     *                     The value cannot be negative.
     */
    public void setAnimDuration(int animDuration) {
        mAnimDuration = animDuration;
    }

 //   public void setWindowAnimStyle(@StyleRes int windowAnimStyle) {
 //       mWindowAnimStyle = windowAnimStyle;
  //  }

    public void setBgColorResId(@ColorRes int bgColorResId) {
        mBgColorResId = bgColorResId;
    }

    public void onCreate() {
        mFragment.setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar);
    }

    public void onActivityCreated() {
        Window window = mFragment.getDialog().getWindow();
        //window.setWindowAnimations(mWindowAnimStyle);

        Rect visibleFrame = new Rect();
        mRoot = (ViewGroup) mFragment.getActivity().getWindow().getDecorView();
        mRoot.getWindowVisibleDisplayFrame(visibleFrame);

        mBlurContainer = new FrameLayout(mFragment.getActivity());
        if (Util.isPostHoneycomb()) {
            // window has a navigation bar
            mBlurContainer = new FrameLayout(mFragment.getActivity());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(visibleFrame.right - visibleFrame.left,
                    visibleFrame.bottom - visibleFrame.top);
            params.setMargins(visibleFrame.left, visibleFrame.top, 0, 0);
            mBlurContainer.setLayoutParams(params);
        } else {
            mBlurContainer.setPadding(visibleFrame.left, visibleFrame.top, 0, 0);
        }

        mBlurBgView = new View(mFragment.getActivity());
        //mBlurBgView.setBackgroundColor(ContextCompat.getColor(mFragment.getContext(), mBgColorResId));
        Util.setAlpha(mBlurBgView, 0f);

        mBlurImgView = new ImageView(mFragment.getActivity());
        Util.setAlpha(mBlurImgView, 0f);

        mBlurContainer.addView(mBlurImgView);
        mBlurContainer.addView(mBlurBgView);

        mRoot.addView(mBlurContainer);

        // apply blur effect
        Bitmap bitmap = Util.drawViewToBitmap(mRoot, visibleFrame.right,
                visibleFrame.bottom, visibleFrame.left, visibleFrame.top, 3);
        Bitmap blurred = Blur.apply(mFragment.getActivity(), bitmap);
        mBlurImgView.setImageBitmap(blurred);
        bitmap.recycle();

        View view = mFragment.getView();
        if (view != null) {
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mFragment.dismiss();
                    return true;
                }
            });
        }
    }

    public void onStart() {
        startEnterAnimation();
    }

    public void onDismiss() {
        startExitAnimation();
    }

    private void startEnterAnimation() {
        Util.animateAlpha(mBlurBgView, 0f, 1f, mAnimDuration, null);
        Util.animateAlpha(mBlurImgView, 0f, 1f, mAnimDuration, null);
    }

    private void startExitAnimation() {
        Util.animateAlpha(mBlurBgView, 1f, 0f, mAnimDuration, null);
        Util.animateAlpha(mBlurImgView, 1f, 0f, mAnimDuration, new Runnable() {
            @Override
            public void run() {
                mRoot.removeView(mBlurContainer);
            }
        });
    }
}
