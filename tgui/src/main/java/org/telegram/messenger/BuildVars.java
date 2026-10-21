/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import org.telegram.tgui.BuildConfig;

// tgui: trimmed to the debug/log switches used by UI code.
public class BuildVars {

    public static boolean DEBUG_VERSION = BuildConfig.DEBUG;
    public static boolean LOGS_ENABLED = BuildConfig.DEBUG;
    public static boolean DEBUG_PRIVATE_VERSION = false;
}
