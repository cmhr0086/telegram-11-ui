package org.telegram.uidemo;

import android.view.View;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.TelegramUI;

import java.util.ArrayList;

/** Pick one of the built-in upstream themes (Blue, Dark Blue, Arctic Blue, Day, Night). */
public class DemoThemeFragment extends DemoListFragment {

    private final ArrayList<Theme.ThemeInfo> themes = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return "Theme";
    }

    @Override
    protected void fillItems(ArrayList<Item> items) {
        themes.clear();
        for (Theme.ThemeInfo info : Theme.themes) {
            if (info.assetName != null) {
                themes.add(info);
            }
        }
        Theme.ThemeInfo active = Theme.getActiveTheme();
        items.add(header("Built-in themes"));
        for (int i = 0; i < themes.size(); i++) {
            items.add(new Item(TYPE_RADIO, 100 + i, themes.get(i).getName()).checked(themes.get(i) == active));
        }
        items.add(info("Themes are loaded from the upstream .attheme assets; switching animates every ThemeDescription registered by the visible fragments."));
    }

    @Override
    protected void onItemClick(Item item, View view, float x, float y) {
        int index = item.id - 100;
        if (index < 0 || index >= themes.size()) {
            return;
        }
        Theme.ThemeInfo theme = themes.get(index);
        if (theme == Theme.getActiveTheme()) {
            return;
        }
        TelegramUI.applyTheme(theme, view);
        listView.postDelayed(this::updateItems, 450);
    }
}
