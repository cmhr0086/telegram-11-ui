package org.telegram.uidemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import org.telegram.messenger.Emoji;
import org.telegram.ui.Components.Gif;
import org.telegram.ui.Components.Sticker;
import org.telegram.ui.Components.StickerSet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/** Offline demo data. Utya/emoji artwork comes from the existing upstream resources. */
public final class LocalStickerSamples {
    public final ArrayList<StickerSet> sets = new ArrayList<>();
    public final ArrayList<Sticker> recent = new ArrayList<>(), favorites = new ArrayList<>();
    public final ArrayList<Gif> gifs = new ArrayList<>();

    public LocalStickerSamples(Context context) throws IOException {
        LocalEmojiSamples.install(context);
        File webp = new File(context.getFilesDir(), "sample-sticker.webp");
        // Read the bundled PNG directly: EmojiDrawable's first asynchronous draw
        // may still be an empty placeholder when exporting a static WEBP.
        Bitmap bitmap = null;
        String[][] data = org.telegram.messenger.EmojiData.data;
        for (int page = 0; page < data.length && bitmap == null; page++) {
            for (int index = 0; index < data[page].length; index++) {
                if ("🙂".equals(data[page][index])) {
                    bitmap = Emoji.loadBitmap("emoji/" + page + "_" + index + ".png");
                    break;
                }
            }
        }
        if (bitmap == null) throw new IOException("Bundled emoji artwork missing");
        Drawable emoji = new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap.copy(Bitmap.Config.ARGB_8888, false));
        try (FileOutputStream out = new FileOutputStream(webp)) { bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out); }
        bitmap.recycle();
        Sticker still = new Sticker(1, Sticker.Format.WEBP, webp, emoji, 160, 160, "🙂");
        Sticker duck = new Sticker(2, Sticker.Format.TGS, new File(context.getFilesDir(), "emoji-duck.tgs"), null, 512, 512, "🦆");
        Sticker newborn = new Sticker(3, Sticker.Format.TGS, new File(context.getFilesDir(), "emoji-duck.json"), null, 512, 512, "🐥");
        Sticker video = new Sticker(4, Sticker.Format.WEBM, copy(context, R.raw.sample_video_sticker, "sample-sticker.webm"), null, 160, 160, "🎬");
        sets.add(new StickerSet(100, context.getString(R.string.local_sticker_pack), Arrays.asList(still, duck, newborn, video)));
        recent.add(duck); favorites.add(still);
        gifs.add(new Gif(10, copy(context, R.raw.sample_sticker, "sample.gif"), null, 200, 112));
    }

    private static File copy(Context context, int resource, String name) throws IOException {
        File file = new File(context.getFilesDir(), name);
        try (InputStream in = context.getResources().openRawResource(resource); FileOutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        return file;
    }
}
