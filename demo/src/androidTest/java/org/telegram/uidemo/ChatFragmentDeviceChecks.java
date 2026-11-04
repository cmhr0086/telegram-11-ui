package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ChatFragmentBase;
import org.telegram.ui.TelegramActivity;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.Components.ChatActivityEnterView;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Integration checks through the real activity and fragment navigation stack. */
public final class ChatFragmentDeviceChecks {
    private final Instrumentation instrumentation;
    private final TelegramActivity activity;
    private Fixture fragment;
    private ChatFragmentDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation;
        this.activity = (TelegramActivity) activity;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private void main(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { error.set(t); } });
        if (error.get() != null) throw new AssertionError("chat fragment checks", error.get());
    }
    private static void keyboardMotion(Fixture f, float translation) {
        try {
            java.lang.reflect.Method method = org.telegram.ui.Components.ChatInputLayout.class.getDeclaredMethod("setKeyboardMotion", float.class);
            method.setAccessible(true);
            method.invoke(f.contentView(), translation);
        } catch (ReflectiveOperationException e) { throw new AssertionError("setKeyboardMotion", e); }
    }
    public static void run(Instrumentation instrumentation, Activity activity) {
        ChatFragmentDeviceChecks t = new ChatFragmentDeviceChecks(instrumentation, activity);
        try {
            t.main(() -> { t.fragment = new Fixture(); check(t.activity.presentFragment(t.fragment), "present chat fragment"); });
            SystemClock.sleep(500);
            t.main(() -> {
                Fixture f = t.fragment;
                check(t.activity.getLastFragment() == f && f.resumes > 0, "chat attached and resumed");
                check(f.contentView() != null && f.list() != null && f.input() != null && f.getActionBar() != null, "assembled views");
                check(f.contentView().isAttachedToWindow() && f.list().getAdapter() != null && f.list().getAdapter().getItemCount() == 1, "adapter in real window");
                check(f.list().getMeasuredWidth() > 0 && f.list().getMeasuredHeight() > 0 && f.input().getMeasuredHeight() > 0 && f.getActionBar().getMeasuredHeight() > 0, "views laid out");
                check(f.list().getPaddingTop() == f.getActionBar().getMeasuredHeight() + AndroidUtilities.dp(4), "list below action bar");
                check(f.contentView().indexOfChild(f.getActionBar()) > f.contentView().indexOfChild(f.list()), "action bar above list");
                f.input().setSendPreviewEnabled(false);
                f.input().setFieldText("fragment message");
            });
            SystemClock.sleep(300);
            t.main(() -> {
                t.fragment.input().getSendButton().performClick();
                check(t.fragment.sends == 1 && "fragment message".contentEquals(t.fragment.sent), "input sends through host delegate");
                SpannableString draft = new SpannableString("styled draft");
                draft.setSpan(new StyleSpan(Typeface.BOLD), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                t.fragment.input().setFieldText(draft);
                t.fragment.input().getEditField().setSelection(2, 5);
                t.fragment.oldInput = t.fragment.input();
                t.fragment.oldList = t.fragment.list();
                t.activity.getActionBarLayout().rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
            });
            SystemClock.sleep(500);
            t.main(() -> {
                Fixture f = t.fragment;
                check(f.input() != f.oldInput && f.inputDestroys == 1 && f.oldList.getAdapter() == null, "rebuild destroys old input once and removes adapter");
                CharSequence restored = f.input().getFieldText();
                check("styled draft".contentEquals(restored) && restored instanceof Spanned
                        && ((Spanned) restored).getSpans(0, 6, StyleSpan.class).length == 1,
                        "rebuild restores styled draft");
                check(f.input().getEditField().getSelectionStart() == 2 && f.input().getEditField().getSelectionEnd() == 5, "rebuild restores selection");
                f.refreshColors();
                check(f.input().getEditField().isEnabled(), "theme update keeps input usable");
                f.input().setForceShowSendButton(true, false);
                f.input().addTopView(new View(t.activity), new View(t.activity), AndroidUtilities.dp(48));
                // The panel was added after the window's initial size notification. Deliver the
                // current no-IME state once more, as SizeNotifierFrameLayout does on size change;
                // the eligibility check reads the actual attached content height.
                check(f.contentView().getHeight() >= AndroidUtilities.dp(72) + org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight(), "window has room for top panel");
                f.input().onSizeChanged(0, false);
                f.input().showTopView(true, false);
            });
            SystemClock.sleep(80);
            t.main(() -> {
                Fixture f = t.fragment;
                check(f.topCallbacks > 0, "host receives top panel animation callbacks");
                check(Math.abs(f.list().getTranslationY() - f.input().getTranslationY() - f.input().getTopViewTranslation()) < .5f,
                        "list follows animated input top panel");
                float top = f.input().getTopViewTranslation();
                check(top > 0, "top panel is still animating");
                keyboardMotion(f, -AndroidUtilities.dp(24));
                check(Math.abs(f.list().getTranslationY() - (-AndroidUtilities.dp(24) + top)) < .5f,
                        "keyboard motion and top panel offsets compose");
                keyboardMotion(f, 0);
                check(Math.abs(f.list().getTranslationY() - top) < .5f, "keyboard reset retains top offset");
            });
            SystemClock.sleep(350);
            t.main(() -> {
                Fixture f = t.fragment;
                check(Math.abs(f.list().getTranslationY() - f.input().getTranslationY() - f.input().getTopViewTranslation()) < .5f,
                        "list follows top panel after animation");
                f.input().setFieldText("");
                f.input().showEmojiView();
                check(t.fragment.input().isPopupShowing(), "emoji popup opened");
                t.activity.onBackPressed();
                check(t.activity.getLastFragment() == t.fragment && !t.fragment.input().isPopupShowing(), "back closes popup before fragment");
            });
            SystemClock.sleep(450);
            t.main(() -> {
                check(t.activity.presentFragment(new BaseFragment() {
                    @Override public View createView(Context context) { fragmentView = new TextView(context); return fragmentView; }
                }), "present another fragment");
            });
            SystemClock.sleep(500);
            t.main(() -> { check(t.fragment.pauses > 0, "navigation pauses chat"); t.activity.onBackPressed(); });
            SystemClock.sleep(500);
            t.main(() -> {
                check(t.activity.getLastFragment() == t.fragment && t.fragment.resumes >= 2, "navigation resumes chat");
                t.activity.onBackPressed();
            });
            SystemClock.sleep(500);
            t.main(() -> check(t.fragment.destroyed == 1 && t.activity.getLastFragment() != t.fragment, "navigation destroys chat once"));
        } finally {
            t.main(() -> { if (t.fragment != null && t.activity.getLastFragment() == t.fragment) t.activity.onBackPressed(); });
        }
    }
    private static final class Fixture extends ChatFragmentBase {
        int resumes, pauses, destroyed, sends, inputDestroys, topCallbacks;
        ChatActivityEnterView oldInput;
        org.telegram.ui.Components.RecyclerListView oldList;
        CharSequence sent;
        @Override protected RecyclerView.Adapter createMessageAdapter(Context context) {
            return new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                    TextView text = new TextView(parent.getContext());
                    return new RecyclerView.ViewHolder(text) {};
                }
                @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) { ((TextView)holder.itemView).setText("example"); }
                @Override public int getItemCount() { return 1; }
            };
        }
        @Override protected ChatActivityEnterView.ChatActivityEnterViewDelegate createInputDelegate() {
            return new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
                @Override public void onSendText(CharSequence text, ArrayList<TLRPC.MessageEntity> entities, boolean notify, int scheduleDate) { sends++; sent = text.toString(); }
                @Override public void onTopViewTranslationChanged(float translationY) { topCallbacks++; }
            };
        }
        @Override protected ChatActivityEnterView createInputView(Context context) {
            return new ChatActivityEnterView(getParentActivity(), contentView, this, true, getResourceProvider()) {
                @Override public void onDestroy() { inputDestroys++; super.onDestroy(); }
            };
        }
        void refreshColors() { onChatColorsChanged(); }
        @Override protected void configureChat(Context context) { getActionBar().setTitle("Chat fixture"); }
        @Override public void onResume() { super.onResume(); resumes++; }
        @Override public void onPause() { super.onPause(); pauses++; }
        @Override public void onFragmentDestroy() { super.onFragmentDestroy(); destroyed++; }
        org.telegram.ui.Components.ChatInputLayout contentView() { return contentView; }
        org.telegram.ui.Components.RecyclerListView list() { return listView; }
        ChatActivityEnterView input() { return enterView; }
    }
}
