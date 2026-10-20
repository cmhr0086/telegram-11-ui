/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.graphics.Bitmap;
import java.nio.ByteBuffer;
import android.graphics.Canvas;
import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.security.MessageDigest;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.security.SecureRandom;
import java.util.Random;
import java.util.regex.Pattern;

public class Utilities {
    public static Pattern pattern = Pattern.compile("[\\-0-9]+");
    public static SecureRandom random = new SecureRandom();
    public static Random fastRandom = new Random(random.nextLong());

    public static volatile DispatchQueue stageQueue = new DispatchQueue("stageQueue");
    public static volatile DispatchQueue globalQueue = new DispatchQueue("globalQueue");
    public static volatile DispatchQueue cacheClearQueue = new DispatchQueue("cacheClearQueue");
    public static volatile DispatchQueue searchQueue = new DispatchQueue("searchQueue");
    public static volatile DispatchQueue phoneBookQueue = new DispatchQueue("phoneBookQueue");
    public static volatile DispatchQueue themeQueue = new DispatchQueue("themeQueue");
    public static volatile DispatchQueue externalNetworkQueue = new DispatchQueue("externalNetworkQueue");

    private final static String RANDOM_STRING_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    static {
        try {
            File URANDOM_FILE = new File("/dev/urandom");
            FileInputStream sUrandomIn = new FileInputStream(URANDOM_FILE);
            byte[] buffer = new byte[1024];
            sUrandomIn.read(buffer);
            sUrandomIn.close();
            random.setSeed(buffer);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
//    public static native int saveProgressiveJpeg(Bitmap bitmap, int width, int height, int stride, int quality, String path);
    public static native void generateGradient(Bitmap bitmap, boolean unpin, int phase, float progress, int width, int height, int stride, int[] colors);

    // tgui: upstream uses native stack blur (libtmessages); this is a Java port of the same algorithm.
    public static void stackBlurBitmap(Bitmap bitmap, int radius) {
        if (bitmap == null || radius < 1) {
            return;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);
        stackBlur(pix, w, h, radius);
        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
    }

    public static void blurBitmap(Object bitmap, int radius, int unpin, int width, int height, int stride) {
        if (bitmap instanceof Bitmap) {
            stackBlurBitmap((Bitmap) bitmap, radius);
        }
    }

    private static void stackBlur(int[] pix, int w, int h, int radius) {
        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;
        int[] r = new int[wh], g = new int[wh], b = new int[wh], a = new int[wh];
        int rsum, gsum, bsum, asum, x, y, i, p, yp, yi, yw;
        int[] vmin = new int[Math.max(w, h)];
        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }
        yw = yi = 0;
        int[][] stack = new int[div][4];
        int stackpointer, stackstart, rbs;
        int[] sir;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum, aoutsum, rinsum, ginsum, binsum, ainsum;
        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = ainsum = routsum = goutsum = boutsum = aoutsum = rsum = gsum = bsum = asum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                sir[3] = (p >>> 24);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                asum += sir[3] * rbs;
                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3];
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3];
                }
            }
            stackpointer = radius;
            for (x = 0; x < w; x++) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]; a[yi] = dv[asum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum; asum -= aoutsum;
                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]; aoutsum -= sir[3];
                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                sir[3] = (p >>> 24);
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3];
                rsum += rinsum; gsum += ginsum; bsum += binsum; asum += ainsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]; ainsum -= sir[3];
                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = ainsum = routsum = goutsum = boutsum = aoutsum = rsum = gsum = bsum = asum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;
                sir = stack[i + radius];
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]; sir[3] = a[yi];
                rbs = r1 - Math.abs(i);
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs; asum += a[yi] * rbs;
                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3];
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3];
                }
                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                pix[yi] = (dv[asum] << 24) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum; asum -= aoutsum;
                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]; aoutsum -= sir[3];
                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];
                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]; sir[3] = a[p];
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3];
                rsum += rinsum; gsum += ginsum; bsum += binsum; asum += ainsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]; ainsum -= sir[3];
                yi += w;
            }
        }
    }

    public static Bitmap stackBlurBitmapMax(Bitmap bitmap) {
        return stackBlurBitmapMax(bitmap, false);
    }

    public static Bitmap stackBlurBitmapMax(Bitmap bitmap, boolean round) {
        int w = AndroidUtilities.dp(20);
        int h = (int) (AndroidUtilities.dp(20) * (float) bitmap.getHeight() / bitmap.getWidth());
        Bitmap scaledBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(scaledBitmap);
        canvas.save();
        canvas.scale((float) scaledBitmap.getWidth() / bitmap.getWidth(), (float) scaledBitmap.getHeight() / bitmap.getHeight());
        if (round) {
            Path path = new Path();
            path.addCircle(bitmap.getWidth() / 2f, bitmap.getHeight() / 2f, Math.min(bitmap.getWidth(), bitmap.getHeight()) / 2f - 1, Path.Direction.CW);
            canvas.clipPath(path);
        }
        canvas.drawBitmap(bitmap, 0, 0, null);
        canvas.restore();
        Utilities.stackBlurBitmap(scaledBitmap, Math.max(10, Math.max(w, h) / 150));
        return scaledBitmap;
    }

    public static Bitmap stackBlurBitmapWithScaleFactor(Bitmap bitmap, float scaleFactor) {
        int w = (int) Math.max(AndroidUtilities.dp(20), bitmap.getWidth() / scaleFactor);
        int h = (int) Math.max(AndroidUtilities.dp(20) * (float) bitmap.getHeight() / bitmap.getWidth(), bitmap.getHeight() / scaleFactor);
        Bitmap scaledBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(scaledBitmap);
        canvas.save();
        canvas.scale((float) scaledBitmap.getWidth() / bitmap.getWidth(), (float) scaledBitmap.getHeight() / bitmap.getHeight());
        canvas.drawBitmap(bitmap, 0, 0, null);
        canvas.restore();
        Utilities.stackBlurBitmap(scaledBitmap, Math.max(10, Math.max(w, h) / 150));
        return scaledBitmap;
    }

    public static Bitmap blurWallpaper(Bitmap src) {
        if (src == null) {
            return null;
        }
        Bitmap b;
        if (src.getHeight() > src.getWidth()) {
            b = Bitmap.createBitmap(Math.round(450f * src.getWidth() / src.getHeight()), 450, Bitmap.Config.ARGB_8888);
        } else {
            b = Bitmap.createBitmap(450, Math.round(450f * src.getHeight() / src.getWidth()), Bitmap.Config.ARGB_8888);
        }
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        Rect rect = new Rect(0, 0, b.getWidth(), b.getHeight());
        new Canvas(b).drawBitmap(src, null, rect, paint);
        stackBlurBitmap(b, 12);
        return b;
    }

    public static Integer parseInt(CharSequence value) {
        if (value == null) {
            return 0;
        }
        if (false) {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                return Integer.valueOf(matcher.group());
            }
        } else {
            int val = 0;
            try {
                int start = -1, end;
                for (end = 0; end < value.length(); ++end) {
                    char character = value.charAt(end);
                    boolean allowedChar = character == '-' || character >= '0' && character <= '9';
                    if (allowedChar && start < 0) {
                        start = end;
                    } else if (!allowedChar && start >= 0) {
                        end++;
                        break;
                    }
                }
                if (start >= 0) {
                    String str = value.subSequence(start, end).toString();
//                val = parseInt(str);
                    val = Integer.parseInt(str);
                }
            } catch (Exception ignore) {}
            return val;
        }
        return 0;
    }

    private static int parseInt(final String s) {
        int num = 0;
        boolean negative = true;
        final int len = s.length();
        final char ch = s.charAt(0);
        if (ch == '-') {
            negative = false;
        } else {
            num = '0' - ch;
        }
        int i = 1;
        while (i < len) {
            num = num * 10 + '0' - s.charAt(i++);
        }

        return negative ? -num : num;
    }

    public static Long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        long val = 0L;
        try {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                String num = matcher.group(0);
                val = Long.parseLong(num);
            }
        } catch (Exception ignore) {

        }
        return val;
    }

    public static String parseIntToString(String value) {
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return null;
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static boolean arraysEquals(byte[] arr1, int offset1, byte[] arr2, int offset2) {
        if (arr1 == null || arr2 == null || offset1 < 0 || offset2 < 0 || arr1.length - offset1 > arr2.length - offset2 || arr1.length - offset1 < 0 || arr2.length - offset2 < 0) {
            return false;
        }
        boolean result = true;
        for (int a = offset1; a < arr1.length; a++) {
            if (arr1[a + offset1] != arr2[a + offset2]) {
                result = false;
            }
        }
        return result;
    }

    public static byte[] computeSHA1(byte[] convertme, int offset, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(convertme, offset, len);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[20];
    }

    public static byte[] computeSHA1(ByteBuffer convertme, int offset, int len) {
        int oldp = convertme.position();
        int oldl = convertme.limit();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            convertme.position(offset);
            convertme.limit(len);
            md.update(convertme);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            convertme.limit(oldl);
            convertme.position(oldp);
        }
        return new byte[20];
    }

    public static byte[] computeSHA1(ByteBuffer convertme) {
        return computeSHA1(convertme, 0, convertme.limit());
    }

    public static byte[] computeSHA1(byte[] convertme) {
        return computeSHA1(convertme, 0, convertme.length);
    }

    public static byte[] computeSHA256(byte[] convertme) {
        return computeSHA256(convertme, 0, convertme.length);
    }

    public static byte[] computeSHA256(byte[] convertme, int offset, long len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(convertme, offset, (int) len);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[32];
    }

    public static byte[] computeSHA256(byte[]... args) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (int a = 0; a < args.length; a++) {
                md.update(args[a], 0, args[a].length);
            }
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[32];
    }

    public static byte[] computeSHA512(byte[] convertme) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(convertme, 0, convertme.length);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[64];
    }

    public static byte[] computeSHA512(byte[] convertme, byte[] convertme2) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(convertme, 0, convertme.length);
            md.update(convertme2, 0, convertme2.length);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[64];
    }

    public static byte[] computeSHA512(byte[] convertme, byte[] convertme2, byte[] convertme3) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(convertme, 0, convertme.length);
            md.update(convertme2, 0, convertme2.length);
            md.update(convertme3, 0, convertme3.length);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[64];
    }

    public static byte[] computeSHA256(byte[] b1, int o1, int l1, ByteBuffer b2, int o2, int l2) {
        int oldp = b2.position();
        int oldl = b2.limit();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(b1, o1, l1);
            b2.position(o2);
            b2.limit(l2);
            md.update(b2);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            b2.limit(oldl);
            b2.position(oldp);
        }
        return new byte[32];
    }

    public static long bytesToLong(byte[] bytes) {
        return ((long) bytes[7] << 56) + (((long) bytes[6] & 0xFF) << 48) + (((long) bytes[5] & 0xFF) << 40) + (((long) bytes[4] & 0xFF) << 32)
                + (((long) bytes[3] & 0xFF) << 24) + (((long) bytes[2] & 0xFF) << 16) + (((long) bytes[1] & 0xFF) << 8) + ((long) bytes[0] & 0xFF);
    }

    public static int bytesToInt(byte[] bytes) {
        return (((int) bytes[3] & 0xFF) << 24) + (((int) bytes[2] & 0xFF) << 16) + (((int) bytes[1] & 0xFF) << 8) + ((int) bytes[0] & 0xFF);
    }

    public static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value};
    }

    public static String MD5(String md5) {
        if (md5 == null) {
            return null;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(AndroidUtilities.getStringBytes(md5));
            StringBuilder sb = new StringBuilder();
            for (int a = 0; a < array.length; a++) {
                sb.append(Integer.toHexString((array[a] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            FileLog.e(e);
        }
        return null;
    }

    public static String SHA256(String x) {
        if (x == null) {
            return null;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] array = md.digest(AndroidUtilities.getStringBytes(x));
            StringBuilder sb = new StringBuilder();
            for (int a = 0; a < array.length; a++) {
                sb.append(Integer.toHexString((array[a] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            FileLog.e(e);
        }
        return null;
    }

    public static int clamp(int value, int maxValue, int minValue) {
        return Math.max(Math.min(value, maxValue), minValue);
    }

    public static long clamp(long value, long maxValue, long minValue) {
        return Math.max(Math.min(value, maxValue), minValue);
    }

    public static float clamp(float value, float maxValue, float minValue) {
        if (Float.isNaN(value)) {
            return minValue;
        }
        if (Float.isInfinite(value)) {
            return maxValue;
        }
        return Math.max(Math.min(value, maxValue), minValue);
    }

    public static float clamp01(float value) {
        return clamp(value, 1f, 0f);
    }

    public static double clamp(double value, double maxValue, double minValue) {
        if (Double.isNaN(value)) {
            return minValue;
        }
        if (Double.isInfinite(value)) {
            return maxValue;
        }
        return Math.max(Math.min(value, maxValue), minValue);
    }

    public static String generateRandomString() {
        return generateRandomString(16);
    }

    public static String generateRandomString(int chars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars; i++) {
            sb.append(RANDOM_STRING_CHARS.charAt(fastRandom.nextInt(RANDOM_STRING_CHARS.length())));
        }
        return sb.toString();
    }

    public static String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        String ext = null;
        if (idx != -1) {
            ext = fileName.substring(idx + 1);
        }
        if (ext == null) {
            return null;
        }
        ext = ext.toUpperCase();
        return ext;
    }

    public static interface Callback<T> {
        public void run(T arg);
    }

    public static interface CallbackVoidReturn<ReturnType> {
        public ReturnType run();
    }

    public static interface Callback0Return<ReturnType> {
        public ReturnType run();
    }

    public static interface CallbackReturn<Arg, ReturnType> {
        public ReturnType run(Arg arg);
    }

    public static interface Callback2Return<T1, T2, ReturnType> {
        public ReturnType run(T1 arg, T2 arg2);
    }

    public static interface Callback3Return<T1, T2, T3, ReturnType> {
        public ReturnType run(T1 arg, T2 arg2, T3 arg3);
    }

    public static interface Callback2<T, T2> {
        public void run(T arg, T2 arg2);
    }

    public static interface Callback3<T, T2, T3> {
        public void run(T arg, T2 arg2, T3 arg3);
    }

    public static interface Callback4<T, T2, T3, T4> {
        public void run(T arg, T2 arg2, T3 arg3, T4 arg4);
    }

    public static interface Callback4Return<T, T2, T3, T4, ReturnType> {
        public ReturnType run(T arg, T2 arg2, T3 arg3, T4 arg4);
    }
    public static interface Callback5<T, T2, T3, T4, T5> {
        public void run(T arg, T2 arg2, T3 arg3, T4 arg4, T5 arg5);
    }

    public static interface Callback5Return<T, T2, T3, T4, T5, ReturnType> {
        public ReturnType run(T arg, T2 arg2, T3 arg3, T4 arg4, T5 arg5);
    }

    public static interface IndexedConsumer<T> {
        void accept(T t, int index);
    }

    public static <Key, Value> Value getOrDefault(HashMap<Key, Value> map, Key key, Value defaultValue) {
        Value v = map.get(key);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }

    public static void doCallbacks(Utilities.Callback<Runnable> ...actions) {
        doCallbacks(0, actions);
    }
    private static void doCallbacks(int i, Utilities.Callback<Runnable> ...actions) {
        if (actions != null && actions.length > i) {
            actions[i].run(() -> doCallbacks(i + 1, actions));
        }
    }

    public static void raceCallbacks(Runnable onFinish, Utilities.Callback<Runnable> ...actions) {
        if (actions == null || actions.length == 0) {
            if (onFinish != null) {
                onFinish.run();
            }
            return;
        }
        final int[] finished = new int[] { 0 };
        Runnable checkFinish = () -> {
            finished[0]++;
            if (finished[0] == actions.length) {
                if (onFinish != null) {
                    onFinish.run();
                }
            }
        };
        for (int i = 0; i < actions.length; ++i) {
            actions[i].run(checkFinish);
        }
    }

    public static boolean isNullOrEmpty(final Collection<?> list) {
        return list == null || list.isEmpty();
    }

    public static Uri uriParseSafe(String link) {
        try {
            return Uri.parse(link);
        } catch (Exception ignore) {
            return null;
        }
    }

}
