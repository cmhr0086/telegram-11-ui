package org.telegram.messenger;

// tgui stub: there are no accounts in the UI library. Index 0 is the only "account".
public class UserConfig {

    public static final int MAX_ACCOUNT_COUNT = 1;
    public static int selectedAccount = 0;

    private static final UserConfig instance = new UserConfig();

    public static UserConfig getInstance(int account) {
        return instance;
    }

    public boolean isPremium() {
        return false;
    }

    public long getClientUserId() {
        return 0;
    }

    public boolean isClientActivated() {
        return false;
    }
}
