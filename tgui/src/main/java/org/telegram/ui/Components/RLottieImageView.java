package org.telegram.ui.Components;

import org.telegram.messenger.AndroidUtilities;

import android.content.Context;
import android.widget.ImageView;

import java.util.HashMap;

/**
 * tgui stub: ImageView with the RLottieImageView API. Lottie playback is not available in
 * phase 1 (no native rlottie), so setAnimation() keeps the drawable but renders nothing.
 */
public class RLottieImageView extends ImageView {

    private RLottieDrawable drawable;
    private boolean autoRepeat;
    private boolean playing;
    private HashMap<String, Integer> layerColors;

    public RLottieImageView(Context context) {
        super(context);
    }

    public void clearLayerColors() {
        if (layerColors != null) {
            layerColors.clear();
        }
    }

    public void setLayerColor(String layer, int color) {
        if (layerColors == null) {
            layerColors = new HashMap<>();
        }
        layerColors.put(layer, color);
        if (drawable != null) {
            drawable.setLayerColor(layer, color);
        }
    }

    public void replaceColors(int[] colors) {
    }

    public void setAnimation(int resId, int w, int h) {
        setAnimation(resId, w, h, null);
    }

    public void setAnimation(int resId, int w, int h, int[] colorReplacement) {
        setAnimation(new RLottieDrawable(resId, "" + resId, AndroidUtilitiesCompat.dp(w), AndroidUtilitiesCompat.dp(h), false, colorReplacement));
    }

    public void setOnAnimationEndListener(Runnable r) {
        if (drawable != null) {
            drawable.setOnAnimationEndListener(r);
        }
    }

    public void setAnimation(RLottieDrawable lottieDrawable) {
        if (drawable == lottieDrawable) {
            return;
        }
        drawable = lottieDrawable;
        if (drawable != null) {
            drawable.setMasterParent(this);
            if (autoRepeat) {
                drawable.setAutoRepeat(1);
            }
        }
        setImageDrawable(drawable);
    }

    public void clearAnimationDrawable() {
        drawable = null;
        setImageDrawable(null);
    }

    public void setProgress(float progress) {
        if (drawable != null) {
            drawable.setProgress(progress);
        }
    }

    public void setAutoRepeat(boolean repeat) {
        autoRepeat = repeat;
    }

    public void setOnlyLastFrame(boolean value) {
    }

    public void setReverse() {
    }

    public RLottieDrawable getAnimatedDrawable() {
        return drawable;
    }

    public boolean isPlaying() {
        return drawable != null && drawable.isRunning();
    }

    public void playAnimation() {
        if (drawable != null) {
            drawable.start();
        }
    }

    public void stopAnimation() {
        if (drawable != null) {
            drawable.stop();
        }
    }

    private static class AndroidUtilitiesCompat {
        static int dp(float value) {
            return org.telegram.messenger.AndroidUtilities.dp(value);
        }
    }
}
