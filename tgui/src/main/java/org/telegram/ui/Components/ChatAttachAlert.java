/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Attachment sheet adapted from release-11.4.2-5469; host data, library media permission requests. */
public class ChatAttachAlert extends BottomSheet {
    public static final int TAB_GALLERY = 0, TAB_FILE = 1, TAB_CONTACT = 2, TAB_LOCATION = 3, TAB_POLL = 4;
    public interface SelectionListener extends Attachment.SelectionListener { }
    public interface LocationCallback { void onLocationSelected(Attachment.Location location); }
    public interface Delegate {
        default void onSend(List<Attachment> selection, CharSequence caption) { }
        default void onPermissionResult(boolean granted, boolean partial) { }
        default List<Attachment.Contact> getContacts() { return Collections.emptyList(); }
        default View createLocationView(Context context, LocationCallback callback) { return null; }
    }

    private final Activity activity;
    private final Delegate host;
    private final Theme.ResourcesProvider colors;
    private final FrameLayout panel;
    private final FrameLayout pageContainer;
    private final EditText caption;
    private final TextView send;
    private final AttachButton[] tabs = new AttachButton[5];
    private final View[] pages = new View[5];
    private final ArrayList<List<Attachment>> selections = new ArrayList<>();
    private final ChatAttachAlertPhotoLayout photo;
    private final ChatAttachAlertDocumentLayout document;
    private final ChatAttachAlertContactsLayout contacts;
    private final ChatAttachAlertLocationLayout location;
    private final ChatAttachAlertPollLayout poll;
    private int currentTab = -1;
    private int panelHeight;
    private ValueAnimator heightAnimator, transition;
    private SpringAnimation spring;
    private boolean destroyed;
    private boolean expanded, sent;
    private BottomSheet previewSheet;

    public ChatAttachAlert(Activity activity, Delegate delegate, Theme.ResourcesProvider resourcesProvider) {
        super(activity, true, resourcesProvider);
        this.activity = activity;
        this.host = delegate != null ? delegate : new Delegate() { };
        this.colors = resourcesProvider;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        for (int i = 0; i < 5; i++) selections.add(Collections.emptyList());
        photo = new ChatAttachAlertPhotoLayout(activity, colors, selectionListener(TAB_GALLERY));
        document = new ChatAttachAlertDocumentLayout(activity, colors, selectionListener(TAB_FILE));
        contacts = new ChatAttachAlertContactsLayout(activity, colors, selectionListener(TAB_CONTACT));
        location = new ChatAttachAlertLocationLayout(activity, colors, selectionListener(TAB_LOCATION));
        poll = new ChatAttachAlertPollLayout(activity, colors, selectionListener(TAB_POLL));
        pages[0] = photo; pages[1] = document; pages[2] = contacts; pages[3] = location; pages[4] = poll;
        List<Attachment.Contact> suppliedContacts = host.getContacts();
        contacts.setContacts(suppliedContacts == null ? Collections.emptyList() : suppliedContacts);
        View map = host.createLocationView(activity, location::setLocation);
        if (map != null) location.setMapView(map);

        panel = new FrameLayout(activity) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = MeasureSpec.getSize(heightSpec);
                int screen = activity.getResources().getDisplayMetrics().heightPixels;
                int maximum = Math.min(available > 0 ? available : screen, screen - AndroidUtilities.dp(24));
                if (panelHeight == 0) panelHeight = (int) (maximum * .65f);
                int height = Math.min(maximum, Math.max(AndroidUtilities.dp(240), panelHeight));
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
        };
        panel.setBackgroundColor(color(Theme.key_dialogBackground));
        setCustomView(panel);
        View handle = new View(activity);
        handle.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(2), color(Theme.key_dialogTextGray2)));
        FrameLayout handleArea = new FrameLayout(activity);
        handleArea.addView(handle, LayoutHelper.createFrame(36, 4, Gravity.CENTER));
        panel.addView(handleArea, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP));
        handleArea.setOnTouchListener(new View.OnTouchListener() {
            float downY; int start;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (heightAnimator != null) heightAnimator.cancel();
                    downY = event.getRawY(); start = panel.getHeight();
                    v.getParent().requestDisallowInterceptTouchEvent(true); return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    setPanelHeight(start + Math.round(downY - event.getRawY())); return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    int low = collapsedHeight(), high = expandedHeight();
                    setExpanded(panelHeight > (low + high) / 2); return true;
                }
                return true;
            }
        });
        HorizontalScrollView scroller = new HorizontalScrollView(activity);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(activity);
        scroller.addView(tabRow, new HorizontalScrollView.LayoutParams(-2, -1));
        panel.addView(scroller, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 84, Gravity.TOP, 0, 20, 0, 0));
        int[] labels = {R.string.ChatGallery, R.string.ChatDocument, R.string.AttachContact, R.string.ChatLocation, R.string.Poll};
        int[] icons = {R.raw.attach_gallery, R.raw.attach_file, R.raw.attach_contact, R.raw.attach_location, R.raw.attach_poll};
        int[] backgrounds = {Theme.key_chat_attachGalleryBackground, Theme.key_chat_attachFileBackground, Theme.key_chat_attachContactBackground, Theme.key_chat_attachLocationBackground, Theme.key_chat_attachPollBackground};
        int[] textColors = {Theme.key_chat_attachGalleryText, Theme.key_chat_attachFileText, Theme.key_chat_attachContactText, Theme.key_chat_attachLocationText, Theme.key_chat_attachPollText};
        int tabWidth = (int) (activity.getResources().getDisplayMetrics().widthPixels / 4.5f);
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            tabs[i] = new AttachButton(activity, labels[i], icons[i], backgrounds[i], textColors[i]);
            tabs[i].setOnClickListener(v -> selectTab(tab));
            tabRow.addView(tabs[i], new LinearLayout.LayoutParams(tabWidth, AndroidUtilities.dp(84)));
        }
        pageContainer = new FrameLayout(activity);
        panel.addView(pageContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 104, 0, 56));
        FrameLayout footer = new FrameLayout(activity);
        panel.addView(footer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.BOTTOM));
        caption = new EditText(activity);
        caption.setSingleLine(false); caption.setMaxLines(2); caption.setTextSize(16);
        caption.setHint(R.string.AddCaption); caption.setTextColor(color(Theme.key_chat_messagePanelText));
        caption.setHintTextColor(color(Theme.key_chat_messagePanelHint)); caption.setBackground(null);
        footer.addView(caption, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 16, 0, 72, 0));
        send = new TextView(activity);
        send.setGravity(Gravity.CENTER); send.setTextColor(color(Theme.key_chat_messagePanelSend));
        footer.addView(send, LayoutHelper.createFrame(72, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP));
        send.setOnClickListener(v -> showPreview());
        super.setOnDismissListener(dialog -> destroyPages());
        selectTab(TAB_GALLERY);
    }

    private SelectionListener selectionListener(int tab) {
        return items -> {
            List<Attachment> copy = items == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(items));
            selections.set(tab, copy);
            if (tab == currentTab) updateSend();
        };
    }
    private int color(int key) { return Theme.getColor(key, colors); }
    private int expandedHeight() { return Math.max(AndroidUtilities.dp(240), activity.getResources().getDisplayMetrics().heightPixels - AndroidUtilities.dp(48)); }
    private int collapsedHeight() { return Math.max(AndroidUtilities.dp(240), (int) (expandedHeight() * .65f)); }
    private void setPanelHeight(int height) { panelHeight = Math.max(collapsedHeight(), Math.min(expandedHeight(), height)); panel.requestLayout(); }
    private void animateHeight(int height) {
        if (heightAnimator != null) heightAnimator.cancel();
        heightAnimator = ValueAnimator.ofInt(panelHeight, height);
        heightAnimator.setDuration(180); heightAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        heightAnimator.addUpdateListener(a -> setPanelHeight((int) a.getAnimatedValue())); heightAnimator.start();
    }
    public void setExpanded(boolean value) {
        if (destroyed) return;
        expanded = value;
        animateHeight(value ? expandedHeight() : collapsedHeight());
    }
    public boolean isExpanded() { return expanded; }

    /** Freeze the current page selection and caption for confirmation. */
    public void showPreview() {
        if (destroyed || sent || previewSheet != null || !isShowing()) return;
        final List<Attachment> items = getSelection();
        if (items.isEmpty()) return;
        final String text = caption.getText().toString();
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(activity);
        title.setText(R.string.tgui_attachment_preview);
        title.setTextSize(20); title.setTextColor(color(Theme.key_dialogTextBlack));
        title.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(activity);
        LinearLayout rows = new LinearLayout(activity); rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        for (Attachment item : items) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(6), AndroidUtilities.dp(16), AndroidUtilities.dp(6));
            if (item.type == Attachment.Type.PHOTO || item.type == Attachment.Type.VIDEO || item.thumbnail != null) {
                AttachmentThumbnailView thumbnail = new AttachmentThumbnailView(activity);
                thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
                thumbnail.setAttachment(item);
                LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(72), AndroidUtilities.dp(72));
                thumbParams.rightMargin = AndroidUtilities.dp(12);
                row.addView(thumbnail, thumbParams);
            }
            TextView description = new TextView(activity);
            description.setText(previewDescription(item));
            description.setTextColor(color(Theme.key_dialogTextBlack)); description.setTextSize(16);
            row.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
            rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        if (!text.isEmpty()) {
            TextView captionPreview = new TextView(activity);
            captionPreview.setText(text); captionPreview.setTextColor(color(Theme.key_dialogTextBlack));
            captionPreview.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
            rows.addView(captionPreview, new LinearLayout.LayoutParams(-1, -2));
        }
        int maxHeight = Math.max(AndroidUtilities.dp(120), activity.getResources().getDisplayMetrics().heightPixels / 2);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(maxHeight, AndroidUtilities.dp(items.size() * 84 + (text.isEmpty() ? 0 : 56)))));
        TextView confirm = new TextView(activity);
        confirm.setText(R.string.Send); confirm.setGravity(Gravity.CENTER); confirm.setTextSize(16);
        confirm.setTextColor(color(Theme.key_chat_messagePanelSend));
        content.addView(confirm, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(52)));
        BottomSheet preview = new BottomSheet.Builder(activity, false, colors).setCustomView(content).create();
        previewSheet = preview;
        preview.setOnDismissListener(dialog -> { if (previewSheet == preview) previewSheet = null; });
        confirm.setOnClickListener(v -> {
            if (sent || destroyed || previewSheet != preview) return;
            sent = true;
            confirm.setEnabled(false);
            previewSheet = null;
            preview.dismiss();
            dismiss();
            host.onSend(items, text);
        });
        preview.show();
        if (!preview.isShowing() && previewSheet == preview) previewSheet = null;
    }
    private CharSequence previewDescription(Attachment item) {
        String name = item.name == null ? "" : item.name;
        switch (item.type) {
            case PHOTO: case VIDEO: return name.isEmpty() ? activity.getString(R.string.ChatGallery) : name;
            case FILE: return name.isEmpty() ? activity.getString(R.string.ChatDocument) : name;
            case CONTACT: return item.contact == null ? activity.getString(R.string.AttachContact) : item.contact.name + (item.contact.phone.isEmpty() ? "" : "\n" + item.contact.phone);
            case LOCATION: return item.location == null ? activity.getString(R.string.ChatLocation) : (item.location.title.isEmpty() ? activity.getString(R.string.ChatLocation) : item.location.title) + "\n" + item.location.latitude + ", " + item.location.longitude;
            case POLL: return item.poll == null ? activity.getString(R.string.Poll) : item.poll.question + "\n" + android.text.TextUtils.join("\n", item.poll.options);
            default: return name;
        }
    }
    public List<Attachment> getSelection() { return currentTab < 0 ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(selections.get(currentTab))); }
    public void setCaption(CharSequence text) { caption.setText(text); }
    private void updateSend() {
        int count = getSelection().size();
        send.setText(activity.getString(R.string.Send) + (count == 0 ? "" : " (" + count + ")"));
        send.setEnabled(count > 0); send.setAlpha(count > 0 ? 1f : .5f);
    }
    public void selectTab(int tab) {
        if (destroyed || tab < 0 || tab >= pages.length || tab == currentTab) return;
        if (transition != null) { transition.cancel(); transition = null; }
        if (spring != null) { spring.cancel(); spring = null; }
        View previous = currentTab < 0 ? null : pages[currentTab];
        if (previous != null) { if (isShowing()) hidePage(currentTab); previous.animate().cancel(); previous.setAlpha(1f); previous.setTranslationY(0f); }
        pageContainer.removeAllViews();
        currentTab = tab;
        for (int i = 0; i < tabs.length; i++) tabs[i].setChecked(i == tab, previous != null);
        View next = pages[tab];
        if (next.getParent() instanceof ViewGroup) ((ViewGroup) next.getParent()).removeView(next);
        pageContainer.addView(next, new FrameLayout.LayoutParams(-1, -1));
        if (isShowing()) showPage(tab);
        updateSend();
        if (previous != null) {
            next.setAlpha(0f); next.setTranslationY(AndroidUtilities.dp(78));
            transition = ValueAnimator.ofFloat(0f, 1f); transition.setDuration(180); transition.setInterpolator(CubicBezierInterpolator.DEFAULT);
            transition.addUpdateListener(a -> next.setAlpha((float) a.getAnimatedValue()));
            transition.addListener(new AnimatorListenerAdapter() {
                boolean canceled;
                @Override public void onAnimationCancel(Animator a) { canceled = true; }
                @Override public void onAnimationEnd(Animator a) {
                    transition = null;
                    if (canceled || destroyed || pages[currentTab] != next) return;
                    spring = new SpringAnimation(next, DynamicAnimation.TRANSLATION_Y, 0);
                    spring.getSpring().setDampingRatio(.75f).setStiffness(500f);
                    spring.start();
                }
            }); transition.start();
        } else { next.setAlpha(1f); next.setTranslationY(0f); }
    }
    private void showPage(int tab) {
        switch (tab) {
            case 0: photo.onShow(); if (isShowing()) photo.refresh(activity, (granted, partial) -> host.onPermissionResult(granted, partial)); break;
            case 1: document.onShow(); break;
            case 2: contacts.onShow(); break;
            case 3: location.onShow(); break;
            case 4: poll.onShow(); break;
        }
    }
    @Override public void show() {
        super.show();
        if (isShowing() && !destroyed && currentTab >= 0) showPage(currentTab);
    }
    @Override public void dismissInternal() {
        destroyPages();
        super.dismissInternal();
    }
    private void hidePage(int tab) {
        switch (tab) { case 0: photo.onHide(); break; case 1: document.onHide(); break; case 2: contacts.onHide(); break; case 3: location.onHide(); break; case 4: poll.onHide(); break; }
    }
    private void destroyPages() {
        if (destroyed) return;
        destroyed = true;
        if (previewSheet != null) { BottomSheet preview = previewSheet; previewSheet = null; preview.dismiss(); }
        if (transition != null) transition.cancel(); if (spring != null) spring.cancel(); if (heightAnimator != null) heightAnimator.cancel();
        if (currentTab >= 0) hidePage(currentTab);
        photo.onDestroy(); document.onDestroy(); contacts.onDestroy(); location.onDestroy(); poll.onDestroy();
    }
    private class AttachButton extends FrameLayout {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RLottieImageView icon;
        final TextView label;
        final int backgroundKey, textKey;
        float checkedState;
        ValueAnimator checkAnimator;
        AttachButton(Context context, int text, int resource, int background, int textColor) {
            super(context); setWillNotDraw(false); setFocusable(true);
            backgroundKey = background; textKey = textColor;
            icon = new RLottieImageView(context); icon.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            icon.setAnimation(resource, 26, 26);
            addView(icon, LayoutHelper.createFrame(32, 32, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 18, 0, 0));
            label = new TextView(context); label.setText(text); label.setMaxLines(2); label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12); label.setLineSpacing(-AndroidUtilities.dp(2), 1f);
            addView(label, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 62, 0, 0));
            setContentDescription(context.getString(text)); setChecked(false, false);
        }
        void setChecked(boolean checked, boolean animate) {
            setSelected(checked);
            if (checkAnimator != null) checkAnimator.cancel();
            if (checked && animate) { icon.setProgress(0); icon.playAnimation(); }
            if (!animate) { updateChecked(checked ? 1 : 0); return; }
            checkAnimator = ValueAnimator.ofFloat(checkedState, checked ? 1 : 0); checkAnimator.setDuration(200);
            checkAnimator.addUpdateListener(a -> updateChecked((float) a.getAnimatedValue())); checkAnimator.start();
        }
        void updateChecked(float value) {
            checkedState = value; icon.setScaleX(1f - .06f * value); icon.setScaleY(1f - .06f * value);
            label.setTextColor(ColorUtils.blendARGB(color(Theme.key_dialogTextGray2), color(textKey), value)); invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = icon.getScaleX() + .06f * checkedState, radius = AndroidUtilities.dp(23) * scale;
            float cx = icon.getLeft() + icon.getMeasuredWidth() / 2f, cy = icon.getTop() + icon.getMeasuredHeight() / 2f;
            paint.setColor(color(backgroundKey)); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(AndroidUtilities.dp(3) * scale);
            paint.setAlpha(Math.round(255 * checkedState)); canvas.drawCircle(cx, cy, radius - .5f * paint.getStrokeWidth(), paint);
            paint.setAlpha(255); paint.setStyle(Paint.Style.FILL); canvas.drawCircle(cx, cy, radius - AndroidUtilities.dp(5) * checkedState, paint);
        }
    }
}
