/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.ActivityNotFoundException;
import org.telegram.messenger.FileLog;
import android.content.Intent;
import android.text.style.URLSpan;
import android.net.Uri;
import android.view.View;

// tgui: upstream routes clicks through the in-app Browser (tg:// links, Instant View...).
// Here links are opened with a plain ACTION_VIEW intent.
public class URLSpanReplacement extends URLSpan {

    public URLSpanReplacement(String url) {
        super(url != null ? url.replace('‮', ' ') : url);
    }

    @Override
    public void onClick(View widget) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getURL()));
            widget.getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            FileLog.e(e);
        }
    }
}
