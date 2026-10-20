package org.telegram.uidemo;

import android.app.Application;
import android.content.res.Configuration;

import org.telegram.ui.TelegramUI;

public class DemoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        TelegramUI.init(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        TelegramUI.onConfigurationChanged(newConfig);
    }
}
