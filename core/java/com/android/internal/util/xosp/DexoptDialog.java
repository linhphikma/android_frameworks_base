/*
 * Copyright (C) 2016 AllianceROM, ~Morningstar
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

package com.android.internal.util.xosp;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemProperties;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.internal.R;
import com.android.internal.util.AllianceUtils;

/**
 * @hide
 */
public class DexoptDialog extends Dialog {

    private final Context mContext;
    private final PackageManager mPackageManager;

    private ImageView mAppIcon;
    private TextView mPrimaryText;
    private TextView mPackageName;
    private ProgressBar mProgress;

    private boolean mWasApk;

    private int mTotal;

    private ImageView mLogo;

    public static DexoptDialog create(Context context) {
        return create(context,  WindowManager.LayoutParams.TYPE_BOOT_PROGRESS);
    }

    public static DexoptDialog create(Context context, int windowType) {
        final PackageManager pm = context.getPackageManager();
        final int theme = com.android.internal.R.style.Theme_Material_Light;
        return new DexoptDialog(context, theme, windowType);
    }

    private DexoptDialog(Context context, int themeResId, int windowType) {
        super(context, themeResId);
        mContext = context;
        mPackageManager = context.getPackageManager();

        final LayoutInflater inflater = LayoutInflater.from(context);
        final View rootView = inflater.inflate(com.android.internal.R.layout.dexopt_layout, null, false);
        mPrimaryText = (TextView) rootView.findViewById(R.id.dexopt_message);
        mPackageName = (TextView) rootView.findViewById(R.id.dexopt_message_detail);
        mAppIcon = (ImageView) rootView.findViewById(R.id.dexopt_icon);
        mProgress = (ProgressBar) rootView.findViewById(R.id.dexopt_progress);
        mLogo = (ImageView) rootView.findViewById(R.id.dexopt_logo);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(rootView);

        if (windowType != 0) {
            getWindow().setType(windowType);
        }
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        final WindowManager.LayoutParams lp = getWindow().getAttributes();
        // turn off button lights when dexopting
        lp.buttonBrightness = 0;
        lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
        getWindow().setAttributes(lp);
        setCancelable(false);
        show();

        rootView.post(new Runnable() {
            @Override public void run() {
                mAppIcon.setImageDrawable(null);

                // start the marquee
                mPrimaryText.setSelected(true);
                mPackageName.setSelected(true);
            }
        });
    }

    public void setProgress(final ApplicationInfo info, final int current, final int total) {
        boolean isApk = false;
        String msg = "";

        // if we initialized with an invalid total, get it from the valid dexopt messages
        if (mTotal != total && total > 0) {
            mTotal = total;
            mProgress.setMax(mTotal);
        }

        if (info == null) {
            if (current == Integer.MIN_VALUE) {
                msg = mContext.getResources().getString(com.android.internal.R.string.android_upgrading_starting_apps);
            } else if (current == (Integer.MIN_VALUE + 1)) {
                msg = mContext.getResources().getString(com.android.internal.R.string.android_upgrading_fstrim);
            } else if (current == (Integer.MIN_VALUE + 3)) {
                msg = mContext.getResources().getString(com.android.internal.R.string.android_upgrading_complete);
            }
        } else if (current == (Integer.MIN_VALUE + 2)) {
            final CharSequence label = info.loadLabel(mContext.getPackageManager());
            msg = mContext.getResources().getString(com.android.internal.R.string.android_preparing_apk, label);
        } else {
            isApk = true;
            msg = mContext.getResources().getString(com.android.internal.R.string.android_upgrading_apk, current, total);
            mProgress.setProgress(current);
            if ((current + 1) <= total) {
                mProgress.setSecondaryProgress(current + 1);
            }
        }

        // check if the state has changed
        if (mWasApk != isApk) {
            mWasApk = isApk;
            if (isApk) {
                mPackageName.setVisibility(View.VISIBLE);
                mProgress.setVisibility(View.VISIBLE);
            } else {
                mPackageName.setVisibility(View.GONE);
                mProgress.setVisibility(View.INVISIBLE);
            }
        }

        // if we are processing an apk, load its icon and set the message details
        if (isApk) {
            mAppIcon.setImageDrawable(info.loadIcon(mPackageManager));
            mPackageName.setText(String.format("(%s)", info.packageName));
        } else {
            mAppIcon.setImageDrawable(null);
        }
        mPrimaryText.setText(msg);
    }

    // This dialog will consume all events coming in to
    // it, to avoid it trying to do things too early in boot.

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return true;
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return true;
    }
}
