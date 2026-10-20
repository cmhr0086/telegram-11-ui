/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.View;

import org.telegram.messenger.FileLog;

// tgui: upstream routes clicks through the in-app Browser (tg:// links, Instant View...).
// Here links are opened with a plain ACTION_VIEW intent.
public class URLSpanReplacement extends URLSpan {

    private TextStyleSpan.TextStyleRun style;

    public URLSpanReplacement(String url) {
        this(url, null);
    }

    public URLSpanReplacement(String url, TextStyleSpan.TextStyleRun run) {
        super(url != null ? url.replace('‮', ' ') : url);
        style = run;
    }

    public TextStyleSpan.TextStyleRun getTextStyleRun() {
        return style;
    }

    @Override
    public void onClick(View widget) {
        openUrl(widget, getURL());
    }

    static void openUrl(View widget, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            widget.getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            FileLog.e(e);
        }
    }

    @Override
    public void updateDrawState(TextPaint p) {
        int color = p.getColor();
        super.updateDrawState(p);
        if (style != null) {
            style.applyStyle(p);
            p.setUnderlineText(p.linkColor == color);
        }
    }
}
