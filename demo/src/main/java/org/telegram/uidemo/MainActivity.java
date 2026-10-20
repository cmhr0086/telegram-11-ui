package org.telegram.uidemo;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.TelegramActivity;

public class MainActivity extends TelegramActivity {

    @Override
    protected BaseFragment createRootFragment() {
        return new DemoMainFragment();
    }
}
