package org.telegram.uidemo;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/** Offline host mapping: existing upstream artwork, one JSON and one gzipped TGS. */
public final class LocalEmojiSamples {
    public static final long JSON_ID = 90001;
    public static final long TGS_ID = 90002;

    public static void install(Context context) throws IOException {
        File json = copy(context, "emoji-duck.json", org.telegram.tgui.R.raw.utyan_newborn, false);
        File tgs = copy(context, "emoji-duck.tgs", org.telegram.tgui.R.raw.utyan_private, true);
        AnimatedEmojiDrawable.setSourceProvider(id -> id == JSON_ID ? json : id == TGS_ID ? tgs : null);
    }

    private static File copy(Context context, String name, int resource, boolean gzip) throws IOException {
        File file = new File(context.getFilesDir(), name);
        try (InputStream input = context.getResources().openRawResource(resource);
             OutputStream output = gzip ? new GZIPOutputStream(new FileOutputStream(file)) : new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return file;
    }

    public static SpannableStringBuilder text(CharSequence label) {
        SpannableStringBuilder text = new SpannableStringBuilder(label).append(" 🐥 🦆 🙂");
        int start = label.length() + 1;
        text.setSpan(new AnimatedEmojiSpan(JSON_ID, null), start, start + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new AnimatedEmojiSpan(TGS_ID, null), start + 3, start + 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private LocalEmojiSamples() { }
}
