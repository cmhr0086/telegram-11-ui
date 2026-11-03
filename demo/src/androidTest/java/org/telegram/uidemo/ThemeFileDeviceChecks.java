package org.telegram.uidemo;

import android.app.Activity;
import android.util.SparseIntArray;
import org.telegram.ui.ActionBar.Theme;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Regression for Windows CRLF assets producing transparent text and backgrounds. */
public final class ThemeFileDeviceChecks {
    public static void run(Activity activity) throws Exception {
        for (String newline : new String[]{"\n", "\r\n"}) {
            File file = File.createTempFile("theme-lines-", ".attheme", activity.getCacheDir());
            String[] link = new String[1];
            try {
                String text = "chat_messageTextIn=-328966" + newline
                        + "windowBackgroundWhite=#123456" + newline
                        + "WLS=https://example.test/wallpaper" + newline + "WPS" + newline;
                byte[] header = text.getBytes(StandardCharsets.UTF_8);
                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(header);
                    output.write(new byte[]{0, 1, 2, 3});
                }
                SparseIntArray values = Theme.getThemeFileValues(file, null, link);
                check(values.get(Theme.key_chat_messageTextIn) == -328966, "decimal color with " + newline.length() + "-byte newline");
                check(values.get(Theme.key_windowBackgroundWhite) == 0xff123456, "hex color");
                check("https://example.test/wallpaper".equals(link[0]), "wallpaper link excludes CR");
                check(values.get(Theme.key_wallpaperFileOffset) == header.length, "binary wallpaper byte offset is preserved");
            } finally { file.delete(); }
        }
        for (String asset : new String[]{"night.attheme", "darkblue.attheme"}) {
            SparseIntArray values = Theme.getThemeFileValues(null, asset, null);
            check(values.get(Theme.key_chat_messageTextIn) == -328966, asset + " packaged text color");
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
