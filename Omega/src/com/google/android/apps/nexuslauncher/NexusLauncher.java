package com.google.android.apps.nexuslauncher;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherCallbacks;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.WallpaperColorInfo;
import com.android.launcher3.util.Themes;
import com.android.systemui.plugins.shared.LauncherExterns;
import com.google.android.apps.nexuslauncher.qsb.QsbAnimationController;
import com.google.android.apps.nexuslauncher.search.ItemInfoUpdateReceiver;
import com.google.android.apps.nexuslauncher.smartspace.SmartspaceController;
import com.google.android.apps.nexuslauncher.smartspace.SmartspaceView;
import com.google.android.apps.nexuslauncher.utils.ActionIntentFilter;
import com.google.android.libraries.gsa.launcherclient.LauncherClient;
import com.google.android.libraries.gsa.launcherclient.LauncherClientService;
import com.google.android.libraries.gsa.launcherclient.StaticInteger;
import com.saggitt.omega.OmegaLauncher;
import com.saggitt.omega.settings.SettingsActivity;
import com.saggitt.omega.smartspace.FeedBridge;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class NexusLauncher {
    private final Launcher mLauncher;
    final NexusLauncherCallbacks mCallbacks;
    private boolean mFeedRunning;
    private final LauncherExterns mExterns;
    private boolean mRunning;
    public LauncherClient mClient;
    private NexusLauncherOverlay mOverlay;
    private boolean mStarted;
    private final Bundle mUiInformation = new Bundle();
    private ItemInfoUpdateReceiver mItemInfoUpdateReceiver;
    public QsbAnimationController mQsbAnimationController;

    public NexusLauncher(OmegaLauncher activity) {
        mLauncher = activity;
        mExterns = activity;
        mCallbacks = new NexusLauncherCallbacks();
        mLauncher.setLauncherCallbacks(mCallbacks);
        mLauncher.addOnDeviceProfileChangeListener(dp -> mClient.redraw());
    }

    public void registerSmartspaceView(SmartspaceView smartspace) {
        mCallbacks.registerSmartspaceView(smartspace);
    }

    class NexusLauncherCallbacks implements LauncherCallbacks, SharedPreferences.OnSharedPreferenceChangeListener, WallpaperColorInfo.OnChangeListener {
        private Set<SmartspaceView> mSmartspaceViews = Collections.newSetFromMap(new WeakHashMap<>());
        private final FeedReconnector mFeedReconnector = new FeedReconnector();

        private ItemInfoUpdateReceiver getUpdateReceiver() {
            if (mItemInfoUpdateReceiver == null) {
                mItemInfoUpdateReceiver = new ItemInfoUpdateReceiver(mLauncher, mCallbacks);
            }
            return mItemInfoUpdateReceiver;
        }

        public void dump(final String s, final FileDescriptor fileDescriptor, final PrintWriter printWriter, final String[] array) {
            SmartspaceController.get(mLauncher).cX(s, printWriter);
        }

        public void onAttachedToWindow() {
            mClient.onAttachedToWindow();
            mFeedReconnector.start();
        }

        void registerSmartspaceView(SmartspaceView smartspace) {
            mSmartspaceViews.add(smartspace);
        }

        public void onCreate(final Bundle bundle) {
            SharedPreferences prefs = Utilities.getPrefs(mLauncher);
            mOverlay = new NexusLauncherOverlay(mLauncher);
            mClient = new LauncherClient(mLauncher, mOverlay, new StaticInteger(
                    (prefs.getBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF,
                            minusOneAvailable()) ? 1 : 0) | 2 | 4 | 8));
            mOverlay.setClient(mClient);

            prefs.registerOnSharedPreferenceChangeListener(this);

            SmartspaceController.get(mLauncher).cW();

            mQsbAnimationController = new QsbAnimationController(mLauncher);

            mUiInformation.putInt("system_ui_visibility", mLauncher.getWindow().getDecorView().getSystemUiVisibility());
            applyFeedTheme(false);
            WallpaperColorInfo instance = WallpaperColorInfo.getInstance(mLauncher);
            instance.addOnChangeListener(this);
            onExtractedColorsChanged(instance);

            getUpdateReceiver().onCreate();
        }

        public void onDestroy() {
            LauncherClient launcherClient = mClient;
            if (!launcherClient.mDestroyed) {
                launcherClient.mActivity.unregisterReceiver(launcherClient.googleInstallListener);
            }

            launcherClient.mDestroyed = true;
            launcherClient.mBaseService.disconnect();

            if (launcherClient.mOverlayCallback != null) {
                launcherClient.mOverlayCallback.mClient = null;
                launcherClient.mOverlayCallback.mWindowManager = null;
                launcherClient.mOverlayCallback.mWindow = null;
                launcherClient.mOverlayCallback = null;
            }

            LauncherClientService service = launcherClient.mLauncherService;
            LauncherClient client = service.getClient();
            if (client != null && client.equals(launcherClient)) {
                service.mClient = null;
                if (!launcherClient.mActivity.isChangingConfigurations()) {
                    service.disconnect();
                    if (LauncherClientService.sInstance == service) {
                        LauncherClientService.sInstance = null;
                    }
                }
            }

            Utilities.getPrefs(mLauncher).unregisterOnSharedPreferenceChangeListener(this);
            WallpaperColorInfo.getInstance(mLauncher).removeOnChangeListener(this);

            getUpdateReceiver().onDestroy();
        }

        public void onDetachedFromWindow() {
            mFeedReconnector.stop();
            mClient.onDetachedFromWindow();
        }

        @Override
        public void onHomeIntent(boolean internalStateHandled) {
            mClient.hideOverlay(mFeedRunning);
        }

        public void onPause() {
            mRunning = false;
            mClient.onPause();

            for (SmartspaceView smartspace : mSmartspaceViews) {
                smartspace.onPause();
            }
        }

        public void onResume() {
            mRunning = true;
            if (mStarted) {
                mFeedRunning = true;
            }

            mClient.onResume();
        }

        public void onStart() {
            if (!ActionIntentFilter.googleEnabled(mLauncher)) {
                mOverlay.setPersistentFlags(0);
            }

            mStarted = true;
            mClient.onStart();
        }

        public void onStop() {
            mStarted = false;
            mClient.onStop();
            if (!mRunning) {
                mFeedRunning = false;
            }
            if (mOverlay.mFlagsChanged) {
                mOverlay.mLauncher.recreate();
            }
        }

        public boolean startSearch(String s, boolean b, Bundle bundle) {
            View gIcon = mLauncher.findViewById(R.id.g_icon);
            while (gIcon != null && !gIcon.isClickable()) {
                if (gIcon.getParent() instanceof View) {
                    gIcon = (View) gIcon.getParent();
                } else {
                    gIcon = null;
                }
            }
            if (gIcon != null && gIcon.performClick()) {
//                mExterns.clearTypedText();
                return true;
            }
            return false;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case SettingsActivity.ENABLE_MINUS_ONE_PREF:
                    mClient.showOverlay(sharedPreferences.getBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF,
                            minusOneAvailable()));
                    break;
                case SettingsActivity.FEED_THEME_PREF:
                    applyFeedTheme(true);
                    break;
            }
        }

        boolean minusOneAvailable() {
            return FeedBridge.useBridge(mLauncher)
                    || ((mLauncher.getApplicationInfo().flags & FLAG_SYSTEM) == FLAG_SYSTEM);
        }

        @Override
        public void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo) {
            int alpha = mLauncher.getResources().getInteger(R.integer.extracted_color_gradient_alpha);

            mUiInformation.putInt("background_color_hint", primaryColor(wallpaperColorInfo, mLauncher, alpha));
            mUiInformation.putInt("background_secondary_color_hint", secondaryColor(wallpaperColorInfo, mLauncher, alpha));

            applyFeedTheme(true);
        }

        private void applyFeedTheme(boolean redraw) {
            String prefValue = Utilities.getPrefs(mLauncher).getString(SettingsActivity.FEED_THEME_PREF, null);
            int feedTheme;
            try {
                feedTheme = Integer.valueOf(prefValue == null ? "1" : prefValue);
            } catch (Exception e) {
                feedTheme = 1;
            }
            boolean auto = (feedTheme & 1) != 0;
            boolean preferDark = (feedTheme & 2) != 0;
            boolean isDark = auto ? Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark) : preferDark;
            mUiInformation.putBoolean("is_background_dark", isDark);

            if (redraw) {
                mClient.redraw(mUiInformation);
            }
        }

        class FeedReconnector implements Runnable {
            private final static int MAX_RETRIES = 10;
            private final static int RETRY_DELAY_MS = 500;

            private final Handler mHandler = new Handler();
            private int mFeedConnectionTries;

            void start() {
                stop();
                mFeedConnectionTries = 0;
                mHandler.post(this);
            }

            void stop() {
                mHandler.removeCallbacks(this);
            }

            @Override
            public void run() {
                if (Utilities.getPrefs(mLauncher).getBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF, true) &&
                        !mClient.mDestroyed &&
                        mClient.mLayoutParams != null &&
                        !mOverlay.mAttached &&
                        mFeedConnectionTries++ < MAX_RETRIES) {
                    mClient.exchangeConfig();
                    mHandler.postDelayed(this, RETRY_DELAY_MS);
                }
            }
        }
    }

    public static int primaryColor(WallpaperColorInfo wallpaperColorInfo, Context context, int alpha) {
        return compositeAllApps(ColorUtils.setAlphaComponent(wallpaperColorInfo.getMainColor(), alpha), context);
    }

    public static int secondaryColor(WallpaperColorInfo wallpaperColorInfo, Context context, int alpha) {
        return compositeAllApps(ColorUtils.setAlphaComponent(wallpaperColorInfo.getSecondaryColor(), alpha), context);
    }

    private static int compositeAllApps(int color, Context context) {
        return ColorUtils.compositeColors(Themes.getAttrColor(context, R.attr.allAppsScrimColor), color);
    }
}