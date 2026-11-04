package org.telegram.uidemo;

import android.app.Activity;
import android.app.Fragment;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.Attachment;
import org.telegram.ui.Components.AttachmentAccess;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertContactsLayout;
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout;
import org.telegram.ui.Components.ChatAttachAlertLocationLayout;
import org.telegram.ui.Components.ChatAttachAlertPollLayout;
import org.telegram.ui.Components.RecyclerListView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Device UI checks. Real MediaStore access and permission dialogs are tested separately. */
public final class AttachmentDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout container;
    private ChatAttachAlert sheet;

    private AttachmentDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation; this.activity = activity;
    }

    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        AttachmentDeviceChecks test = new AttachmentDeviceChecks(instrumentation, activity);
        try {
            test.main(() -> {
                test.container = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(test.container,
                        new ViewGroup.LayoutParams(-1, -1));
            });
            test.status("poll limits and state"); test.poll();
            test.status("contact selection snapshots"); test.contacts();
            test.status("file picker delayed callback"); test.files();
            test.status("host map replacement and location"); test.location();
            test.status("sheet isolation, transitions, preview and caption"); test.sheet();
            test.status("checks complete");
        } finally {
            test.main(() -> {
                if (test.sheet != null) test.sheet.dismissInternal();
                if (test.container != null && test.container.getParent() instanceof ViewGroup)
                    ((ViewGroup) test.container.getParent()).removeView(test.container);
            });
        }
    }

    private void poll() {
        main(() -> {
            AtomicReference<List<Attachment>> selected = new AtomicReference<>(Collections.emptyList());
            ChatAttachAlertPollLayout page = new ChatAttachAlertPollLayout(activity, null, selected::set);
            mount(page); page.onShow();
            check(selected.get().isEmpty() && page.getPoll() == null, "empty poll cannot send");
            page.setQuestion("Which option?");
            page.getOptionField(0).setText("First");
            check(selected.get().isEmpty(), "one nonempty answer cannot send");
            page.getOptionField(1).setText("Second");
            check(selected.get().size() == 1 && selected.get().get(0).type == Attachment.Type.POLL,
                    "two answers create a poll selection");
            Attachment.Poll original = selected.get().get(0).poll;
            check(original.anonymous && !original.multipleChoice, "upstream default poll settings");
            page.setAnonymous(false); page.setMultipleChoice(true);
            check(!selected.get().get(0).poll.anonymous && selected.get().get(0).poll.multipleChoice,
                    "poll option toggles reach host model");
            check(original.anonymous && !original.multipleChoice, "previous poll snapshot remains unchanged");
            for (int i = 2; i < 10; i++) check(page.addOption("Option " + i), "option added at " + i);
            check(page.getOptionCount() == 10 && page.getPoll().options.size() == 10, "ten answers supported");
            check(!page.addOption("Eleventh"), "cannot exceed ten answer rows");
            page.setQuestion(repeat('Q', 300));
            page.getOptionField(0).setText(repeat('A', 120));
            check(page.getQuestionField().length() == 255 && page.getOptionField(0).length() == 100,
                    "pasted text obeys question/answer length limits");
            check(page.getPoll().question.length() == 255 && page.getPoll().options.get(0).length() == 100,
                    "boundary-length poll remains sendable");
            for (int i = 9; i >= 2; i--) check(page.removeOption(i), "remove option " + i);
            check(page.getOptionCount() == 2 && !page.removeOption(0), "two editable rows remain");
            page.getOptionField(1).setText(" \n ");
            check(selected.get().isEmpty(), "blank option disables send");
            page.getOptionField(1).setText("Second");
            page.setQuestion(" \n ");
            check(selected.get().isEmpty(), "blank question disables send");
            page.onHide(); page.onDestroy(); container.removeView(page);
        });
    }

    private void contacts() throws Exception {
        ChatAttachAlertContactsLayout[] page = new ChatAttachAlertContactsLayout[1];
        AtomicReference<List<Attachment>> selected = new AtomicReference<>(Collections.emptyList());
        Attachment.Contact a = new Attachment.Contact(1, "Alice", "100", null);
        Attachment.Contact b = new Attachment.Contact(2, "Bob", "200", null);
        main(() -> {
            page[0] = new ChatAttachAlertContactsLayout(activity, null, selected::set);
            page[0].setContacts(Arrays.asList(a, b, a));
            mount(page[0]);
            check(list(page[0]).getAdapter().getItemCount() == 2, "duplicate contact ids are deduplicated");
        });
        clickRow(listRef(page[0]), 0);
        check(selected.get().size() == 1 && selected.get().get(0).contact == a, "first contact tap preserves identity");
        List<Attachment> first = selected.get();
        clickRow(listRef(page[0]), 1);
        check(selected.get().size() == 2 && first.size() == 1, "multi-select creates a fresh snapshot");
        main(() -> {
            selected.get().clear();
            page[0].setContacts(Arrays.asList(a, b));
            check(selected.get().size() == 2, "host mutating callback list cannot mutate internal selection");
            Attachment.Contact newer = new Attachment.Contact(1, "Alice Updated", "101", null);
            page[0].setContacts(Collections.singletonList(newer));
            check(selected.get().size() == 1 && selected.get().get(0).contact == newer,
                    "snapshot refresh drops missing contacts and refreshes selected metadata");
            List<Attachment> before = selected.get();
            page[0].onDestroy();
            page[0].setContacts(Arrays.asList(a, b));
            check(selected.get() == before, "late contact snapshot after destruction is ignored");
            container.removeView(page[0]);
        });
    }

    private void files() throws Exception {
        ChatAttachAlertDocumentLayout[] page = new ChatAttachAlertDocumentLayout[1];
        AtomicReference<List<Attachment>> selected = new AtomicReference<>(Collections.emptyList());
        Attachment file = Attachment.file(5, Uri.parse("content://fixture/report.txt"), "report.txt", "text/plain", 42);
        main(() -> {
            page[0] = new ChatAttachAlertDocumentLayout(activity, null, selected::set);
            page[0].setFiles(Arrays.asList(file, file)); mount(page[0]);
            check(list(page[0]).getAdapter().getItemCount() == 1, "files deduplicate by URI");
        });
        clickRow(listRef(page[0]), 0);
        check(selected.get().size() == 1 && selected.get().get(0) == file, "file tap preserves identity");
        // Intercept the platform picker: obtain the real pending library callback,
        // destroy the page, then deliver a late successful picker result.
        IntentFilter pickerFilter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
        pickerFilter.addCategory(Intent.CATEGORY_OPENABLE);
        pickerFilter.addDataType("*/*");
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(pickerFilter,
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
        AtomicReference<AttachmentAccess.FileResult> callback = new AtomicReference<>();
        int[] callbacks = {0};
        try {
            main(() -> {
                container.removeView(page[0]); page[0].onDestroy();
                page[0] = new ChatAttachAlertDocumentLayout(activity, null, values -> callbacks[0]++);
                mount(page[0]);
                page[0].pickFiles(activity);
                if (Build.VERSION.SDK_INT >= 26) {
                    for (Fragment fragment : activity.getFragmentManager().getFragments()) {
                        if (fragment instanceof AttachmentAccess.Bridge) {
                            Object pending = field(fragment, "filesDone");
                            if (pending instanceof AttachmentAccess.FileResult) callback.set((AttachmentAccess.FileResult) pending);
                        }
                    }
                    check(callback.get() != null, "file picker callback captured before result delivery");
                }
                page[0].onDestroy();
                if (callback.get() != null) callback.get().onResult(Collections.singletonList(file));
                page[0].setFiles(Collections.singletonList(file));
            });
            SystemClock.sleep(250);
            main(() -> {
                check(callbacks[0] == 0, "destroyed file page ignores picker and setter results");
                container.removeView(page[0]);
            });
        } finally { instrumentation.removeMonitor(monitor); }
    }

    private void location() {
        main(() -> {
            AtomicReference<List<Attachment>> selected = new AtomicReference<>(Collections.emptyList());
            ChatAttachAlertLocationLayout page = new ChatAttachAlertLocationLayout(activity, null, selected::set);
            mount(page); page.onShow(); check(selected.get().isEmpty(), "unset location has no selection");
            View first = new View(activity), second = new View(activity);
            page.setMapView(first); check(first.getParent() != null, "host map is attached");
            page.setMapView(second); check(first.getParent() == null && second.getParent() != null, "map replacement detaches previous view");
            Attachment.Location location = new Attachment.Location(34.24, 108.96, "Campus", new ColorDrawable(Color.GREEN));
            page.setLocation(location);
            check(selected.get().size() == 1 && selected.get().get(0).location == location, "location result retains host identity");
            page.setMapView(null); check(second.getParent() == null, "static map fallback releases host view");
            page.setLocation(null); check(selected.get().isEmpty(), "clearing location clears selection");
            page.setMapView(second); page.setLocation(location);
            List<Attachment> before = selected.get();
            page.onDestroy(); page.setLocation(new Attachment.Location(1, 2, "Late", null));
            check(second.getParent() == null && selected.get() == before, "destroyed location page ignores late updates");
            container.removeView(page);
        });
    }

    private void sheet() throws Exception {
        AtomicReference<ChatAttachAlert.LocationCallback> locationCallback = new AtomicReference<>();
        AtomicReference<List<Attachment>> sentItems = new AtomicReference<>();
        AtomicReference<CharSequence> sentCaption = new AtomicReference<>();
        int[] sends = {0};
        main(() -> {
            container.removeAllViews();
            sheet = new ChatAttachAlert(activity, new ChatAttachAlert.Delegate() {
                @Override public View createLocationView(android.content.Context context, ChatAttachAlert.LocationCallback callback) {
                    locationCallback.set(callback); return new View(context);
                }
                @Override public void onSend(List<Attachment> items, CharSequence caption) {
                    sends[0]++; sentItems.set(items); sentCaption.set(caption);
                }
            }, null);
            // Select before show: this suite does not launch MediaStore permission dialogs.
            sheet.selectTab(ChatAttachAlert.TAB_POLL); sheet.show();
            ChatAttachAlertPollLayout poll = (ChatAttachAlertPollLayout) field(sheet, "poll");
            poll.setQuestion("Frozen question"); poll.getOptionField(0).setText("A"); poll.getOptionField(1).setText("B");
            check(sheet.getSelection().size() == 1 && sheet.getSelection().get(0).type == Attachment.Type.POLL,
                    "poll selection reaches sheet");
            locationCallback.get().onLocationSelected(new Attachment.Location(1, 2, "Map selection", null));
            check(sheet.getSelection().get(0).type == Attachment.Type.POLL,
                    "background location callback cannot replace current page selection");
            sheet.selectTab(ChatAttachAlert.TAB_LOCATION);
            check(sheet.getSelection().size() == 1 && sheet.getSelection().get(0).type == Attachment.Type.LOCATION,
                    "switching page retrieves that page selection");
            sheet.selectTab(ChatAttachAlert.TAB_FILE);
            check(sheet.getSelection().isEmpty(), "empty file page does not inherit location selection");
            sheet.selectTab(ChatAttachAlert.TAB_CONTACT); sheet.selectTab(ChatAttachAlert.TAB_LOCATION);
            sheet.selectTab(ChatAttachAlert.TAB_POLL);
            sheet.setExpanded(true); sheet.setExpanded(false); sheet.setExpanded(true);
        });
        SystemClock.sleep(1000);
        main(() -> {
            ViewGroup pages = (ViewGroup) field(sheet, "pageContainer");
            View active = pages.getChildAt(0);
            check(pages.getChildCount() == 1 && active instanceof ChatAttachAlertPollLayout,
                    "rapid switching leaves exactly the requested page attached");
            check(Math.abs(active.getAlpha() - 1f) < .01f && Math.abs(active.getTranslationY()) < 1,
                    "rapid switching settles alpha and spring translation");
            check(sheet.isExpanded(), "last expansion command wins");
            sheet.setCaption("Frozen caption"); sheet.showPreview();
            check(field(sheet, "previewSheet") instanceof BottomSheet, "preview opens for current selection");
            Object preview = field(sheet, "previewSheet"); sheet.showPreview();
            check(field(sheet, "previewSheet") == preview, "repeated preview does not stack dialogs");
            sheet.setCaption("Changed later");
            ((ChatAttachAlertPollLayout) field(sheet, "poll")).setQuestion("Changed later");
        });
        SystemClock.sleep(350);
        main(() -> {
            BottomSheet preview = (BottomSheet) field(sheet, "previewSheet");
            TextView confirm = findText(preview.getContainerView(), activity.getString(org.telegram.tgui.R.string.Send));
            check(confirm != null, "preview confirmation button exists");
            confirm.callOnClick(); confirm.callOnClick();
            check(sends[0] == 1 && "Frozen caption".contentEquals(sentCaption.get()), "confirmation sends exactly once with frozen caption");
            check(sentItems.get().size() == 1 && "Frozen question".equals(sentItems.get().get(0).poll.question),
                    "preview sends the captured selection instead of later edits");
            boolean immutable = false;
            try { sentItems.get().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            check(immutable, "send callback selection is immutable");
        });
        SystemClock.sleep(500);
        main(() -> check(!sheet.isShowing() && Boolean.TRUE.equals(field(sheet, "destroyed")), "confirmed sheet dismisses and destroys pages"));
    }

    private void mount(View view) {
        container.removeAllViews();
        int width = AndroidUtilities.dp(320), height = AndroidUtilities.dp(420);
        container.addView(view, new FrameLayout.LayoutParams(width, height));
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
    private RecyclerListView listRef(View parent) {
        AtomicReference<RecyclerListView> result = new AtomicReference<>(); main(() -> result.set(list(parent))); return result.get();
    }
    private static RecyclerListView list(View parent) {
        if (parent instanceof RecyclerListView) return (RecyclerListView) parent;
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) { RecyclerListView found = list(group.getChildAt(i)); if (found != null) return found; }
        }
        return null;
    }
    private void clickRow(RecyclerListView list, int position) throws Exception {
        main(() -> {
            org.telegram.tgui.recyclerview.RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
            check(holder != null, "list row is laid out: " + position);
            View child = holder.itemView;
            float x = child.getLeft() + child.getWidth() / 2f, y = child.getTop() + child.getHeight() / 2f;
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
            MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0);
            try { list.dispatchTouchEvent(down); list.dispatchTouchEvent(up); } finally { down.recycle(); up.recycle(); }
        });
        SystemClock.sleep(350);
    }
    private static TextView findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) { TextView found = findText(group.getChildAt(i), text); if (found != null) return found; }
        }
        return null;
    }
    private static Object field(Object target, String name) {
        try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static String repeat(char value, int count) { char[] chars = new char[count]; Arrays.fill(chars, value); return new String(chars); }
    private void status(String text) { Bundle value = new Bundle(); value.putString("stream", "\nAttachmentDeviceChecks: " + text + "\n"); instrumentation.sendStatus(1, value); }
    private void main(Runnable task) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { task.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError("attachment device check", failure.get());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
