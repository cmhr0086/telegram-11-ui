package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small content-URI thumbnail boundary; all I/O is off the main thread. */
public final class AttachmentThumbnailView extends ImageView {
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private Attachment item;
    private long generation;
    public AttachmentThumbnailView(Context context) { super(context); setScaleType(ScaleType.CENTER_CROP); }
    public void setAttachment(Attachment item) { this.item = item; load(); }
    private void load() {
        long token = ++generation;
        setImageDrawable(item == null ? null : item.thumbnail);
        Attachment bound = item;
        if (!isAttachedToWindow() || bound == null || bound.uri == null || bound.thumbnail != null
                || bound.type != Attachment.Type.PHOTO && bound.type != Attachment.Type.VIDEO) return;
        Context context = getContext().getApplicationContext();
        IO.execute(() -> {
            Bitmap decoded = null;
            try {
                if (bound.type == Attachment.Type.VIDEO) {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    try {
                        retriever.setDataSource(context, bound.uri);
                        decoded = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    } finally { retriever.release(); }
                    if (decoded != null && Math.max(decoded.getWidth(), decoded.getHeight()) > 512) {
                        float ratio = 512f / Math.max(decoded.getWidth(), decoded.getHeight());
                        Bitmap scaled = Bitmap.createScaledBitmap(decoded, Math.max(1, (int) (decoded.getWidth() * ratio)), Math.max(1, (int) (decoded.getHeight() * ratio)), true);
                        if (scaled != decoded) decoded.recycle(); decoded = scaled;
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= 28) {
                    decoded = Api28.decode(context, bound.uri);
                } else {
                    BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                    try (InputStream in = context.getContentResolver().openInputStream(bound.uri)) { BitmapFactory.decodeStream(in, null, options); }
                    options.inSampleSize = 1;
                    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
                    options.inJustDecodeBounds = false;
                    try (InputStream in = context.getContentResolver().openInputStream(bound.uri)) { decoded = BitmapFactory.decodeStream(in, null, options); }
                }
            } catch (Exception ignored) { }
            Bitmap bitmap = decoded;
            MAIN.post(() -> {
                if (token != generation || !isAttachedToWindow()) { if (bitmap != null) bitmap.recycle(); return; }
                if (bitmap != null) setImageBitmap(bitmap);
            });
        });
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); load(); }
    @Override protected void onDetachedFromWindow() { generation++; setImageDrawable(null); super.onDetachedFromWindow(); }
    private static final class Api28 {
        static Bitmap decode(Context context, android.net.Uri uri) throws java.io.IOException {
            return android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.getContentResolver(), uri),
                    (decoder, info, source) -> {
                        decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                        int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                        float ratio = Math.min(1, 512f / Math.max(width, height));
                        decoder.setTargetSize(Math.max(1, (int) (width * ratio)), Math.max(1, (int) (height * ratio)));
                    });
        }
    }
}
