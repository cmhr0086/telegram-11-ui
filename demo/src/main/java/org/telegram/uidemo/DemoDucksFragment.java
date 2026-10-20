package org.telegram.uidemo;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

/** The 11 "Utya" duck Lottie animations bundled with upstream (res/raw/utyan_*.json). */
public class DemoDucksFragment extends BaseFragment {

    private static final int[] DUCKS = {
            R.raw.utyan_empty, R.raw.utyan_newborn, R.raw.utyan_saved_messages, R.raw.utyan_private,
            R.raw.utyan_passcode, R.raw.utyan_call, R.raw.utyan_cache, R.raw.utyan_change_number,
            R.raw.utyan_gigagroup, R.raw.utyan_schedule, R.raw.utyan_streaming
    };
    private static final String[] NAMES = {
            "empty", "newborn", "saved_messages", "private",
            "passcode", "call", "cache", "change_number",
            "gigagroup", "schedule", "streaming"
    };

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Utya ducks");
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
        hint.setText("Tap a duck to replay, long press to show it in an AlertDialog.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setGravity(Gravity.CENTER);
        column.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 8));

        LinearLayout row = null;
        for (int i = 0; i < DUCKS.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                column.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            row.addView(createDuck(context, DUCKS[i], NAMES[i]), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        }
        if (DUCKS.length % 2 == 1) {
            row.addView(new View(context), LayoutHelper.createLinear(0, 1, 1f));
        }
        return fragmentView;
    }

    private View createDuck(Context context, int resId, String name) {
        FrameLayout cell = new FrameLayout(context);
        cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));

        RLottieImageView duck = new RLottieImageView(context);
        duck.setAutoRepeat(true);
        duck.setAnimation(resId, 120, 120);
        duck.playAnimation();
        cell.addView(duck, LayoutHelper.createFrame(120, 120, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 12, 0, 0));

        TextView label = new TextView(context);
        label.setText(name);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        label.setGravity(Gravity.CENTER);
        cell.addView(label, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 4, 138, 4, 12));

        cell.setOnClickListener(v -> {
            if (duck.getAnimatedDrawable() != null) {
                duck.getAnimatedDrawable().setCurrentFrame(0, false);
            }
            duck.playAnimation();
        });
        cell.setOnLongClickListener(v -> {
            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTopAnimation(resId, 110, true, Theme.getColor(Theme.key_dialogTopBackground))
                    .setTopAnimationIsNew(true)
                    .setTitle(name)
                    .setMessage("R.raw." + "utyan_" + name)
                    .setPositiveButton("OK", null)
                    .create());
            return true;
        });
        return cell;
    }
}
