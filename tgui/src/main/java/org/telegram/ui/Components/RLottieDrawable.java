package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.HashMap;

/**
 * tgui stub: upstream RLottieDrawable renders Lottie through the native rlottie library.
 * Phase 1 of the UI library has no NDK, so this keeps the API shape and draws nothing.
 * Views that use it fall back to their static icon (if any).
 */
public class RLottieDrawable extends Drawable implements Animatable {

    private final int width;
    private final int height;
    private boolean running;
    private int currentFrame;
    private int framesCount = 1;
    private boolean autoRepeat = true;
    private Runnable onFinishCallback;
    protected View currentParentView;

    public RLottieDrawable(int rawRes, String name, int w, int h) {
        this(rawRes, name, w, h, true, null);
    }

    public RLottieDrawable(int rawRes, String name, int w, int h, boolean startDecode, int[] colorReplacement) {
        width = w;
        height = h;
    }

    @Override
    public void draw(Canvas canvas) {
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    @Override
    public void start() {
        running = true;
        if (onFinishCallback != null && !autoRepeat) {
            Runnable r = onFinishCallback;
            running = false;
            r.run();
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public void restart() {
        start();
    }

    public boolean restart(boolean force) {
        start();
        return true;
    }

    public void setLayerColor(String layerName, int color) {
    }

    public void replaceColors(int[] colors) {
    }

    public void setColorReplacement(HashMap<Integer, Integer> colors) {
    }

    public void beginApplyLayerColors() {
    }

    public void commitApplyLayerColors() {
    }

    public void setCurrentFrame(int frame) {
        currentFrame = frame;
    }

    public void setCurrentFrame(int frame, boolean async) {
        currentFrame = frame;
    }

    public void setCurrentFrame(int frame, boolean async, boolean resetFrame) {
        currentFrame = frame;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public int getFramesCount() {
        return framesCount;
    }

    public void setProgress(float progress) {
    }

    public void setProgress(float progress, boolean async) {
    }

    public float getProgress() {
        return 0;
    }

    public void setCustomEndFrame(int frame) {
    }

    public boolean setCustomEndFrame(int frame, boolean animated) {
        return false;
    }

    public int getCustomEndFrame() {
        return -1;
    }

    public void setPlayInDirectionOfCustomEndFrame(boolean value) {
    }

    public void setAutoRepeat(int value) {
        autoRepeat = value == 1;
    }

    public void setAllowDecodeSingleFrame(boolean value) {
    }

    public void setOnFinishCallback(Runnable callback, int frame) {
        onFinishCallback = callback;
    }

    public void setOnAnimationEndListener(Runnable runnable) {
        onFinishCallback = runnable;
    }

    public void setMasterParent(View parent) {
        currentParentView = parent;
    }

    public void addParentView(View view) {
    }

    public void removeParentView(View view) {
    }

    public void setInvalidateOnProgressSet(boolean value) {
    }

    public boolean hasBitmap() {
        return false;
    }

    public boolean isLastFrame() {
        return true;
    }

    public void recycle() {
    }

    public void recycle(boolean uiThread) {
    }

    public long getDuration() {
        return 0;
    }
}
