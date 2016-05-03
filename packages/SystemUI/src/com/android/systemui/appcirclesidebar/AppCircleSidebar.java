package com.android.systemui.statusbar.appcirclesidebar;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_BACK;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.*;
import android.net.Uri;
import android.os.IBinder;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.*;
import android.view.animation.*;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.*;
import com.android.systemui.statusbar.*;

import com.android.systemui.chaos.TriggerOverlayView;
import com.android.systemui.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AppCircleSidebar extends TriggerOverlayView implements PackageAdapter.OnCircleItemClickListener,
                            CircleListView.OnItemCenteredListener {
    private static final String TAG = "AppCircleSidebar";
    private static final boolean DEBUG_LAYOUT = false;
    private static final long AUTO_HIDE_DELAY = 3000;

    private static final String ACTION_HIDE_APP_CONTAINER
            = "com.android.internal.policy.statusbar.HIDE_APP_CONTAINER";

    private static enum SIDEBAR_STATE { OPENING, OPENED, CLOSING, CLOSED };
    private SIDEBAR_STATE mState = SIDEBAR_STATE.CLOSED;

    private static final String DRAG_LABEL_SHORTCUT = "Dragging shortcut";

    private int mTriggerWidth;
    private CircleListView mCircleListView;
    private PackageAdapter mPackageAdapter;
    private Context mContext;
    private boolean mFirstTouch = false;
    private boolean mFloatingWindow = false;
    private SettingsObserver mSettingsObserver;
    private List<FloatingTaskInfo> mAppRunning;

    private PopupMenu mPopup;
    private PopupMenu mPopupMax;
    private WindowManager mWM;
    private AlarmManager mAM;

    public AppCircleSidebar(Context context) {
        this(context, null);
    }

    public AppCircleSidebar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppCircleSidebar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mTriggerWidth = context.getResources().getDimensionPixelSize(R.dimen.app_sidebar_trigger_width);
        mContext = context;
        mWM = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mAM = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
    }

	// BlurOS Project 

    public static boolean mBlurredStatusBarExpandedEnabled;
    public static AppCircleSidebar mAppCircleSidebarView;

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

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_ACTIVITY_LAUNCH_DETECTOR);
        filter.addAction(Intent.ACTION_ACTIVITY_END_DETECTOR);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(ACTION_HIDE_APP_CONTAINER);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mAppChangeReceiver, filter);

        mCircleListView = (CircleListView) findViewById(R.id.circle_list);
        mPackageAdapter = new PackageAdapter(mContext);
        mPackageAdapter.setOnCircleItemClickListener(this);

        mCircleListView.setAdapter(mPackageAdapter);
        mCircleListView.setViewModifier(new CircularViewModifier());
        mCircleListView.setOnItemCenteredListener(this);
        mCircleListView.setVisibility(View.GONE);
        createAnimatimations();
        mSettingsObserver = new SettingsObserver(new Handler());
 		// BlurOS Project        
            mAppCircleSidebarView = this;

            // inicia o BlurUtils
            mBlurUtils = new BlurUtils(mAppCircleSidebarView.getContext());

            // animação
            mAlphaAnimation = new AlphaAnimation(0.0f, 1.0f);
            mAlphaAnimation.setDuration(75);
            mAlphaAnimation.setAnimationListener(mAnimationListener);

            // cria o mBlurredView
            mBlurredView = new FrameLayout(mAppCircleSidebarView.getContext());

            // insere o mBlurredView no mNotificationPanelView na posição 0 (ordem importa)
            mAppCircleSidebarView.addView(mBlurredView, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            mAppCircleSidebarView.requestLayout();

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
        if (mAppCircleSidebarView == null)
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

        mAppRunning = new ArrayList<FloatingTaskInfo>();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSettingsObserver != null) {
            mSettingsObserver.observe();
        }
        if (mPackageAdapter != null) {
            mPackageAdapter.reloadApplications();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mSettingsObserver != null) {
            mSettingsObserver.unobserve();
        }
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

            Context context = mAppCircleSidebarView.getContext();

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

    private class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ENABLE_APP_CIRCLE_BAR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.WHITELIST_APP_CIRCLE_BAR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_CIRCLE_BAR_TRIGGER_WIDTH), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_CIRCLE_BAR_TRIGGER_TOP), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_CIRCLE_BAR_TRIGGER_HEIGHT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_CIRCLE_BAR_SHOW_TRIGGER), false, this);
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
            final ContentResolver resolver = mContext.getContentResolver();
            setAppBarVisibility(Settings.System.getIntForUser(
                    resolver, Settings.System.ENABLE_APP_CIRCLE_BAR, 0,
                    UserHandle.USER_CURRENT_OR_SELF) == 1);
            String includedApps = Settings.System.getStringForUser(resolver,
                    Settings.System.WHITELIST_APP_CIRCLE_BAR,
                    UserHandle.USER_CURRENT_OR_SELF);
            if (mPackageAdapter != null) {
                mPackageAdapter.createIncludedAppsSet(includedApps);
                mPackageAdapter.reloadApplications();
            }

            int width = Settings.System.getInt(
                    resolver, Settings.System.APP_CIRCLE_BAR_TRIGGER_WIDTH, 40);
            if (mTriggerWidth != width)
                setTriggerWidth(width);
            setTopPercentage(Settings.System.getInt(
                    resolver, Settings.System.APP_CIRCLE_BAR_TRIGGER_TOP, 0) / 100f);
            setBottomPercentage(Settings.System.getInt(
                    resolver, Settings.System.APP_CIRCLE_BAR_TRIGGER_HEIGHT, 100) / 100f);
            if (Settings.System.getInt(
                    resolver, Settings.System.APP_CIRCLE_BAR_SHOW_TRIGGER, 0) == 1)
                showTriggerRegion();
            else
                hideTriggerRegion();
        }
    }

    private void setAppBarVisibility(boolean enabled) {
        setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
                if (mState == SIDEBAR_STATE.OPENED)
                    showAppContainer(false);
                break;
            case MotionEvent.ACTION_DOWN:
                if (isKeyguardEnabled())
                    return false;
                if (ev.getX() <= mTriggerWidth && mState == SIDEBAR_STATE.CLOSED) {
                    showAppContainer(true);
                    cancelAutoHideTimer();
                    mCircleListView.onTouchEvent(ev);
                    mFirstTouch = true;
                } else
                    updateAutoHideTimer(AUTO_HIDE_DELAY);
                break;
            case MotionEvent.ACTION_MOVE:
                cancelAutoHideTimer();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                updateAutoHideTimer(AUTO_HIDE_DELAY);
                if (mState != SIDEBAR_STATE.CLOSED)
                    mState = SIDEBAR_STATE.OPENED;
                if (mFirstTouch) {
                    mFirstTouch = false;
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
                if (mState == SIDEBAR_STATE.OPENED)
                    showAppContainer(false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                updateAutoHideTimer(AUTO_HIDE_DELAY);
                break;
            case MotionEvent.ACTION_MOVE:
            default:
                cancelAutoHideTimer();
        }
        return mCircleListView.onTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event.getKeyCode() == KEYCODE_BACK && event.getAction() == ACTION_DOWN &&
                mState == SIDEBAR_STATE.OPENED)
            showAppContainer(false);
        return super.dispatchKeyEventPreIme(event);
    }

    private void expandFromRegion() {
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) getLayoutParams();
        params.y = 0;
        params.height = getWindowHeight();
        params.width = LayoutParams.WRAP_CONTENT;
        params.flags = enableKeyEvents();
        mWM.updateViewLayout(this, params);
    }

    private TranslateAnimation mSlideOut = new TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 1.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);

    private TranslateAnimation mSlideIn = new TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 1.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);

    private void createAnimatimations() {
        mSlideIn.setDuration(300);
        mSlideIn.setInterpolator(new DecelerateInterpolator());
        mSlideIn.setFillAfter(true);
        mSlideIn.setAnimationListener(mAnimListener);
        mSlideOut.setDuration(300);
        mSlideOut.setInterpolator(new DecelerateInterpolator());
        mSlideOut.setFillAfter(true);
        mSlideOut.setAnimationListener(mAnimListener);
    }

    private void showAppContainer(boolean show) {
        mState = show ? SIDEBAR_STATE.OPENING : SIDEBAR_STATE.CLOSING;
        if (show) {
            mCircleListView.setVisibility(View.VISIBLE);
            expandFromRegion();
        } else {
            if (mPopup != null) {
                mPopup.dismiss();
            }
            if (mPopupMax != null) {
                mPopupMax.dismiss();
            }
            cancelAutoHideTimer();
        }
        mCircleListView.startAnimation(show ? mSlideIn : mSlideOut);
    }

    private Animation.AnimationListener mAnimListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            animation.cancel();
            mCircleListView.clearAnimation();
            switch (mState) {
                case CLOSING:
                    mState = SIDEBAR_STATE.CLOSED;
                    mCircleListView.setVisibility(View.GONE);
                    reduceToTriggerRegion();
                    break;
                case OPENING:
                    mState = SIDEBAR_STATE.OPENED;
                    mCircleListView.setVisibility(View.VISIBLE);
                    break;
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    };

    private void updateAutoHideTimer(long delay) {
        Intent i = new Intent(ACTION_HIDE_APP_CONTAINER);

        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            mAM.cancel(pi);
        } catch (Exception e) {
        }
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(System.currentTimeMillis() + delay);
        mAM.set(AlarmManager.RTC, time.getTimeInMillis(), pi);
    }

    private void cancelAutoHideTimer() {
        Intent i = new Intent(ACTION_HIDE_APP_CONTAINER);

        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            mAM.cancel(pi);
        } catch (Exception e) {
        }
    }

    private final BroadcastReceiver mAppChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_PACKAGE_ADDED)
                    || action.equals(Intent.ACTION_PACKAGE_REMOVED)
                    || action.equals(Intent.ACTION_PACKAGE_CHANGED)) {
                if (mPackageAdapter != null) {
                    mPackageAdapter.reloadApplications();
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_HIDE_APP_CONTAINER)) {
                showAppContainer(false);
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                showAppContainer(false);
            } else if (action.equals(Intent.ACTION_ACTIVITY_LAUNCH_DETECTOR)) {
                String packageName = intent.getStringExtra("packagename");
                IBinder packageToken = (IBinder) intent.getExtra("packagetoken");
                if (packageName == null) {
                    return;
                }
                if (!getAppFloatingInfo(packageName)) {
                    FloatingTaskInfo taskInfo = new FloatingTaskInfo();
                    taskInfo.packageName = packageName;
                    taskInfo.packageToken = packageToken;
                    mAppRunning.add(taskInfo);
                }
            } else if (action.equals(Intent.ACTION_ACTIVITY_END_DETECTOR)) {
                String packageName = intent.getStringExtra("packagename");
                if (packageName == null) {
                    return;
                }
                if (getAppFloatingInfo(packageName)) {
                    FloatingTaskInfo taskInfo = getFloatingInfo(packageName);
                    if (taskInfo != null) {
                        mAppRunning.remove(taskInfo);
                    }
                }
            }
        }
    };

    private void launchApplication(String packageName, String className) {
        updateAutoHideTimer(500);
        ComponentName cn = new ComponentName(packageName, className);
        Intent intent = Intent.makeMainActivity(cn);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (mFloatingWindow) {
            intent.addFlags(Intent.FLAG_FLOATING_WINDOW);
            mFloatingWindow = false;
        } else {
            intent.setFlags(intent.getFlags() & ~Intent.FLAG_FLOATING_WINDOW);
        }
        mContext.startActivity(intent);
    }

    private void launchApplicationFromHistory(String packageName) {
        if (getAppFloatingInfo(packageName)) {
            updateAutoHideTimer(500);
            FloatingTaskInfo taskInfo = getFloatingInfo(packageName);
            if (taskInfo != null) {
                updateMaximizeApp(taskInfo.packageToken);
            }
        } else {
            updateAutoHideTimer(AUTO_HIDE_DELAY);
        }
    }

    private void killApp(String packageName) {
       final ActivityManager am = (ActivityManager) mContext
                .getSystemService(Activity.ACTIVITY_SERVICE);
       am.forceStopPackage(packageName);
    }

    @Override
    public void onItemCentered(View v) {
        updateAutoHideTimer(AUTO_HIDE_DELAY);
        if (v != null) {
            final int position = (Integer) v.getTag(R.id.key_position);
            final ResolveInfo info = (ResolveInfo) mPackageAdapter.getItem(position);
            if (info != null) {
                final String packageName = info.activityInfo.packageName;
                if (!getAppFloatingInfo(packageName)) {
                    updateAutoHideTimer(AUTO_HIDE_DELAY);
                    return;
                }
                final PopupMenu popup = new PopupMenu(mContext, v);
                mPopupMax = popup;
                popup.getMenuInflater().inflate(R.menu.sidebar_maximize_popup_menu,
                      popup.getMenu());
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.sidebar_maximize_item) {
                            mFloatingWindow = true;
                            launchApplicationFromHistory(packageName);
                        } else if (item.getItemId() == R.id.sidebar_maximize_stop_item) {
                            FloatingTaskInfo taskInfo = getFloatingInfo(packageName);
                            if (taskInfo != null) {
                                mAppRunning.remove(taskInfo);
                            }
                            killApp(packageName);
                        } else {
                            return false;
                        }
                        return true;
                    }
                });
                popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
                    public void onDismiss(PopupMenu menu) {
                        mPopupMax = null;
                    }
                });
                popup.show();
            }
        }
    }

    @Override
    public boolean onItemTouchCenteredEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mFirstTouch = false;
            if (mState != SIDEBAR_STATE.OPENED)
                return false;
        } else if (action == MotionEvent.ACTION_DOWN) {
            cancelAutoHideTimer();
        }
        return true;
    }

    @Override
    public void onClick(final View v, final BaseAdapter adapter) {

        final int position = (Integer) v.getTag(R.id.key_position);
        final ResolveInfo info = (ResolveInfo) adapter.getItem(position);

        if (v.equals(mCircleListView.findViewAtCenter())) {
            if (info != null) {
                launchApplication(info.activityInfo.packageName, info.activityInfo.name);
            }
        } else {
            mCircleListView.smoothScrollToView(v);
        }
    }

    @Override
    public void onLongClick(final View v, final BaseAdapter adapter) {

        final int position = (Integer) v.getTag(R.id.key_position);
        final ResolveInfo info = (ResolveInfo) adapter.getItem(position);
        if (info != null) {
            final String packageName = info.activityInfo.packageName;
            final PopupMenu popup = new PopupMenu(mContext, v);
            mPopup = popup;
            popup.getMenuInflater().inflate(R.menu.sidebar_popup_menu,
                   popup.getMenu());
            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    if (item.getItemId() == R.id.sidebar_float_item) {
                        mFloatingWindow = true;
                        launchApplication(packageName, info.activityInfo.name);
                    } else if (item.getItemId() == R.id.sidebar_inspect_item) {
                        startApplicationDetailsActivity(packageName);
                    } else if (item.getItemId() == R.id.sidebar_stop_item) {
                        FloatingTaskInfo taskInfo = getFloatingInfo(packageName);
                        if (taskInfo != null) {
                            mAppRunning.remove(taskInfo);
                        }
                        killApp(packageName);
                    } else {
                        return false;
                    }
                    return true;
                }
            });
            popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
                public void onDismiss(PopupMenu menu) {
                    mPopup = null;
                }
            });
            popup.show();
        }
    }

    private void startApplicationDetailsActivity(String packageName) {
        updateAutoHideTimer(500);
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts(
                        "package", packageName, null));
        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
        TaskStackBuilder.create(mContext)
                .addNextIntentWithParentStack(intent).startActivities();
        showAppContainer(false);
    }

    private void updateMaximizeApp(IBinder token) {
        IWindowManager wm = (IWindowManager) WindowManagerGlobal.getWindowManagerService();
        try {
             wm.notifyFloatActivityTouched(token, false);
        } catch (RemoteException e) {
        }
    }

    private FloatingTaskInfo getFloatingInfo(String packageName) {
        for (FloatingTaskInfo taskInfo : mAppRunning) {
            if (packageName.equals(taskInfo.packageName)) {
                return taskInfo;
            }
        }
        return null;
    }

    private boolean getAppFloatingInfo(String packageName) {
        for (FloatingTaskInfo taskInfo : mAppRunning) {
            if (packageName.equals(taskInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    public class FloatingTaskInfo {
        public IBinder packageToken;
        public String packageName;
    }
}
