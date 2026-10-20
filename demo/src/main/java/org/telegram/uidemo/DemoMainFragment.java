package org.telegram.uidemo;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.TelegramUI;

import java.util.ArrayList;

public class DemoMainFragment extends DemoListFragment {

    private static final int ROW_NIGHT = 1;
    private static final int ROW_THEME = 2;
    private static final int ROW_ALERT = 10;
    private static final int ROW_ALERT_LIST = 11;
    private static final int ROW_ALERT_INPUT = 12;
    private static final int ROW_ALERT_PROGRESS = 13;
    private static final int ROW_ALERT_DESTRUCTIVE = 14;
    private static final int ROW_ALERT_LOTTIE = 15;
    private static final int ROW_SHEET_ITEMS = 20;
    private static final int ROW_SHEET_CUSTOM = 21;
    private static final int ROW_SHEET_FRAGMENT = 22;
    private static final int ROW_CELLS = 30;
    private static final int ROW_PUSH = 31;
    private static final int ROW_DUCKS = 32;
    private static final int ROW_CHAT = 33;
    private static final int ROW_DIALOGS = 34;

    private static final int MENU_SEARCH = 1;
    private static final int MENU_NIGHT = 2;
    private static final int MENU_ABOUT = 3;

    private View nightSwitchAnchor;

    @Override
    protected CharSequence getTitle() {
        return "Telegram UI";
    }

    @Override
    protected boolean showBackButton() {
        // views can be rebuilt (theme change) while other fragments are on top, so use our own index
        return getParentLayout() != null && getParentLayout().getFragmentStack().indexOf(this) > 0;
    }

    @Override
    protected void createMenu(ActionBar actionBar) {
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem searchItem = menu.addItem(MENU_SEARCH, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onTextChanged(android.widget.EditText editText) {
                actionBar.setSubtitle(editText.length() > 0 ? "Searching: " + editText.getText() : null);
            }
        });
        searchItem.setSearchFieldHint("Search");
        ActionBarMenuItem otherItem = menu.addItem(0, R.drawable.ic_ab_other);
        otherItem.addSubItem(MENU_NIGHT, R.drawable.msg_theme, "Switch day / night");
        otherItem.addSubItem(MENU_ABOUT, R.drawable.msg_info, "About");
    }

    @Override
    protected void onMenuItemClick(int id) {
        if (id == MENU_NIGHT) {
            TelegramUI.toggleDayNight(actionBar);
        } else if (id == MENU_ABOUT) {
            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTitle("Telegram UI")
                    .setMessage("Native UI components extracted from Telegram for Android 11.4.2 (release-11.4.2-5469): ActionBar, BaseFragment navigation, AlertDialog, BottomSheet, settings cells, RecyclerListView and day/night themes.")
                    .setPositiveButton("OK", null)
                    .create());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateItems();
    }

    @Override
    protected void fillItems(ArrayList<Item> items) {
        Theme.ThemeInfo active = Theme.getActiveTheme();
        items.add(header("Appearance"));
        items.add(new Item(TYPE_CHECK, ROW_NIGHT, "Night mode").checked(active.isDark()));
        items.add(new Item(TYPE_SETTINGS, ROW_THEME, "Theme").value(active.getName()));
        items.add(shadow());

        items.add(header("AlertDialog"));
        items.add(text(ROW_ALERT, "Message with buttons", R.drawable.msg_message));
        items.add(text(ROW_ALERT_LIST, "List items with icons", R.drawable.msg_list));
        items.add(text(ROW_ALERT_INPUT, "Text input", R.drawable.msg_edit));
        items.add(text(ROW_ALERT_PROGRESS, "Loading spinner", R.drawable.msg_retry));
        items.add(text(ROW_ALERT_DESTRUCTIVE, "Destructive action", R.drawable.msg_delete));
        items.add(text(ROW_ALERT_LOTTIE, "Animated top icon (Lottie)", R.drawable.msg_folders_archive));
        items.add(shadow());

        items.add(header("BottomSheet"));
        items.add(text(ROW_SHEET_ITEMS, "Menu sheet", R.drawable.msg_share));
        items.add(text(ROW_SHEET_CUSTOM, "Custom view sheet", R.drawable.msg_stories_caption));
        items.add(text(ROW_SHEET_FRAGMENT, "Fragment as sheet", R.drawable.msg_folders));
        items.add(shadow());

        items.add(header("Navigation"));
        items.add(text(ROW_DIALOGS, "Chat list (DialogCell)", R.drawable.msg_list));
        items.add(text(ROW_CHAT, "Chat: background, bubbles, input", R.drawable.msg_msgbubble));
        items.add(text(ROW_CELLS, "Settings cells gallery", R.drawable.msg_settings));
        items.add(text(ROW_PUSH, "Push another screen", R.drawable.msg_link));
        items.add(text(ROW_DUCKS, "Utya ducks (Lottie)", R.drawable.msg_sticker));
        items.add(info("Swipe from the left edge to go back. The ⋮ menu and the Night mode switch play the circular day/night reveal."));
    }

    @Override
    protected void onItemClick(Item item, View view, float x, float y) {
        switch (item.id) {
            case ROW_NIGHT:
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(!Theme.getActiveTheme().isDark());
                }
                TelegramUI.toggleDayNight(view);
                break;
            case ROW_THEME:
                presentFragment(new DemoThemeFragment());
                break;
            case ROW_ALERT:
                showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle("Discard changes?")
                        .setMessage("This is the upstream AlertDialog with its original layout, animations and theme colors.")
                        .setPositiveButton("Discard", (dialog, which) -> toast("Discarded"))
                        .setNegativeButton("Cancel", null)
                        .create());
                break;
            case ROW_ALERT_LIST:
                showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle("Share via")
                        .setItems(new CharSequence[]{"Copy link", "Share", "Edit"}, new int[]{R.drawable.msg_copy, R.drawable.msg_share, R.drawable.msg_edit}, (dialog, which) -> toast("Item " + which))
                        .create());
                break;
            case ROW_ALERT_INPUT:
                showInputDialog();
                break;
            case ROW_ALERT_LOTTIE:
                showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTopAnimation(R.raw.chats_archive, 72, false, Theme.getColor(Theme.key_dialogTopBackground))
                        .setTitle("Archive chat?")
                        .setMessage("The top icon is a Lottie animation rendered by the bundled rlottie (libtguilottie.so).")
                        .setPositiveButton("Archive", (dialog, which) -> toast("Archived"))
                        .setNegativeButton("Cancel", null)
                        .create());
                break;
            case ROW_ALERT_PROGRESS: {
                AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                progress.setCanCancel(true);
                showDialog(progress);
                AndroidUtilities.runOnUIThread(progress::dismiss, 1500);
                break;
            }
            case ROW_ALERT_DESTRUCTIVE: {
                AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle("Delete item")
                        .setMessage("Are you sure you want to delete this item?")
                        .setPositiveButton("Delete", (d, which) -> toast("Deleted"))
                        .setNegativeButton("Cancel", null)
                        .create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
                break;
            }
            case ROW_SHEET_ITEMS: {
                BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
                builder.setTitle("Share", true);
                builder.setItems(new CharSequence[]{"Copy link", "Share to app", "Edit", "Delete"},
                        new int[]{R.drawable.msg_copy, R.drawable.msg_share, R.drawable.msg_edit, R.drawable.msg_delete},
                        (dialog, which) -> toast("Sheet item " + which));
                showDialog(builder.create());
                break;
            }
            case ROW_SHEET_CUSTOM:
                showCustomSheet();
                break;
            case ROW_SHEET_FRAGMENT:
                showAsSheet(new DemoCellsFragment());
                break;
            case ROW_CELLS:
                presentFragment(new DemoCellsFragment());
                break;
            case ROW_PUSH:
                presentFragment(new DemoMainFragment());
                break;
            case ROW_DUCKS:
                presentFragment(new DemoDucksFragment());
                break;
            case ROW_CHAT:
                presentFragment(new DemoChatFragment());
                break;
            case ROW_DIALOGS:
                presentFragment(new DemoDialogsFragment());
                break;
        }
    }

    private void showInputDialog() {
        Context context = getParentActivity();
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setHint("Your name");
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        editText.setBackground(null);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Theme.getColor(Theme.key_text_RedRegular));
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setPadding(0, 0, 0, AndroidUtilities.dp(6));

        FrameLayout container = new FrameLayout(context);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP, 24, 6, 24, 0));

        AlertDialog dialog = new AlertDialog.Builder(context, getResourceProvider())
                .setTitle("Rename")
                .setView(container)
                .setPositiveButton("Save", (d, which) -> toast("Hello, " + editText.getText()))
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
        showDialog(dialog);
    }

    private void showCustomSheet() {
        Context context = getParentActivity();
        BottomSheet sheet = new BottomSheet(context, false, getResourceProvider());
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView title = new TextView(context);
        title.setText("Custom sheet");
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView body = new TextView(context);
        body.setText("BottomSheet hosts any view: it keeps upstream's drag-to-dismiss, nested scrolling, keyboard handling and navigation bar coloring.");
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        body.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        layout.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 16));

        TextView button = new TextView(context);
        button.setText("Got it");
        button.setGravity(Gravity.CENTER);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        button.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8));
        button.setOnClickListener(v -> sheet.dismiss());
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        sheet.setCustomView(layout);
        showDialog(sheet);
    }

    private void toast(CharSequence text) {
        Toast.makeText(getParentActivity(), text, Toast.LENGTH_SHORT).show();
    }
}
