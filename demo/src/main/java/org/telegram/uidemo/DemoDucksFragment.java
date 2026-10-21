package org.telegram.uidemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

/** The large Lottie illustrations bundled with upstream (res/raw/*.json), grouped like they are used in the app. */
public class DemoDucksFragment extends BaseFragment {

    private static class Group {
        final String title;
        /** White-only glyphs; upstream draws them on a colored circle. */
        final boolean onCircle;
        final int[] res;
        final String[] names;

        Group(String title, boolean onCircle, int[] res, String[] names) {
            this.title = title;
            this.onCircle = onCircle;
            this.res = res;
            this.names = names;
        }
    }

    private static final Group[] GROUPS = {
            new Group("Utya ducks", false, new int[]{
                    R.raw.utyan_empty, R.raw.utyan_newborn, R.raw.utyan_saved_messages, R.raw.utyan_private,
                    R.raw.utyan_passcode, R.raw.utyan_call, R.raw.utyan_cache, R.raw.utyan_change_number,
                    R.raw.utyan_gigagroup, R.raw.utyan_schedule, R.raw.utyan_streaming
            }, new String[]{
                    "utyan_empty", "utyan_newborn", "utyan_saved_messages", "utyan_private",
                    "utyan_passcode", "utyan_call", "utyan_cache", "utyan_change_number",
                    "utyan_gigagroup", "utyan_schedule", "utyan_streaming"
            }),
            new Group("Two-step verification monkey", false, new int[]{
                    R.raw.tsv_setup_monkey_idle1, R.raw.tsv_setup_monkey_idle2, R.raw.tsv_setup_monkey_tracking,
                    R.raw.tsv_setup_monkey_peek, R.raw.tsv_setup_monkey_close_and_peek,
                    R.raw.tsv_setup_monkey_close_and_peek_to_idle, R.raw.tsv_monkey_close
            }, new String[]{
                    "tsv_setup_monkey_idle1", "tsv_setup_monkey_idle2", "tsv_setup_monkey_tracking",
                    "tsv_setup_monkey_peek", "tsv_setup_monkey_close_and_peek",
                    "tsv_setup_monkey_close_and_peek_to_idle", "tsv_monkey_close"
            }),
            new Group("Setup & email", false, new int[]{
                    R.raw.tsv_setup_intro, R.raw.tsv_setup_hint, R.raw.tsv_setup_mail, R.raw.tsv_setup_email_sent,
                    R.raw.email_setup_heart, R.raw.email_check_inbox
            }, new String[]{
                    "tsv_setup_intro", "tsv_setup_hint", "tsv_setup_mail", "tsv_setup_email_sent",
                    "email_setup_heart", "email_check_inbox"
            }),
            new Group("Wallet", false, new int[]{
                    R.raw.wallet_allset, R.raw.wallet_congrats, R.raw.wallet_perfect, R.raw.wallet_science
            }, new String[]{
                    "wallet_allset", "wallet_congrats", "wallet_perfect", "wallet_science"
            }),
            new Group("Other illustrations", false, new int[]{
                    R.raw.qr_login, R.raw.code_laptop, R.raw.filter_new, R.raw.filter_no_chats,
                    R.raw.statistic_preload, R.raw.giveaway_results, R.raw.db_migration_placeholder,
                    R.raw.media_forbidden, R.raw.report_police, R.raw.import_finish, R.raw.sandclock
            }, new String[]{
                    "qr_login", "code_laptop", "filter_new", "filter_no_chats",
                    "statistic_preload", "giveaway_results", "db_migration_placeholder",
                    "media_forbidden", "report_police", "import_finish", "sandclock"
            }),
            new Group("Header icons (white, on a circle)", true, new int[]{
                    R.raw.passcode_lock, R.raw.passcode_lock_close, R.raw.payment_success, R.raw.stats,
                    R.raw.large_lastseen, R.raw.large_readtime, R.raw.large_message_lock,
                    R.raw.fragment, R.raw.fragment_phone, R.raw.fragment_username, R.raw.monetize,
                    R.raw.linkbroken, R.raw.not_available, R.raw.imported, R.raw.topics, R.raw.boosts,
                    R.raw.saved_messages, R.raw.contacts_sync_on
            }, new String[]{
                    "passcode_lock", "passcode_lock_close", "payment_success", "stats",
                    "large_lastseen", "large_readtime", "large_message_lock",
                    "fragment", "fragment_phone", "fragment_username", "monetize",
                    "linkbroken", "not_available", "imported", "topics", "boosts",
                    "saved_messages", "contacts_sync_on"
            }),
    };

    private static final int COLUMNS = 3;
    private static final int SIZE = 96;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Lottie illustrations");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = scrollView;

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(16));
        scrollView.addView(column, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(context);
        hint.setText("Tap to replay, long press to show it in an AlertDialog.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setGravity(Gravity.CENTER);
        column.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 0));

        for (Group group : GROUPS) {
            HeaderCell header = new HeaderCell(context);
            header.setText(group.title + " · " + group.res.length);
            column.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout row = null;
            for (int i = 0; i < group.res.length; i++) {
                if (i % COLUMNS == 0) {
                    row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    column.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 0));
                }
                row.addView(createItem(context, group.res[i], group.names[i], group.onCircle), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            }
            for (int i = group.res.length % COLUMNS; i != 0 && i < COLUMNS; i++) {
                row.addView(new View(context), LayoutHelper.createLinear(0, 1, 1f));
            }
        }
        return fragmentView;
    }

    private View createItem(Context context, int resId, String name, boolean onCircle) {
        FrameLayout cell = new FrameLayout(context);
        cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));

        RLottieImageView image;
        int animationSize;
        if (onCircle) {
            Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            image = new RLottieImageView(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) / 2f, circlePaint);
                    super.onDraw(canvas);
                }
            };
            animationSize = SIZE * 2 / 3;
        } else {
            image = new RLottieImageView(context);
            animationSize = SIZE;
        }
        image.setAutoRepeat(true);
        image.setAnimation(resId, animationSize, animationSize);
        image.playAnimation();
        cell.addView(image, LayoutHelper.createFrame(SIZE, SIZE, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 10, 0, 0));

        TextView label = new TextView(context);
        label.setText(name);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(label, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 4, SIZE + 14, 4, 10));

        cell.setOnClickListener(v -> {
            if (image.getAnimatedDrawable() != null) {
                image.getAnimatedDrawable().setCurrentFrame(0, false);
            }
            image.playAnimation();
        });
        cell.setOnLongClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
            if (onCircle) {
                builder.setTopAnimation(resId, 72, true, Theme.getColor(Theme.key_dialogTopBackground))
                        .setTopAnimationIsNew(true);
            } else {
                builder.setTopAnimation(resId, 110, true, Theme.getColor(Theme.key_dialogBackground));
            }
            showDialog(builder
                    .setTitle(name)
                    .setMessage("R.raw." + name)
                    .setPositiveButton("OK", null)
                    .create());
            return true;
        });
        return cell;
    }
}
