package org.telegram.uidemo;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;

import org.telegram.ui.Components.Attachment;
import org.telegram.ui.Components.AttachmentAccess;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Device-only checks that never launch a permission dialog or system picker. */
public final class AttachmentAccessDeviceChecks {
    private static final String IMAGES = "android.permission.READ_MEDIA_IMAGES";
    private static final String VIDEO = "android.permission.READ_MEDIA_VIDEO";
    private static final String SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED";

    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        testPermissionClassification(activity);
        testModels();
        testActualGallery(instrumentation, activity);
    }
    private static void testPermissionClassification(Context context) {
        if (Build.VERSION.SDK_INT < 23) return;
        Context denied = permissions(context, false, false, false, false);
        check(!AttachmentAccess.hasGalleryAccess(denied), "denied gallery access");
        check(!AttachmentAccess.hasPartialGalleryAccess(denied), "denied is not partial");
        if (Build.VERSION.SDK_INT < 33) {
            check(AttachmentAccess.hasGalleryAccess(permissions(context, true, false, false, false)), "legacy grant");
        } else {
            check(AttachmentAccess.hasGalleryAccess(permissions(context, false, true, false, false)), "images grant");
            check(AttachmentAccess.hasGalleryAccess(permissions(context, false, false, true, false)), "video grant");
            check(AttachmentAccess.hasPartialGalleryAccess(permissions(context, false, true, false, false)), "images-only is partial");
            check(AttachmentAccess.hasPartialGalleryAccess(permissions(context, false, false, true, false)), "video-only is partial");
            if (Build.VERSION.SDK_INT >= 34) {
                Context selected = permissions(context, false, false, false, true);
                check(AttachmentAccess.hasGalleryAccess(selected), "selected grant");
                check(AttachmentAccess.hasPartialGalleryAccess(selected), "selected access is partial");
                Context full = permissions(context, false, true, true, true);
                check(AttachmentAccess.hasGalleryAccess(full) && !AttachmentAccess.hasPartialGalleryAccess(full), "full access");
            }
        }
    }
    private static Context permissions(Context base, boolean legacy, boolean images, boolean video, boolean selected) {
        return new ContextWrapper(base) {
            @Override public int checkSelfPermission(String permission) {
                boolean granted = permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE) ? legacy
                        : permission.equals(IMAGES) ? images : permission.equals(VIDEO) ? video
                        : permission.equals(SELECTED) && selected;
                return granted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
            }
        };
    }
    private static void testModels() {
        Uri uri = Uri.parse("content://org.telegram.uidemo.test/document/42");
        Attachment photo = Attachment.media(42, uri, "image/jpeg", "photo.jpg", 12, 100, 80, 0, null);
        Attachment video = Attachment.media(43, uri, "video/mp4", "video.mp4", 100, 200, 300, 4, null);
        Attachment file = Attachment.file(44, uri, "document.pdf", "application/pdf", 20);
        check(photo.type == Attachment.Type.PHOTO && photo.uri.equals(uri) && photo.width == 100, "photo snapshot");
        check(video.type == Attachment.Type.VIDEO && video.duration == 4, "video snapshot");
        check(file.type == Attachment.Type.FILE && file.uri.equals(uri), "file content URI preserved");
    }
    private static void testActualGallery(Instrumentation instrumentation, Activity activity) throws Exception {
        boolean hasAccess = AttachmentAccess.hasGalleryAccess(activity);
        boolean partial = AttachmentAccess.hasPartialGalleryAccess(activity);
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger permissionCalls = new AtomicInteger(), resultCalls = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<Attachment>> received = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> AttachmentAccess.loadGallery(activity, new AttachmentAccess.GalleryResult() {
            @Override public void onResult(List<Attachment> items) {
                try {
                    check(Looper.myLooper() == Looper.getMainLooper(), "result delivered on main thread");
                    resultCalls.incrementAndGet(); received.set(items);
                    check(items != null, "non-null gallery snapshot");
                    for (Attachment item : items) {
                        check(item != null && item.uri != null && "content".equals(item.uri.getScheme()), "gallery uses content URIs");
                        check(item.type == Attachment.Type.PHOTO || item.type == Attachment.Type.VIDEO, "gallery media type");
                        check(item.size >= 0 && item.duration >= 0, "gallery metadata");
                    }
                    if (!hasAccess) check(items.isEmpty(), "no permission yields empty snapshot");
                } catch (Throwable error) { failure.compareAndSet(null, error); }
                finally { done.countDown(); }
            }
            @Override public void onError(Throwable error) { failure.compareAndSet(null, error); }
        }, (granted, isPartial) -> {
            try {
                check(Looper.myLooper() == Looper.getMainLooper(), "permission delivered on main thread");
                permissionCalls.incrementAndGet();
                check(granted == hasAccess && isPartial == partial, "real permission state reported");
            } catch (Throwable error) { failure.compareAndSet(null, error); }
        }, false));
        check(done.await(10, TimeUnit.SECONDS), "gallery callback timeout");
        if (failure.get() != null) throw new AssertionError("gallery read failed", failure.get());
        check(permissionCalls.get() == 1 && resultCalls.get() == 1 && received.get() != null, "gallery callbacks once");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private AttachmentAccessDeviceChecks() { }
}
