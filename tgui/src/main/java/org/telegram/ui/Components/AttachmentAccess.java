package org.telegram.ui.Components;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android permission/URI boundary; never contacts an account or a server. */
public final class AttachmentAccess {
    public interface PermissionListener { void onPermissionResult(boolean granted, boolean partial); }
    public interface GalleryResult {
        void onResult(List<Attachment> items);
        default void onError(Throwable error) { }
    }
    public interface FileResult { void onResult(List<Attachment> items); }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final String IMAGES = "android.permission.READ_MEDIA_IMAGES";
    private static final String VIDEO = "android.permission.READ_MEDIA_VIDEO";
    private static final String SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED";
    private static boolean granted(Context context, String permission) {
        return Build.VERSION.SDK_INT < 23 || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }
    public static boolean hasGalleryAccess(Context context) {
        return Build.VERSION.SDK_INT < 33 ? granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                : granted(context, IMAGES) || granted(context, VIDEO) || Build.VERSION.SDK_INT >= 34 && granted(context, SELECTED);
    }
    public static boolean hasPartialGalleryAccess(Context context) {
        return Build.VERSION.SDK_INT >= 33 && hasGalleryAccess(context)
                && !(granted(context, IMAGES) && granted(context, VIDEO));
    }
    public static void loadGallery(Activity activity, GalleryResult result, PermissionListener permission, boolean requestPermission) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> loadGallery(activity, result, permission, requestPermission)); return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            if (permission != null) permission.onPermissionResult(false, false);
            result.onResult(Collections.emptyList()); return;
        }
        if (!hasGalleryAccess(activity) && requestPermission && Build.VERSION.SDK_INT >= 23) {
            Bridge bridge = new Bridge();
            bridge.permissionsDone = () -> loadGallery(activity, result, permission, false);
            attach(activity, bridge);
        } else {
            boolean allowed = hasGalleryAccess(activity), partial = hasPartialGalleryAccess(activity);
            if (permission != null) permission.onPermissionResult(allowed, partial);
            if (!allowed) { result.onResult(Collections.emptyList()); return; }
            queryGallery(activity.getApplicationContext(), result);
        }
    }
    /** Explicit reselection action for Android 14 partial grants. Never called automatically on resume. */
    public static void requestGalleryAccess(Activity activity, Runnable done) {
        Bridge bridge = new Bridge(); bridge.permissionsDone = done; attach(activity, bridge);
    }
    public static void pickFiles(Activity activity, FileResult result) {
        Bridge bridge = new Bridge(); bridge.filesDone = result; attach(activity, bridge);
    }
    private static void attach(Activity activity, Bridge bridge) {
        if (activity.isFinishing() || activity.isDestroyed() || Build.VERSION.SDK_INT >= 26 && activity.getFragmentManager().isStateSaved()) {
            bridge.cancel(); return;
        }
        activity.getFragmentManager().beginTransaction().add(bridge, "tgui-attachment-" + System.nanoTime()).commit();
        activity.getFragmentManager().executePendingTransactions();
    }
    private static void queryGallery(Context context, GalleryResult result) {
        IO.execute(() -> {
            ArrayList<Attachment> items = new ArrayList<>();
            java.util.HashMap<Uri, Long> dates = new java.util.HashMap<>();
            Throwable failure = null;
            try {
                boolean partial = Build.VERSION.SDK_INT >= 34 && granted(context, SELECTED);
                if (Build.VERSION.SDK_INT < 33 || granted(context, IMAGES) || partial) readMedia(context, false, items, dates);
                if (Build.VERSION.SDK_INT < 33 || granted(context, VIDEO) || partial) readMedia(context, true, items, dates);
            } catch (Exception error) { failure = error; }
            Throwable error = failure;
            Collections.sort(items, (a, b) -> Long.compare(dates.get(b.uri), dates.get(a.uri)));
            List<Attachment> snapshot = Collections.unmodifiableList(items);
            MAIN.post(() -> { if (error != null) result.onError(error); result.onResult(snapshot); });
        });
    }
    private static void readMedia(Context context, boolean video, ArrayList<Attachment> output, java.util.Map<Uri, Long> dates) {
        Uri collection = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] columns = video ? new String[]{"_id", "_display_name", "mime_type", "_size", "width", "height", "date_added", "duration"}
                : new String[]{"_id", "_display_name", "mime_type", "_size", "width", "height", "date_added"};
        try (Cursor cursor = context.getContentResolver().query(collection, columns, null, null, "date_added DESC")) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String mime = cursor.getString(2);
                if (video && (mime == null || !mime.startsWith("video/"))) mime = "video/*";
                if (!video && (mime == null || mime.startsWith("video/"))) mime = "image/*";
                Attachment item = Attachment.media(id * 2 + (video ? 1 : 0), ContentUris.withAppendedId(collection, id),
                        mime, cursor.getString(1), cursor.getLong(3), cursor.getInt(4), cursor.getInt(5),
                        video ? (int) (cursor.getLong(7) / 1000) : 0, null);
                dates.put(item.uri, cursor.getLong(6));
                output.add(item);
            }
        }
    }
    private static void readFiles(Context context, List<Uri> uris, FileResult result) {
        IO.execute(() -> {
            ArrayList<Attachment> items = new ArrayList<>();
            for (Uri uri : uris) {
                String name = uri.getLastPathSegment(); long size = 0;
                try (Cursor cursor = context.getContentResolver().query(uri,
                        new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) { name = cursor.getString(0); size = cursor.getLong(1); }
                    items.add(Attachment.file(uri.toString().hashCode(), uri, name, context.getContentResolver().getType(uri), size));
                } catch (Exception ignored) { }
            }
            MAIN.post(() -> result.onResult(Collections.unmodifiableList(items)));
        });
    }

    /** Headless platform Fragment lets a plain Activity host receive library-owned permission results. */
    public static final class Bridge extends Fragment {
        private Runnable permissionsDone;
        private FileResult filesDone;
        private boolean finished;
        public Bridge() { }
        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            if (permissionsDone == null && filesDone == null) { finish(); return; }
            if (filesDone != null) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                        .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try { startActivityForResult(intent, 91); } catch (Exception error) { cancel(); finish(); }
            } else if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(Build.VERSION.SDK_INT >= 34 ? new String[]{IMAGES, VIDEO, SELECTED}
                        : Build.VERSION.SDK_INT >= 33 ? new String[]{IMAGES, VIDEO}
                        : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 92);
            } else { completePermissions(); }
        }
        @Override public void onRequestPermissionsResult(int code, String[] names, int[] results) {
            if (code == 92) completePermissions();
        }
        private void completePermissions() {
            Runnable callback = permissionsDone; permissionsDone = null;
            finish(); if (callback != null) callback.run();
        }
        @Override public void onActivityResult(int request, int result, Intent data) {
            if (request != 91) return;
            FileResult callback = filesDone; filesDone = null;
            Context context = getActivity() == null ? null : getActivity().getApplicationContext();
            ArrayList<Uri> uris = new ArrayList<>();
            if (result == Activity.RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
                } else if (data.getData() != null) uris.add(data.getData());
                // ACTION_OPEN_DOCUMENT grants are persistable only when the provider offers them.
                if (context != null && (data.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
                        && (data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                    for (Uri uri : uris) try { context.getContentResolver().takePersistableUriPermission(uri,
                            data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException | IllegalArgumentException ignored) { }
                }
            }
            finish();
            if (callback != null) {
                if (context == null) callback.onResult(Collections.emptyList()); else readFiles(context, uris, callback);
            }
        }
        private void finish() {
            finished = true;
            MAIN.post(() -> { if (isAdded()) getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss(); });
        }
        private void cancel() {
            FileResult file = filesDone; filesDone = null;
            Runnable permission = permissionsDone; permissionsDone = null;
            if (file != null) file.onResult(Collections.emptyList());
            if (permission != null) permission.run();
        }
        @Override public void onDestroy() {
            // A destroyed Activity cannot own a UI callback. The recreated host opens a new sheet.
            permissionsDone = null; filesDone = null;
            super.onDestroy();
        }
    }
    private AttachmentAccess() { }
}
