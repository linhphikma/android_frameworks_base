/*
 * Copyright (C) 2013 The ChameleonOS Project
 * Copyright (C) 2015 SlimRoms Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.slimrecent;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_BACK;

import android.content.ContentResolver;
import android.content.Context;
import android.content.*;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.*;
import android.os.Handler;
import android.os.UserHandle;
import android.os.AsyncTask;
import android.provider.Settings;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.view.animation.*;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import com.android.internal.util.slim.Action;
import com.android.internal.util.slim.ActionConfig;
import com.android.internal.util.slim.ActionConstants;
import com.android.internal.util.slim.ActionHelper;
import com.android.systemui.R;
import com.android.systemui.statusbar.*;

import java.util.ArrayList;

public class AppSidebar extends FrameLayout {
    private static final String TAG = "SlimRecentAppSidebar";

    private static final LinearLayout.LayoutParams SCROLLVIEW_LAYOUT_PARAMS =
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1.0f
            );

    private static LinearLayout.LayoutParams ITEM_LAYOUT_PARAMS =
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1.0f
            );

    public static float DEFAULT_SCALE_FACTOR = 1.0f;

    private LinearLayout mAppContainer;
    private SnappingScrollView mScrollView;
    private Rect mScaledIconBounds;
    private int mIconSize;
    private int mScaledIconSize;
    private int mItemTextSize;
    private int mScaledItemTextSize;
    private int mBackgroundColor;
    private int mLabelColor;
    private boolean mHideTextLabels = false;

    private float mScaleFactor = DEFAULT_SCALE_FACTOR;

    private Context mContext;
    private SettingsObserver mSettingsObserver;

    private RecentController mSlimRecent;

    public AppSidebar(Context context) {
        this(context, null);
    }

    public AppSidebar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppSidebar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        Resources resources = context.getResources();
        mItemTextSize = resources
                .getDimensionPixelSize(R.dimen.recent_app_sidebar_item_title_text_size);
        mIconSize = resources
                .getDimensionPixelSize(R.dimen.recent_app_sidebar_item_size) - mItemTextSize;
        setScaledSizes();
    }

    private void setScaledSizes() {
        mScaledItemTextSize = Math.round(mItemTextSize * mScaleFactor);
        mScaledIconSize = Math.round(mIconSize * mScaleFactor);
        mScaledIconBounds = new Rect(0, 0, mScaledIconSize, mScaledIconSize);
    }

	// BlurOS Project 

    public static boolean mBlurredStatusBarExpandedEnabled;
    public static AppSidebar mAppSidebar;

    private static int mBlurScale;
    private static int mBlurRadius;
    private static BlurUtils mBlurUtils;
    private static FrameLayout mBlurredView;
    private static ColorFilter mColorFilter;
    private static int mBlurDarkColorFilter;
    private static int mBlurMixedColorFilter;
    private static int mBlurLightColorFilter;
    private static AlphaAnimation mAlphaAnimation;
    private static Animation.AnimationListener mAnimationListener = new Animation.AnimationListener() {

        @Override
        public void onAnimationStart(Animation anim) {

            // visível
            mBlurredView.setVisibility(View.VISIBLE);

        }

        @Override
        public void onAnimationEnd(Animation anim) {}

        @Override
        public void onAnimationRepeat(Animation anim) {}

    };

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setupAppContainer();
        mSettingsObserver = new SettingsObserver(new Handler());
 		// BlurOS Project        
            mAppSidebar = this;

            // inicia o BlurUtils
            mBlurUtils = new BlurUtils(mAppSidebar.getContext());

            // animação
            mAlphaAnimation = new AlphaAnimation(0.0f, 1.0f);
            mAlphaAnimation.setDuration(75);
            mAlphaAnimation.setAnimationListener(mAnimationListener);

            // cria o mBlurredView
            mBlurredView = new FrameLayout(mAppSidebar.getContext());

            // insere o mBlurredView no mNotificationPanelView na posição 0 (ordem importa)
            mAppSidebar.addView(mBlurredView, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            mAppSidebar.requestLayout();

            // seta o tag de: pronto para receber o blur
            mBlurredView.setTag("ready_to_blur");

            // invisível
            mBlurredView.setVisibility(View.INVISIBLE);

    }
    
    public static void startBlurTask() {
        // habilitado ?
        if (!mBlurredStatusBarExpandedEnabled)
            return;

        try {
            // não continua se o blur ja foi aplicado !!!
            if (mBlurredView.getTag().toString().equals("blur_applied"))
                return;
        } catch (Exception e){
        }

        // continua ?
        if (mAppSidebar == null)
            return;

        // callback
        BlurTask.setBlurTaskCallback(new BlurUtils.BlurTaskCallback() {

            @Override
            public void blurTaskDone(Bitmap blurredBitmap) {

                if (blurredBitmap != null) {

                    // -------------------------
                    // bitmap criado com sucesso
                    // -------------------------

                    // corrige o width do mBlurredView
                    int[] screenDimens = BlurTask.getRealScreenDimensions();
                    mBlurredView.getLayoutParams().width = screenDimens[0];
                    mBlurredView.requestLayout();

                    // cria o drawable com o filtro de cor
                    BitmapDrawable drawable = new BitmapDrawable(blurredBitmap);
                    drawable.setColorFilter(mColorFilter);

                    // seta o drawable
                    mBlurredView.setBackground(drawable);

                    // seta o tag de: blur aplicado
                    mBlurredView.setTag("blur_applied");

                } else {

                    // ----------------------------
                    // bitmap nulo por algum motivo
                    // ----------------------------

                    // seta o filtro de cor
                    mBlurredView.setBackgroundColor(mBlurLightColorFilter);

                    // seta o tag de: erro
                    mBlurredView.setTag("error");

                }

                // anima e mostra o blur
                mBlurredView.startAnimation(mAlphaAnimation);

            }

            @Override
            public void dominantColor(int color) {

                // obtém a luminosidade da cor dominante
                double lightness = DisplayUtils.getColorLightness(color);

                if (lightness >= 0.0 && color <= 1.0) {

                    // --------------------------------------------------
                    // seta o filtro de cor de acordo com a cor dominante
                    // --------------------------------------------------

                    if (lightness <= 0.33) {

                        // imagem clara (mais perto do branco)
                        mColorFilter = new PorterDuffColorFilter(mBlurLightColorFilter, PorterDuff.Mode.MULTIPLY);

                    } else if (lightness >= 0.34 && lightness <= 0.66) {

                        // imagem mista
                        mColorFilter = new PorterDuffColorFilter(mBlurMixedColorFilter, PorterDuff.Mode.MULTIPLY);

                    } else if (lightness >= 0.67 && lightness <= 1.0) {

                        // imagem clara (mais perto do preto)
                        mColorFilter = new PorterDuffColorFilter(mBlurDarkColorFilter, PorterDuff.Mode.MULTIPLY);

                    }

                } else {

                    // -------
                    // erro !!
                    // -------

                    // seta a cor mista
                    mColorFilter = new PorterDuffColorFilter(mBlurMixedColorFilter, PorterDuff.Mode.MULTIPLY);

                }
            }
        });

        // engine
        BlurTask.setBlurEngine(BlurUtils.BlurEngine.RenderScriptBlur);

        // blur
        new BlurTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }
    
    
	//BlurOS Project 

    public static void updatePreferences(Context mContext) {
        // atualiza
        mBlurScale = Settings.System.getInt(mContext.getContentResolver(), Settings.System.BLUR_SCALE_PREFERENCE_KEY, 10);
        mBlurRadius = Settings.System.getInt(mContext.getContentResolver(), Settings.System.BLUR_RADIUS_PREFERENCE_KEY, 5);
        mBlurDarkColorFilter = Color.LTGRAY;
        mBlurMixedColorFilter = Color.GRAY;
        mBlurLightColorFilter = Color.DKGRAY;
        mBlurredStatusBarExpandedEnabled = (Settings.System.getInt(mContext.getContentResolver(), Settings.System.STATUS_BAR_EXPANDED_ENABLED_PREFERENCE_KEY, 1) == 1);
    }


    public static void recycle() {

        // limpa e recicla
        if (mBlurredView != null &&
                mBlurredView.getBackground() != null) {

            // bitmap ?
            if (mBlurredView.getBackground() instanceof BitmapDrawable) {

                // recicla
                Bitmap bitmap = ((BitmapDrawable) mBlurredView.getBackground()).getBitmap();
                if (bitmap != null) {

                    bitmap.recycle();
                    bitmap = null;

                }
            }

            // limpa
            mBlurredView.setBackground(null);

        }

        // seta o tag de: pronto para receber o blur
        mBlurredView.setTag("ready_to_blur");

        // invisível
        mBlurredView.setVisibility(View.INVISIBLE);

    }

    public static class BlurTask extends AsyncTask<Void, Void, Bitmap> {

        private static int[] mScreenDimens;
        private static BlurUtils.BlurEngine mBlurEngine;
        private static BlurUtils.BlurTaskCallback mCallback;

        private Bitmap mScreenBitmap;

        public static void setBlurEngine(BlurUtils.BlurEngine blurEngine) {

            mBlurEngine = blurEngine;

        }

        public static void setBlurTaskCallback(BlurUtils.BlurTaskCallback callBack) {

            mCallback = callBack;

        }

        public static int[] getRealScreenDimensions() {

            return mScreenDimens;

        }

        @Override
        protected void onPreExecute() {

            Context context = mAppSidebar.getContext();

            // obtém o tamamho real da tela
            mScreenDimens = DisplayUtils.getRealScreenDimensions(context);

            // obtém a screenshot da tela com escala reduzida
            mScreenBitmap = DisplayUtils.takeSurfaceScreenshot(context, mBlurScale);

        }

        @Override
        protected Bitmap doInBackground(Void... arg0) {

            try {

                // continua ?
                if (mScreenBitmap == null)
                    return null;

                // calback
                mCallback.dominantColor(DisplayUtils.getDominantColorByPixelsSampling(mScreenBitmap, 20, 20));

                // blur engine
            //    if (mBlurEngine == BlurUtils.BlurEngine.RenderScriptBlur) {
//
   //                 mScreenBitmap = mBlurUtils.renderScriptBlur(mScreenBitmap, mBlurRadius);
//
  //              } else if (mBlurEngine == BlurUtils.BlurEngine.StackBlur) {

    //                mScreenBitmap = mBlurUtils.stackBlur(mScreenBitmap, mBlurRadius);
//
  //              } else if (mBlurEngine == BlurUtils.BlurEngine.FastBlur) {
//
  //                  mBlurUtils.fastBlur(mScreenBitmap, mBlurRadius);
//
  //              }
                mScreenBitmap = mBlurUtils.renderScriptBlur(mScreenBitmap, mBlurRadius);
                return mScreenBitmap;

            } catch (OutOfMemoryError e) {

                // erro
                return null;

            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {

            if (bitmap != null) {

                // -----------------------------
                // bitmap criado com sucesso !!!
                // -----------------------------

                // callback
                mCallback.blurTaskDone(bitmap);

            } else {

                // --------------------------
                // erro ao criar o bitmap !!!
                // --------------------------

                // callback
                mCallback.blurTaskDone(null);

            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
    }

    public void setSlimRecent(RecentController slimRecent){
        mSlimRecent = slimRecent;
    }

    private void setupAppContainer() {
        post(new Runnable() {
            public void run() {
                setupSidebarContent();
            }
        });
    }


    private int getBackgroundColor(){
        return mBackgroundColor == 0x00ffffff ?
                mContext.getResources().getColor(R.color.recent_background) : mBackgroundColor;
    }
    private int getLabelColor(){
        return mLabelColor == 0x00ffffff ?
                mContext.getResources().getColor(R.color.recents_task_bar_light_text_color) :
                mLabelColor;
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event.getKeyCode() == KEYCODE_BACK && event.getAction() == ACTION_DOWN &&
                mSlimRecent != null)
            mSlimRecent.onExit();
        return super.dispatchKeyEventPreIme(event);
    }

    private OnClickListener mItemClickedListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            Action.processAction(mContext, ((ActionConfig)view.getTag()).getClickAction(), false);
            hideSlimRecent();
        }
    };

    private OnLongClickListener mItemLongClickedListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            String action = ((ActionConfig)view.getTag()).getLongpressAction();
            if (!ActionConstants.ACTION_NULL.equals(action)) {
                Action.processAction(mContext, action, false);
                hideSlimRecent();
                return true;
            }
            return false;
        }
    };

    class SnappingScrollView extends ScrollView {

        private boolean mSnapTrigger = false;

        public SnappingScrollView(Context context) {
            super(context);
        }

        Runnable mSnapRunnable = new Runnable(){
            @Override
            public void run() {
                int mSelectedItem = ((getScrollY() + (ITEM_LAYOUT_PARAMS.height / 2)) /
                        ITEM_LAYOUT_PARAMS.height);
                int scrollTo = mSelectedItem * ITEM_LAYOUT_PARAMS.height;
                smoothScrollTo(0, scrollTo);
                mSnapTrigger = false;
            }
        };

        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            if (Math.abs(oldt - t) <= 1 && mSnapTrigger) {
                removeCallbacks(mSnapRunnable);
                postDelayed(mSnapRunnable, 100);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int action = ev.getAction();
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                mSnapTrigger = true;
            } else if (action == MotionEvent.ACTION_DOWN) {
                mSnapTrigger = false;
            }
            return super.onTouchEvent(ev);
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_APP_SIDEBAR_DISABLE_LABELS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_APP_SIDEBAR_BG_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_APP_SIDEBAR_TEXT_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_APP_SIDEBAR_SCALE_FACTOR), false, this);
            update();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();

            boolean requireNewSetup = false;

            boolean hideLabels = Settings.System.getIntForUser(
                    resolver, Settings.System.RECENT_APP_SIDEBAR_DISABLE_LABELS, 0,
                    UserHandle.USER_CURRENT) == 1;

            int labelColor = Settings.System.getIntForUser(resolver,
                    Settings.System.RECENT_APP_SIDEBAR_TEXT_COLOR, 0x00ffffff,
                    UserHandle.USER_CURRENT);

            if (hideLabels != mHideTextLabels || labelColor != mLabelColor) {
                mHideTextLabels = hideLabels;
                mLabelColor = labelColor;
                if (mScrollView != null) {
                    requireNewSetup = true;
                }
            }

            int backgroundColor = Settings.System.getIntForUser(resolver,
                    Settings.System.RECENT_APP_SIDEBAR_BG_COLOR, 0x00ffffff,
                    UserHandle.USER_CURRENT);

            if (mBackgroundColor != backgroundColor) {
                mBackgroundColor = backgroundColor;
                setBackgroundColor(getBackgroundColor());
            }

            float scaleFactor = Settings.System.getIntForUser(
                    resolver, Settings.System.RECENT_APP_SIDEBAR_SCALE_FACTOR, 100,
                    UserHandle.USER_CURRENT) / 100.0f;
            if (scaleFactor != mScaleFactor) {
                mScaleFactor = scaleFactor;
                setScaledSizes();
                requireNewSetup = true;
            }
            if (requireNewSetup) {
                setupAppContainer();
            }
        }
    }

    private void setupSidebarContent(){
        // Load content
        ArrayList<ActionConfig> contentConfig = ActionHelper.getRecentAppSidebarConfig(mContext);
        ArrayList<View> mContainerItems = new ArrayList<View>();

        for (ActionConfig config: contentConfig){
            mContainerItems.add(createAppItem(config));
        }

        // Layout items
        Rect r = new Rect();
        getWindowVisibleDisplayFrame(r);
        int windowHeight = r.bottom - r.top;;
        int statusBarHeight = r.top;
        if (mScrollView != null)
            removeView(mScrollView);

        // create a LinearLayout to hold our items
        if (mAppContainer == null) {
            mAppContainer = new LinearLayout(mContext);
            mAppContainer.setOrientation(LinearLayout.VERTICAL);
            mAppContainer.setGravity(Gravity.CENTER);
        }
        mAppContainer.removeAllViews();

        // set the layout height based on the item height we would like and the
        // number of items that would fit at on screen at once given the height
        // of the app sidebar
        int padding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.recent_app_sidebar_item_padding);
        int desiredItemSize = mScaledIconSize + padding * 2;
        if (!mHideTextLabels) {
            // add size twice to make sure that the text won't get cut
            // (e.g. "y" being displayed as "v")
            desiredItemSize += mScaledItemTextSize * 2;
        }
        int numItems = (int)Math.floor(windowHeight / desiredItemSize);
        ITEM_LAYOUT_PARAMS.height = windowHeight / numItems;
        ITEM_LAYOUT_PARAMS.width = desiredItemSize;
        LinearLayout.LayoutParams firstItemLayoutParams = new LinearLayout.LayoutParams(
                ITEM_LAYOUT_PARAMS.width, ITEM_LAYOUT_PARAMS.height);
        firstItemLayoutParams.topMargin += statusBarHeight;

        boolean firstIcon = true;
        for (View icon : mContainerItems) {
            icon.setOnClickListener(mItemClickedListener);
            icon.setOnLongClickListener(mItemLongClickedListener);
            if (mHideTextLabels) {
                ((TextView)icon).setTextSize(0);
            }
            icon.setClickable(true);
            icon.setPadding(0, padding, 0, padding);
            if (firstIcon) {
                // First icon should not hide behind the status bar
                mAppContainer.addView(icon, firstItemLayoutParams);
                firstIcon = false;
            } else {
                mAppContainer.addView(icon, ITEM_LAYOUT_PARAMS);
            }
        }

        // we need our horizontal scroll view to wrap the linear layout
        if (mScrollView == null) {
            mScrollView = new SnappingScrollView(mContext);
            // make the fading edge the size of a button (makes it more noticible that we can scroll
            mScrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            mScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        mScrollView.removeAllViews();
        mScrollView.addView(mAppContainer, SCROLLVIEW_LAYOUT_PARAMS);
        addView(mScrollView, SCROLLVIEW_LAYOUT_PARAMS);
        mAppContainer.setFocusable(true);
    }

    private TextView createAppItem(ActionConfig config) {
        TextView tv = new TextView(mContext);
        Drawable icon = ActionHelper.getActionIconImage(mContext, config.getClickAction(),
                config.getIcon());
        if (icon != null) {
            icon.setBounds(mScaledIconBounds);
            tv.setCompoundDrawables(null, icon, null, null);
        }
        tv.setTag(config);
        tv.setText(config.getClickActionDescription());
        tv.setSingleLine(true);
        tv.setEllipsize(TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mScaledItemTextSize);
        tv.setTextColor(getLabelColor());

        return tv;
    }

    private void hideSlimRecent(){
        if (mSlimRecent != null)
            mSlimRecent.hideRecents(false);
    }
}
