package com.example.tguihost;

import android.app.Application;
import android.content.res.Configuration;

import org.telegram.ui.TelegramUI;

public class HostApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        TelegramUI.init(this);
        // Host owns the local files and id mapping; tgui never fetches emoji from a server.
        try {
            java.io.File file = new java.io.File(getFilesDir(), "local-emoji.json");
            try (java.io.InputStream input = getResources().openRawResource(org.telegram.tgui.R.raw.utyan_newborn);
                 java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
            org.telegram.ui.Components.AnimatedEmojiDrawable.setSourceProvider(id -> id == 90001 ? file : null);
        } catch (java.io.IOException error) {
            org.telegram.messenger.FileLog.e(error);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        TelegramUI.onConfigurationChanged(newConfig);
    }
}
