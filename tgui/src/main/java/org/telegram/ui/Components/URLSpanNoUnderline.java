package org.telegram.ui.Components;

import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.View;

// tgui: links open with ACTION_VIEW (no in-app Browser); "@name" opens https://t.me/name.
public class URLSpanNoUnderline extends URLSpan {

    private boolean forceNoUnderline = false;
    private TextStyleSpan.TextStyleRun style;
    private Object object;

    // Used to label video timestamps
    public String label;

    public URLSpanNoUnderline(String url) {
        this(url, null);
    }

    public URLSpanNoUnderline(String url, boolean forceNoUnderline) {
        this(url, null);
        this.forceNoUnderline = forceNoUnderline;
    }

    public URLSpanNoUnderline(String url, TextStyleSpan.TextStyleRun run) {
        super(url != null ? url.replace('‮', ' ') : url);
        style = run;
    }

    @Override
    public void onClick(View widget) {
        String url = getURL();
        if (url.startsWith("@")) {
            URLSpanReplacement.openUrl(widget, "https://t.me/" + url.substring(1));
        } else {
            URLSpanReplacement.openUrl(widget, url);
        }
    }

    @Override
    public void updateDrawState(TextPaint p) {
        int l = p.linkColor;
        int c = p.getColor();
        super.updateDrawState(p);
        if (style != null) {
            style.applyStyle(p);
        }
        p.setUnderlineText(l == c && !forceNoUnderline);
    }

    public void setObject(Object spanObject) {
        this.object = spanObject;
    }

    public Object getObject() {
        return object;
    }
}
