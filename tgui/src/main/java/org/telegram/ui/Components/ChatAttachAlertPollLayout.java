package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordinary poll composer extracted from release-11.4.2-5469.
 * Keeps the source's question/answer limits (124-125), non-empty answer validation
 * (687-716), HeaderCell/TextCell/TextCheckCell rows (1339-1407,1474), and ordinary
 * PollEditTextCell text/delete geometry (133-161,180-183,216-250).
 * TL serialization, account/Premium state, quiz, emoji panels and server sending
 * are replaced by an immutable Attachment.Poll delivered to the sheet.
 */
public class ChatAttachAlertPollLayout extends FrameLayout {
    public static final int MAX_QUESTION_LENGTH = 255;
    public static final int MAX_ANSWER_LENGTH = 100;
    public static final int MAX_ANSWERS = 10;
    private final Theme.ResourcesProvider colors;
    private final Attachment.SelectionListener listener;
    private final ScrollView scrollView;
    private final LinearLayout answerContainer;
    private final EditTextBoldCursor question;
    private final ArrayList<AnswerRow> answers = new ArrayList<>();
    private final TextCell addOption;
    private final TextCheckCell anonymousCell, multipleCell;
    private boolean anonymous = true;
    private boolean multipleChoice;
    private boolean destroyed;
    private boolean updating;

    public ChatAttachAlertPollLayout(Context context, Theme.ResourcesProvider resourcesProvider,
                                     Attachment.SelectionListener selectionListener) {
        super(context);
        colors = resourcesProvider;
        listener = selectionListener;
        setBackgroundColor(color(Theme.key_windowBackgroundGray));
        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        addView(scrollView, LayoutHelper.createFrame(-1, -1));
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(rows, new ScrollView.LayoutParams(-1, -2));
        rows.addView(header(R.string.PollQuestion));
        FrameLayout questionRow = new FrameLayout(context);
        questionRow.setMinimumHeight(AndroidUtilities.dp(50));
        questionRow.setBackgroundColor(color(Theme.key_windowBackgroundWhite));
        question = edit(context, R.string.QuestionHint, MAX_QUESTION_LENGTH);
        questionRow.addView(question, LayoutHelper.createFrame(-1, -2, Gravity.CENTER_VERTICAL | Gravity.LEFT, 19, 0, 19, 0));
        rows.addView(questionRow, new LinearLayout.LayoutParams(-1, -2));
        rows.addView(header(R.string.AnswerOptions));
        answerContainer = new LinearLayout(context);
        answerContainer.setOrientation(LinearLayout.VERTICAL);
        rows.addView(answerContainer, new LinearLayout.LayoutParams(-1, -2));
        addOption = new TextCell(context, resourcesProvider);
        addOption.setBackgroundColor(color(Theme.key_windowBackgroundWhite));
        addOption.setColors(-1, Theme.key_windowBackgroundWhiteBlueText4);
        Drawable circle = context.getResources().getDrawable(R.drawable.poll_add_circle).mutate();
        Drawable plus = context.getResources().getDrawable(R.drawable.poll_add_plus).mutate();
        circle.setColorFilter(new PorterDuffColorFilter(color(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
        plus.setColorFilter(new PorterDuffColorFilter(color(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
        addOption.setTextAndIcon(context.getString(R.string.AddAnOption), new CombinedDrawable(circle, plus), false);
        rows.addView(addOption, new LinearLayout.LayoutParams(-1, -2));
        addOption.setOnClickListener(v -> {
            if (addOption("") && !answers.isEmpty()) {
                EditTextBoldCursor field = answers.get(answers.size() - 1).field;
                field.requestFocus();
                AndroidUtilities.showKeyboard(field);
            }
        });
        rows.addView(header(R.string.Settings));
        anonymousCell = new TextCheckCell(context, resourcesProvider);
        anonymousCell.setBackgroundColor(color(Theme.key_windowBackgroundWhite));
        anonymousCell.setTextAndCheck(context.getString(R.string.PollAnonymous), true, true);
        anonymousCell.setOnClickListener(v -> setAnonymous(!anonymous));
        rows.addView(anonymousCell, new LinearLayout.LayoutParams(-1, -2));
        multipleCell = new TextCheckCell(context, resourcesProvider);
        multipleCell.setBackgroundColor(color(Theme.key_windowBackgroundWhite));
        multipleCell.setTextAndCheck(context.getString(R.string.PollMultiple), false, false);
        multipleCell.setOnClickListener(v -> setMultipleChoice(!multipleChoice));
        rows.addView(multipleCell, new LinearLayout.LayoutParams(-1, -2));
        updating = true;
        addOption(""); addOption("");
        updating = false;
        question.addTextChangedListener(watcher());
    }

    private HeaderCell header(int text) {
        HeaderCell cell = new HeaderCell(getContext(), Theme.key_windowBackgroundWhiteBlueHeader, 21, 15, false, colors);
        cell.setBackgroundColor(color(Theme.key_windowBackgroundWhite));
        cell.setText(getContext().getString(text));
        return cell;
    }

    private EditTextBoldCursor edit(Context context, int hint, int limit) {
        EditTextBoldCursor field = new EditTextBoldCursor(context);
        field.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(color(Theme.key_windowBackgroundWhiteHintText));
        field.setCursorColor(color(Theme.key_windowBackgroundWhiteBlackText));
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        field.setBackgroundDrawable(null);
        field.setImeOptions(field.getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setInputType(field.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(11));
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        field.setHint(hint);
        return field;
    }

    private TextWatcher watcher() {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { publish(); }
        };
    }

    public EditTextBoldCursor getQuestionField() { return question; }
    public EditTextBoldCursor getOptionField(int index) { return answers.get(index).field; }
    public int getOptionCount() { return answers.size(); }
    public void setQuestion(CharSequence value) { if (!destroyed) question.setText(value); }
    public void setAnonymous(boolean value) {
        if (destroyed) return;
        anonymous = value; anonymousCell.setChecked(value); publish();
    }
    public void setMultipleChoice(boolean value) {
        if (destroyed) return;
        multipleChoice = value; multipleCell.setChecked(value); publish();
    }
    public boolean addOption(CharSequence value) {
        if (destroyed || answers.size() >= MAX_ANSWERS) return false;
        AnswerRow row = new AnswerRow(getContext());
        row.field.setText(value);
        answers.add(row);
        answerContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
        row.field.addTextChangedListener(watcher());
        updateOptionActions(); publish();
        return true;
    }
    public boolean removeOption(int index) {
        if (destroyed || index < 0 || index >= answers.size() || answers.size() <= 2) return false;
        AnswerRow row = answers.remove(index);
        if (row.field.hasFocus()) AndroidUtilities.hideKeyboard(row.field);
        answerContainer.removeView(row);
        updateOptionActions(); publish();
        return true;
    }
    private void updateOptionActions() {
        addOption.setVisibility(answers.size() < MAX_ANSWERS ? VISIBLE : GONE);
        for (AnswerRow row : answers) row.remove.setVisibility(answers.size() > 2 ? VISIBLE : INVISIBLE);
    }
    public Attachment.Poll getPoll() {
        String text = question.getText().toString().trim();
        if (text.isEmpty() || text.length() > MAX_QUESTION_LENGTH) return null;
        List<String> options = new ArrayList<>();
        for (AnswerRow row : answers) {
            String value = row.field.getText().toString().trim();
            if (value.length() > MAX_ANSWER_LENGTH) return null;
            if (!value.isEmpty()) options.add(value);
        }
        return options.size() < 2 ? null : new Attachment.Poll(text, options, anonymous, multipleChoice);
    }
    private void publish() {
        if (destroyed || updating || listener == null) return;
        Attachment.Poll poll = getPoll();
        listener.onSelectionChanged(poll == null ? Collections.emptyList() : Collections.singletonList(Attachment.poll(poll)));
    }
    public void onShow() { if (!destroyed) publish(); }
    public void onHide() {
        View focused = findFocus();
        if (focused != null) { AndroidUtilities.hideKeyboard(focused); focused.clearFocus(); }
    }
    public void onDestroy() { onHide(); destroyed = true; }
    private int color(int key) { return Theme.getColor(key, colors); }

    /** PollEditTextCell ordinary field/delete branch; account emoji controls and reorder are omitted. */
    private final class AnswerRow extends FrameLayout {
        final EditTextBoldCursor field;
        final ImageView remove;
        AnswerRow(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(color(Theme.key_windowBackgroundWhite));
            setMinimumHeight(AndroidUtilities.dp(50));
            field = edit(context, R.string.OptionHint, MAX_ANSWER_LENGTH);
            addView(field, LayoutHelper.createFrame(-1, -2, Gravity.CENTER_VERTICAL | Gravity.LEFT, 19, 0, 58, 0));
            remove = new ImageView(context);
            remove.setFocusable(false);
            remove.setScaleType(ImageView.ScaleType.CENTER);
            remove.setBackgroundDrawable(Theme.createSelectorDrawable(color(Theme.key_stickers_menuSelector)));
            remove.setImageResource(R.drawable.poll_remove);
            remove.setColorFilter(new PorterDuffColorFilter(color(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            remove.setContentDescription(context.getString(R.string.Delete));
            remove.setOnClickListener(v -> removeOption(answers.indexOf(this)));
            addView(remove, LayoutHelper.createFrame(48, 50, Gravity.RIGHT | Gravity.TOP, 0, 0, 3, 0));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Theme.dividerPaint.setColor(color(Theme.key_divider));
            canvas.drawLine(AndroidUtilities.dp(20), getHeight() - 1, getWidth(), getHeight() - 1, Theme.dividerPaint);
        }
    }
}
