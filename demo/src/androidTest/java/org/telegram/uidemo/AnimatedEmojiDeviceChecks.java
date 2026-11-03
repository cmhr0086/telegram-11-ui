package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.EditTextEffects;
import org.telegram.ui.Components.RLottieDrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Invoke on the instrumentation worker, never on the main thread. */
public final class AnimatedEmojiDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout container;
    private EditTextEffects edit;
    private TextBubbleCell bubble;
    private AnimatedEmojiSpan[] editSpans;
    private AnimatedEmojiSpan[] bubbleSpans;

    private AnimatedEmojiDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation;
        this.activity = activity;
    }

    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        AnimatedEmojiDeviceChecks checks = new AnimatedEmojiDeviceChecks(instrumentation, activity);
        AnimatedEmojiDrawable.SourceProvider previous = AnimatedEmojiDrawable.getSourceProvider();
        boolean systemEmoji = SharedConfig.useSystemEmoji;
        try {
            checks.status("install samples");
            LocalEmojiSamples.install(activity);
            checks.status("mixed text, clone and entities");
            checks.mixedCloneAndEntities();
            checks.status("attach input and bubble; wait for layout");
            checks.realViewsAndFrames();
            checks.emojiOnlyAndInPlaceEdit();
            checks.status("missing and corrupt providers");
            checks.providerFailures();
            checks.status("shared bindings and reattachment");
            checks.sharedBindings();
            checks.status("queued native frame disposal stress");
            checks.queuedFrameDisposal();
            checks.status("checks complete");
        } finally {
            checks.main(() -> {
                if (checks.container != null && checks.container.getParent() instanceof ViewGroup) {
                    ((ViewGroup) checks.container.getParent()).removeView(checks.container);
                }
                SharedConfig.useSystemEmoji = systemEmoji;
                AnimatedEmojiDrawable.setSourceProvider(previous);
            });
        }
    }

    private void status(String stage) {
        Bundle bundle = new Bundle();
        bundle.putString("stream", "\nAnimatedEmojiDeviceChecks: " + stage + "\n");
        instrumentation.sendStatus(0, bundle);
    }

    private void main(Runnable runnable) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { runnable.run(); } catch (Throwable t) { failure.set(t); }
        });
        if (failure.get() != null) throw new AssertionError("main-thread emoji check failed", failure.get());
    }

    private void mixedCloneAndEntities() {
        main(() -> {
            SharedConfig.useSystemEmoji = false;
            SpannableStringBuilder original = LocalEmojiSamples.text("mixed");
            AnimatedEmojiSpan[] source = spans(original);
            CharSequence replaced = Emoji.replaceEmoji(original, new TextPaint().getFontMetricsInt(), true);
            AnimatedEmojiSpan[] after = spans(replaced);
            check(after.length == 2, "mixed ordinary/custom emoji must retain both custom spans");
            CharSequence cloned = AnimatedEmojiSpan.cloneSpans(original);
            AnimatedEmojiSpan[] clones = spans(cloned);
            check(clones.length == 2 && clones[0] != source[0] && clones[1] != source[1], "clone must copy span objects");
            check(((Spanned) cloned).getSpanStart(clones[0]) == original.getSpanStart(source[0]), "clone must preserve offsets");
            clones[0].documentId = -1;
            check(source[0].documentId == LocalEmojiSamples.JSON_ID, "clone mutation must not mutate source");

            CharSequence[] message = { original };
            ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(0).getEntities(message, true);
            check(entities != null, "custom entities must be extracted");
            SpannableStringBuilder restored = new SpannableStringBuilder(message[0].toString());
            MediaDataController.addAnimatedEmojiSpans(entities, restored, null);
            AnimatedEmojiSpan[] recovered = spans(restored);
            check(recovered.length == 2, "custom entities must restore two spans");
            for (AnimatedEmojiSpan span : recovered) {
                check(span.getDocumentId() == LocalEmojiSamples.JSON_ID || span.getDocumentId() == LocalEmojiSamples.TGS_ID, "restored id");
                check(restored.getSpanEnd(span) - restored.getSpanStart(span) == 2, "restored UTF-16 range");
            }
        });
    }

    private void realViewsAndFrames() throws Exception {
        main(() -> {
            container = new FrameLayout(activity);
            ((ViewGroup) activity.findViewById(android.R.id.content)).addView(container,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            edit = new EditTextEffects(activity);
            edit.setTextSize(22);
            edit.setCursorVisible(false);
            edit.setText(LocalEmojiSamples.text("Input"));
            container.addView(edit, new FrameLayout.LayoutParams(AndroidUtilities.dp(320), AndroidUtilities.dp(100)));
            bubble = new TextBubbleCell(activity);
            bubble.setMessage(new TextBubbleMessage(99, LocalEmojiSamples.text("Bubble"), false, "12:34", TextBubbleMessage.STATE_SENT), false, false);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(AndroidUtilities.dp(320), AndroidUtilities.dp(180));
            params.topMargin = AndroidUtilities.dp(110);
            container.addView(bubble, params);
        });
        final boolean[] laidOut = {false};
        for (int attempt = 0; attempt < 50 && !laidOut[0]; attempt++) {
            main(() -> {
                try {
                    Field field = TextBubbleCell.class.getDeclaredField("textLayout");
                    field.setAccessible(true);
                    laidOut[0] = edit.isAttachedToWindow() && bubble.isAttachedToWindow()
                            && edit.getWidth() > 0 && edit.getHeight() > 0 && edit.getLayout() != null
                            && bubble.getWidth() > 0 && bubble.getHeight() > 0 && field.get(bubble) != null;
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            if (!laidOut[0]) SystemClock.sleep(100);
        }
        check(laidOut[0], "input and bubble did not lay out within five seconds");
        main(() -> {
            check(edit.isAttachedToWindow() && bubble.isAttachedToWindow(), "test views must attach");
            edit.updateAnimatedEmoji(true);
            editSpans = spans(edit.getText());
            try {
                Field field = TextBubbleCell.class.getDeclaredField("textLayout");
                field.setAccessible(true);
                Layout layout = (Layout) field.get(bubble);
                check(layout != null, "bubble must create a text layout");
                bubbleSpans = spans(layout.getText());
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            check(editSpans.length == 2 && bubbleSpans.length == 2, "input and bubble must retain both custom spans");
        });
        status("input frame and draw checks");
        assertAnimatedView(edit, editSpans, "input");
        main(() -> {
            edit.wrapCanvasToFixClipping = false;
            edit.setTextSize(28);
            AnimatedEmojiDrawable.setSourceProvider(AnimatedEmojiDrawable.getSourceProvider());
            edit.invalidate();
        });
        status("input hardware canvas and font resize checks");
        assertAnimatedView(edit, editSpans, "input without canvas wrapper");
        status("bubble frame and draw checks");
        assertAnimatedView(bubble, bubbleSpans, "bubble");
    }

    private void assertAnimatedView(View view, AnimatedEmojiSpan[] spans, String label) {
        final int[] firstFrames = {-1, -1};
        final boolean[] advanced = {false, false};
        final long[] pixels = {0};
        final boolean[] havePixels = {false};
        final boolean[] changed = {false};
        for (int attempt = 0; attempt < 35; attempt++) {
            main(() -> {
                for (int i = 0; i < spans.length; i++) {
                    AnimatedEmojiDrawable drawable = spans[i].getDrawable();
                    if (drawable != null && drawable.isLoaded()) {
                        int frame = drawable.getCurrentFrame();
                        if (firstFrames[i] < 0) firstFrames[i] = frame;
                        else if (frame != firstFrames[i]) advanced[i] = true;
                    }
                }
                if (advanced[0] && advanced[1] && view.getWidth() > 0 && view.getHeight() > 0) {
                    Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                    view.draw(new Canvas(bitmap));
                    long hash = 1;
                    for (int y = 0; y < bitmap.getHeight(); y += 2) for (int x = 0; x < bitmap.getWidth(); x += 2) hash = hash * 31 + bitmap.getPixel(x, y);
                    bitmap.recycle();
                    if (!havePixels[0]) { pixels[0] = hash; havePixels[0] = true; }
                    else if (hash != pixels[0]) changed[0] = true;
                }
            });
            if (advanced[0] && advanced[1] && changed[0]) return;
            SystemClock.sleep(180);
        }
        check(advanced[0] && advanced[1], label + " JSON and TGS frames must both advance");
        check(changed[0], label + " actual View.draw pixels must change across frames");
    }

    private void emojiOnlyAndInPlaceEdit() {
        main(() -> {
            SpannableStringBuilder text = new SpannableStringBuilder("🐥");
            AnimatedEmojiSpan original = new AnimatedEmojiSpan(LocalEmojiSamples.JSON_ID, null);
            text.setSpan(original, 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextBubbleMessage message = new TextBubbleMessage(100, text, false, "12:35", TextBubbleMessage.STATE_SENT);
            bubble.setMessage(message, false, false);
            int width = View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(320), View.MeasureSpec.EXACTLY);
            bubble.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            try {
                Field field = TextBubbleCell.class.getDeclaredField("textLayout");
                field.setAccessible(true);
                AnimatedEmojiSpan enlarged = spans(((Layout) field.get(bubble)).getText())[0];
                android.graphics.Paint.FontMetricsInt normal = org.telegram.ui.ActionBar.Theme.chat_msgTextPaint.getFontMetricsInt();
                check(enlarged.size > Math.abs(normal.ascent) + Math.abs(normal.descent), "custom-only bubble must use enlarged emoji metrics");
                original.documentId = LocalEmojiSamples.TGS_ID;
                bubble.setMessage(message, false, false);
                bubble.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                AnimatedEmojiSpan edited = spans(((Layout) field.get(bubble)).getText())[0];
                check(edited.getDocumentId() == LocalEmojiSamples.TGS_ID, "same-length in-place span edits must rebuild bubble layout");
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }

    private void providerFailures() throws Exception {
        File json = new File(activity.getFilesDir(), "emoji-duck.json");
        File tgs = new File(activity.getFilesDir(), "emoji-duck.tgs");
        check(json.isFile() && tgs.isFile(), "sample source files exist");
        File broken = File.createTempFile("emoji-broken-", ".json", activity.getCacheDir());
        try {
            try (FileOutputStream out = new FileOutputStream(broken)) { out.write("not valid lottie".getBytes("UTF-8")); }
            main(() -> {
                AnimatedEmojiDrawable.setSourceProvider(null);
                AnimatedEmojiDrawable first = editSpans[0].getDrawable();
                check(first != null && !first.isLoaded(), "missing provider must fall back");
                AnimatedEmojiDrawable.setSourceProvider(id -> broken);
                check(!first.isLoaded(), "invalid JSON must fall back");
                check(broken.isFile() && json.isFile() && tgs.isFile(), "library must not delete host source files");
            });
            LocalEmojiSamples.install(activity);
            main(() -> check(editSpans[0].getDrawable().isLoaded() && editSpans[1].getDrawable().isLoaded(), "provider restore must reload local sources"));
        } finally { broken.delete(); }
    }

    private void sharedBindings() {
        main(() -> {
            AnimatedEmojiSpan span = new AnimatedEmojiSpan(LocalEmojiSamples.JSON_ID, null);
            SpannableStringBuilder text = new SpannableStringBuilder("🐥");
            text.setSpan(span, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextPaint paint = new TextPaint();
            paint.setTextSize(AndroidUtilities.dp(20));
            Layout layout = new StaticLayout(text, paint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            View first = new View(activity), second = new View(activity);
            container.addView(first, new FrameLayout.LayoutParams(1, 1));
            container.addView(second, new FrameLayout.LayoutParams(1, 1));
            AnimatedEmojiSpan.EmojiGroupedSpans groupA = AnimatedEmojiSpan.update(0, first, null, layout);
            AnimatedEmojiSpan.EmojiGroupedSpans groupB = AnimatedEmojiSpan.update(0, second, null, layout);
            AnimatedEmojiDrawable drawable = span.getDrawable();
            check(drawable != null && drawable.isLoaded(), "shared span must load once bound");
            container.removeView(first);
            check(span.getDrawable() == drawable && drawable.isLoaded(), "removing first parent must preserve shared animation");
            container.removeView(second);
            check(span.getDrawable() == null && !drawable.isLoaded(), "last detached parent must release animation");
            container.addView(second, new FrameLayout.LayoutParams(1, 1));
            check(span.getDrawable() != null && span.getDrawable().isLoaded(), "reattach must restore animation");
            AnimatedEmojiSpan.release(first, groupA);
            AnimatedEmojiSpan.release(second, groupB);
            container.removeView(second);
        });
    }

    private void queuedFrameDisposal() throws Exception {
        Field delegateField = AnimatedEmojiDrawable.class.getDeclaredField("animation");
        Field pointerField = RLottieDrawable.class.getDeclaredField("nativePtr");
        Field secondPointerField = RLottieDrawable.class.getDeclaredField("secondNativePtr");
        delegateField.setAccessible(true);
        pointerField.setAccessible(true);
        secondPointerField.setAccessible(true);
        ArrayList<RLottieDrawable> delegates = new ArrayList<>();
        main(() -> {
            View owner = new View(activity);
            container.addView(owner, new FrameLayout.LayoutParams(1, 1));
            try {
                for (int i = 0; i < 80; i++) {
                    AnimatedEmojiDrawable drawable = new AnimatedEmojiDrawable(LocalEmojiSamples.JSON_ID, AndroidUtilities.dp(24));
                    drawable.addView(owner);
                    RLottieDrawable delegate = (RLottieDrawable) delegateField.get(drawable);
                    check(delegate != null && drawable.isLoaded(), "stress instance must load native animation");
                    delegates.add(delegate);
                    drawable.removeView(owner);
                    drawable.recycle();
                }
            } catch (IllegalAccessException e) { throw new AssertionError(e); }
            container.removeView(owner);
        });
        int pending = delegates.size();
        for (int attempt = 0; attempt < 100 && pending != 0; attempt++) {
            final int[] remaining = {0};
            main(() -> {
                try {
                    for (RLottieDrawable drawable : delegates) {
                        if (pointerField.getLong(drawable) != 0 || secondPointerField.getLong(drawable) != 0 || drawable.isRunning()) remaining[0]++;
                    }
                } catch (IllegalAccessException e) { throw new AssertionError(e); }
            });
            pending = remaining[0];
            if (pending != 0) SystemClock.sleep(100);
        }
        check(pending == 0, "native pointers or playback remained active for " + pending + " recycled animations");
    }

    private static AnimatedEmojiSpan[] spans(CharSequence text) {
        return text instanceof Spanned ? ((Spanned) text).getSpans(0, text.length(), AnimatedEmojiSpan.class) : new AnimatedEmojiSpan[0];
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
