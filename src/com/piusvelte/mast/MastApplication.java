package com.piusvelte.mast;

import android.app.Application;

import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;

/**
 * Created by bemmanuel on 12/4/15.
 */
public class MastApplication extends Application {

    private static final String APP_ID = "C8E438D7";

    @Override
    public void onCreate() {
        super.onCreate();

        VideoCastManager.initialize(this, APP_ID, null, null)
                .enableFeatures(VideoCastManager.FEATURE_LOCKSCREEN
                        | VideoCastManager.FEATURE_NOTIFICATION
                        | VideoCastManager.FEATURE_DEBUGGING
                        | VideoCastManager.FEATURE_WIFI_RECONNECT
                        | VideoCastManager.FEATURE_AUTO_RECONNECT);
    }
}
