/*
 * Copyright (C) 2013 The ChameleonOS Open Source Project
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

package com.android.systemui.chaos.lab.gestureanywhere;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;

import android.annotation.ChaosLab;
import android.annotation.ChaosLab.Classification;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.*;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.Prediction;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.*;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.os.AsyncTask;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.animation.*;
import android.view.Gravity;
import android.widget.*;
import android.widget.FrameLayout;
import com.android.systemui.R;
import com.android.systemui.chaos.TriggerOverlayView;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.*;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_BACK;

@ChaosLab(name="GestureAnywhere", classification=Classification.NEW_CLASS)
public class GestureAnywhereView extends TriggerOverlayView implements GestureOverlayView.OnGestureListener {
    private static final String TAG = "GestureAnywhere";
    private final File mStoreFile = new File("/data/system", "ga_gestures");
    State mState = State.Collapsed;
    private View mContent;
    private GestureOverlayView mGestureView;
    private GestureLibrary mStore;
    private SettingsObserver mSettingsObserver;
    private long mGestureLoadedTime = 0;
    private boolean mTriggerVisible = false;
    private TranslateAnimation mSlideIn;
    private TranslateAnimation mSlideOut;

	// BlurOS Project 

    public static boolean mBlurredStatusBarExpandedEnabled;
    public static GestureAnywhereView mGestureAnywhereView;

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


    // Reference to the status bar
    private BaseStatusBar mBar;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GESTURE_ANYWHERE_ENABLED), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GESTURE_ANYWHERE_POSITION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GESTURE_ANYWHERE_CHANGED), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GESTURE_ANYWHERE_TRIGGER_WIDTH), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GESTURE_ANYWHERE_TRIGGER_TOP), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GESTURE_ANYWHERE_TRIGGER_HEIGHT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GESTURE_ANYWHERE_SHOW_TRIGGER), false, this);
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

            boolean enabled = Settings.System.getInt(
                    resolver, Settings.System.GESTURE_ANYWHERE_ENABLED, 0) == 1;
            setVisibility(enabled ? View.VISIBLE : View.GONE);

            int position = Settings.System.getInt(
                    resolver, Settings.System.GESTURE_ANYWHERE_POSITION, Gravity.LEFT);
            setPosition(position);

            long gestureChangedTime = Settings.System.getLong(resolver,
                    Settings.System.GESTURE_ANYWHERE_CHANGED, System.currentTimeMillis());
            if (mGestureLoadedTime < gestureChangedTime) {
                reloadGestures();
            }

            int width = Settings.System.getInt(
                    resolver, Settings.System.GESTURE_ANYWHERE_TRIGGER_WIDTH, 10);
            if (mTriggerWidth != width)
                setTriggerWidth(width);
            setTopPercentage(Settings.System.getInt(
                    resolver, Settings.System.GESTURE_ANYWHERE_TRIGGER_TOP, 0) / 100f);
            setBottomPercentage(Settings.System.getInt(
                    resolver, Settings.System.GESTURE_ANYWHERE_TRIGGER_HEIGHT, 100) / 100f);
            mTriggerVisible = Settings.System.getInt(
                    resolver, Settings.System.GESTURE_ANYWHERE_SHOW_TRIGGER, 0) == 1;
            if (mTriggerVisible)
                showTriggerRegion();
            else
                hideTriggerRegion();
        }
    }

    public GestureAnywhereView(Context context) {
        this(context, null);
    }

    public GestureAnywhereView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureAnywhereView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mStore = GestureLibraries.fromFile(mStoreFile);
    }

    public void reloadGestures() {
        if (mStore != null) {
            mStore.load();
        }
    }

    OnClickListener mCancelButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mState != State.Collapsed) {
                switchToState(State.Closing);
            }
        }
    };

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.content);
        mGestureView = (GestureOverlayView) findViewById(R.id.gesture_overlay);
        mGestureView.setGestureVisible(true);
        mGestureView.addOnGestureListener(this);
        mLayoutParams = (WindowManager.LayoutParams) getLayoutParams();
        mSettingsObserver = new SettingsObserver(new Handler());
        findViewById(R.id.cancel_gesturing).setOnClickListener(mCancelButtonListener);
        createAnimations();
        invalidate();
 		// BlurOS Project        
            mGestureAnywhereView = this;

            // inicia o BlurUtils
            mBlurUtils = new BlurUtils(mGestureAnywhereView.getContext());

            // animação
            mAlphaAnimation = new AlphaAnimation(0.0f, 1.0f);
            mAlphaAnimation.setDuration(75);
            mAlphaAnimation.setAnimationListener(mAnimationListener);

            // cria o mBlurredView
            mBlurredView = new FrameLayout(mGestureAnywhereView.getContext());
            
            mGestureAnywhereView.addView(mBlurredView, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            mGestureAnywhereView.requestLayout();

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
        if (mGestureAnywhereView == null)
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
        mBlurRadius = Settings.System.getInt(mContext.getContentResolver(), Settings.System.BLUR_RADIUS_PREFERENCE_KEY, 3);
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

            Context context = mGestureAnywhereView.getContext();

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
        mContext.registerReceiver(mBroadcastReceiver,
                new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (!isKeyguardEnabled() && mState == State.Collapsed) {
                    switchToState(State.Opening);
                    return false;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event.getKeyCode() == KEYCODE_BACK && event.getAction() == ACTION_DOWN &&
                mState != State.Collapsed) {
            switchToState(State.Closing);
            return true;
        }
        return super.dispatchKeyEventPreIme(event);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mState == State.Collapsed) {
            reposition();
            reduceToTriggerRegion();
        }
    }

    private void reposition() {
        mViewHeight = getWindowHeight();
        final ContentResolver resolver = mContext.getContentResolver();
        setTopPercentage(Settings.System.getInt(
                resolver, Settings.System.GESTURE_ANYWHERE_TRIGGER_TOP, 0) / 100f);
        setBottomPercentage(Settings.System.getInt(
                resolver, Settings.System.GESTURE_ANYWHERE_TRIGGER_HEIGHT, 100) / 100f);
    }

    public void setStatusBar(BaseStatusBar bar) {
        mBar = bar;
    }

    private void switchToState(State state) {
        switch (state) {
            case Collapsed:
                reduceToTriggerRegion();
                mGestureView.clear(false);
                if(mTriggerVisible) {
                    showTriggerRegion();
                } else {
                    hideTriggerRegion();
                }
                mContent.setVisibility(View.GONE);
                mGestureView.setVisibility(View.GONE);
                break;
            case Expanded:
                mGestureView.setVisibility(View.VISIBLE);
                break;
            case Gesturing:
                break;
            case Opening:
                expandFromTriggerRegion();
                mContent.setVisibility(View.VISIBLE);
                mContent.startAnimation(mSlideIn);
                break;
            case Closing:
                mContent.startAnimation(mSlideOut);
                break;
        }
        mState = state;
    }

    private boolean launchShortcut(String uri) {
        try {
            Intent intent = Intent.parseUri(uri, 0);
            if (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.GESTURE_ANYWHERE_FLOATING, 0) == 1) {
                intent.setFlags(Intent.FLAG_FLOATING_WINDOW
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } else {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return true;
        } catch (URISyntaxException e) {
            Log.e(TAG, "URISyntaxException: [" + uri + "]");
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "ActivityNotFound: [" + uri + "]");
        }
        return false;
    }

    private void createAnimations() {
        mSlideIn = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 1.0f, Animation.RELATIVE_TO_PARENT, 0.0f);

        mSlideOut = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 1.0f);
        mSlideIn.setDuration(200);
        mSlideIn.setInterpolator(new DecelerateInterpolator());
        mSlideIn.setFillAfter(true);
        mSlideIn.setAnimationListener(mAnimListener);
        mSlideOut.setDuration(300);
        mSlideOut.setInterpolator(new AccelerateInterpolator());
        mSlideOut.setFillAfter(true);
        mSlideOut.setAnimationListener(mAnimListener);
    }

    private Animation.AnimationListener mAnimListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            animation.cancel();
            mContent.clearAnimation();
            switch (mState) {
                case Closing:
                    switchToState(State.Collapsed);
                    break;
                case Opening:
                    switchToState(State.Expanded);
                    break;
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action) && mState != State.Collapsed) {
                switchToState(State.Closing);
            }
        }
    };

    @Override
    public void onGesture(GestureOverlayView overlay, MotionEvent event) {
    }

    @Override
    public void onGestureCancelled(GestureOverlayView overlay, MotionEvent event) {
    }

    @Override
    public void onGestureEnded(GestureOverlayView overlay, MotionEvent event) {
        Gesture gesture = overlay.getGesture();
        List<Prediction> predictions = mStore.recognize(gesture);
        for (Prediction prediction : predictions) {
            if (prediction.score >= 2.0) {
                switchToState(State.Closing);
                String uri = prediction.name.substring(prediction.name.indexOf('|') + 1);
                launchShortcut(uri);
                break;
            }
        }
    }

    @Override
    public void onGestureStarted(GestureOverlayView overlay, MotionEvent event) {
        if (mState == State.Expanded) {
            switchToState(State.Gesturing);
        }
    }

    private enum State {Collapsed, Expanded, Gesturing, Opening, Closing}
}
