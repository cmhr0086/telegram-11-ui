package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.UndoView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;

/** Device-side regression checks, runnable without an AndroidX test runner. */
public class RoadmapInstrumentation extends Instrumentation {
    private Bundle arguments;
    private Activity activity;
    private FrameLayout container;
    private UndoView view;
    private final int[] counts = new int[6];

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? new Bundle() : new Bundle(arguments);
        start();
    }

    @Override public void onStart() {
        new Thread(() -> {
            Bundle result = new Bundle();
            try {
                Intent intent = new Intent(getTargetContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity = startActivitySync(intent);
                waitForIdleSync();
                testTimeoutWithoutDraw();
                testReplacement();
                testUndoClick();
                testReentrantReplacement();
                testDetachPending();
                testDetachDuringExit();
                testNarrowLayout();
                main(() -> {
                    if (container != null && container.getParent() instanceof ViewGroup) {
                        ((ViewGroup) container.getParent()).removeView(container);
                    }
                    activity.finish();
                });
                result.putString("stream", "\nRoadmapInstrumentation: 7 checks passed\n");
                finish(Activity.RESULT_OK, result);
            } catch (Throwable failure) {
                StringWriter trace = new StringWriter();
                failure.printStackTrace(new PrintWriter(trace));
                result.putString("stream", "\nRoadmapInstrumentation failed:\n" + trace);
                result.putString("error", trace.toString());
                finish(Activity.RESULT_CANCELED, result);
            }
        }, "roadmap-instrumentation").start();
    }

    private void main(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> {
            try { action.run(); } catch (Throwable t) { failure.set(t); }
        });
        if (failure.get() != null) throw new AssertionError("main thread check failed", failure.get());
    }

    private void fixture() {
        main(() -> {
            if (container != null && container.getParent() instanceof ViewGroup) {
                ((ViewGroup) container.getParent()).removeView(container);
            }
            for (int i = 0; i < counts.length; i++) counts[i] = 0;
            container = new FrameLayout(activity);
            ((ViewGroup) activity.findViewById(android.R.id.content)).addView(container,
                    new ViewGroup.LayoutParams(AndroidUtilities.dp(240), AndroidUtilities.dp(300)));
            view = new UndoView(activity);
            container.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            check(view.isAttachedToWindow(), "fixture must be attached");
        });
    }

    private void testTimeoutWithoutDraw() {
        fixture();
        main(() -> {
            container.setVisibility(View.INVISIBLE);
            view.showWithAction(UndoView.ACTION_DELETE, null, () -> counts[0]++, () -> counts[1]++);
        });
        SystemClock.sleep(5600);
        main(() -> {
            check(counts[0] == 1 && counts[1] == 0, "invisible timeout must apply exactly once");
            check(!view.hasPendingAction(), "timeout must clear pending action");
            view.hide(true, 0);
            check(counts[0] == 1, "timeout must not reapply");
        });
    }

    private void testReplacement() {
        fixture();
        main(() -> {
            view.showWithAction(UndoView.ACTION_DELETE, null, () -> counts[0]++, () -> counts[1]++);
            view.showWithAction(UndoView.ACTION_DELETE_FEW, null, () -> counts[2]++, () -> counts[3]++);
            check(counts[0] == 1 && counts[1] == 0, "replacement must apply old action once");
            check(view.hasPendingAction(), "replacement must remain pending");
            view.hide(true, 0);
            view.hide(true, 0);
            check(counts[0] == 1 && counts[2] == 1 && counts[3] == 0, "replacement callbacks must be exactly once");
        });
    }

    private void testUndoClick() {
        fixture();
        main(() -> {
            view.showWithAction(UndoView.ACTION_DELETE, null, () -> counts[0]++, () -> counts[1]++);
            view.getUndoButton().performClick();
            view.getUndoButton().performClick();
            view.hide(true, 0);
            check(counts[0] == 0 && counts[1] == 1 && !view.hasPendingAction(), "click must cancel exactly once");
        });
    }

    private void testReentrantReplacement() {
        fixture();
        main(() -> {
            Runnable showC = () -> {
                counts[0]++;
                view.showWithAction(UndoView.ACTION_DELETE, "C", () -> counts[2]++, () -> counts[3]++);
            };
            view.showWithAction(UndoView.ACTION_DELETE, "A", showC, () -> counts[4]++);
            view.showWithAction(UndoView.ACTION_DELETE, "B", () -> counts[1]++, () -> counts[5]++);
            check(counts[0] == 1 && counts[1] == 1 && view.hasPendingAction(), "A and superseded B must apply; C remains pending");
            view.getUndoButton().performClick();
            check(counts[2] == 0 && counts[3] == 1 && counts[4] == 0 && counts[5] == 0,
                    "only C must receive undo");
        });
    }

    private void testDetachPending() {
        fixture();
        main(() -> {
            view.showWithAction(UndoView.ACTION_DELETE, null, () -> counts[0]++, () -> counts[1]++);
            container.removeView(view);
            check(counts[0] == 1 && counts[1] == 0 && !view.hasPendingAction(), "detach must apply pending action once");
            view.hide(true, 0);
            check(counts[0] == 1, "detached action must not apply twice");
        });
    }

    private void testDetachDuringExit() {
        fixture();
        main(() -> {
            view.showWithAction(UndoView.ACTION_DELETE, null, () -> counts[0]++, () -> counts[1]++);
            view.hide(false, 1);
            container.removeView(view);
            container.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            check(view.getVisibility() != View.VISIBLE && !view.hasPendingAction(), "reattached view must not leave a visible exiting prompt");
            check(counts[0] == 0 && counts[1] == 1, "detach during exit must not change callback outcome");
        });
    }

    private void testNarrowLayout() {
        fixture();
        main(() -> {
            view.showWithAction(UndoView.ACTION_DELETE,
                    "A long host supplied message that should wrap without covering the undo button on a narrow device", null, null);
            int width = AndroidUtilities.dp(240);
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            view.layout(0, 0, width, view.getMeasuredHeight());
            View text = view.getChildAt(0);
            View button = view.getUndoButton();
            check(text.getWidth() > 0 && button.getWidth() > 0, "layout must measure text and button");
            check(text.getRight() <= button.getLeft(), "long text overlaps Undo: text right=" + text.getRight() + ", button left=" + button.getLeft());
            view.hide(true, 0);
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
