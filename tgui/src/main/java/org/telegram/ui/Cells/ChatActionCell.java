package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/**
 * tgui: upstream ChatActionCell reduced to the centred service pill a chat uses for date separators
 * ("Today", "Yesterday", "October 21") and other one-off service text. createLayout, onMeasure and
 * drawBackground (the per-line rounded path, the service paint with the wallpaper shader from
 * {@link Theme#applyChatServiceMessageColor} plus the darken paint) are upstream's; gifts, photos,
 * buttons and spoilers are left out.
 */
public class ChatActionCell extends View {

    private final Theme.ResourcesProvider resourcesProvider;

    private CharSequence customText;
    private long customDate;
    private StaticLayout textLayout;
    private int textWidth;
    private int textHeight;
    private int textX;
    private int textY;
    private int textXLeft;
    private int previousWidth;

    private boolean invalidatePath = true;
    private final Path backgroundPath = new Path();
    private final RectF rect = new RectF();
    private final ArrayList<Integer> lineWidths = new ArrayList<>();
    private final ArrayList<Integer> lineHeights = new ArrayList<>();
    private int backgroundLeft, backgroundRight;

    private boolean visiblePartSet;
    private float viewTop;
    private int backgroundHeight;
    private boolean floating;

    /**
     * Locale for month names. The bundled strings (Today / Yesterday / chatDate) are English, so the
     * default is Locale.US; hosts that translate them set their locale here.
     */
    public static Locale dateLocale;

    public ChatActionCell(Context context) {
        this(context, null);
    }

    public ChatActionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        Theme.createChatResources(context, false);
        NotificationCenter.listenEmojiLoading(this);
    }

    /**
     * Date separator text for {@code date} (unix seconds): "Today", "Yesterday", or upstream's
     * formatDateChat (chatDate "MMMM d" within a year, chatFullDate "MMMM d, yyyy" otherwise).
     */
    public static String formatDate(long date) {
        Calendar now = Calendar.getInstance();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date * 1000);
        int dayDiff = now.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
                ? now.get(Calendar.DAY_OF_YEAR) - calendar.get(Calendar.DAY_OF_YEAR)
                : Integer.MAX_VALUE;
        if (dayDiff == 0) {
            return LocaleController.getString(R.string.ChatDateToday);
        } else if (dayDiff == 1 || now.get(Calendar.YEAR) - calendar.get(Calendar.YEAR) == 1 && now.get(Calendar.DAY_OF_YEAR) == 1 && calendar.get(Calendar.MONTH) == Calendar.DECEMBER && calendar.get(Calendar.DAY_OF_MONTH) == 31) {
            return LocaleController.getString(R.string.ChatDateYesterday);
        }
        Locale locale = dateLocale != null ? dateLocale : Locale.US;
        boolean withinYear = Math.abs(System.currentTimeMillis() - date * 1000) < 31536000000L;
        String pattern = LocaleController.getString(withinYear ? R.string.chatDate : R.string.chatFullDate);
        return new SimpleDateFormat(pattern, locale).format(date * 1000);
    }

    /** upstream setCustomDate: the pill shows {@link #formatDate} of {@code date} (unix seconds). */
    public void setCustomDate(long date) {
        if (customDate == date && customText != null) {
            return;
        }
        customDate = date;
        setCustomText(formatDate(date));
    }

    public void setCustomText(CharSequence text) {
        if (TextUtils.equals(text, customText)) {
            return;
        }
        customText = text;
        if (getMeasuredWidth() != 0) {
            createLayout(customText, getMeasuredWidth());
            invalidate();
        }
        previousWidth = 0;
        requestLayout();
    }

    public CharSequence getCustomText() {
        return customText;
    }

    /**
     * upstream setVisiblePart: where the cell sits over the wallpaper, so the service shader lines up.
     * Without it the cell uses its position in the list plus the list's top in its parent.
     */
    public void setVisiblePart(float visibleTop, int parentH) {
        visiblePartSet = true;
        backgroundHeight = parentH;
        viewTop = visibleTop;
    }

    /** The sticky date header over the list: upstream draws it at 75% background alpha. */
    public void setFloating(boolean floating) {
        if (this.floating != floating) {
            this.floating = floating;
            invalidate();
        }
    }

    private void createLayout(CharSequence text, int width) {
        int maxWidth = width - dp(30);
        if (maxWidth < 0 || text == null) {
            return;
        }
        invalidatePath = true;
        TextPaint paint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText);
        paint.linkColor = paint.getColor();
        textLayout = new StaticLayout(text, paint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

        textHeight = 0;
        textWidth = 0;
        int linesCount = textLayout.getLineCount();
        for (int a = 0; a < linesCount; a++) {
            float lineWidth = textLayout.getLineWidth(a);
            if (lineWidth > maxWidth) {
                lineWidth = maxWidth;
            }
            textHeight = (int) Math.max(textHeight, Math.ceil(textLayout.getLineBottom(a)));
            textWidth = (int) Math.max(textWidth, Math.ceil(lineWidth));
        }
        textX = (width - textWidth) / 2;
        textY = dp(7);
        textXLeft = (width - textLayout.getWidth()) / 2;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = Math.max(dp(30), MeasureSpec.getSize(widthMeasureSpec));
        if (previousWidth != width || textLayout == null) {
            previousWidth = width;
            createLayout(customText, width);
        }
        setMeasuredDimension(width, textHeight + dp(14));
    }

    private Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (textLayout == null) {
            return;
        }
        drawBackground(canvas);
        TextPaint textPaint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText);
        if (textLayout.getPaint() != textPaint) {
            createLayout(customText, getMeasuredWidth());
        }
        canvas.save();
        canvas.translate(textXLeft, textY);
        textLayout.draw(canvas);
        canvas.restore();
    }

    private void checkLeftRightBounds() {
        backgroundLeft = (int) Math.min(backgroundLeft, rect.left);
        backgroundRight = (int) Math.max(backgroundRight, rect.right);
    }

    /** upstream drawBackground(canvas, false) for text-only cells. */
    private void drawBackground(Canvas canvas) {
        Paint backgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackground);
        Paint darkenBackgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackgroundDarken);
        if (invalidatePath) {
            invalidatePath = false;
            backgroundLeft = getWidth();
            backgroundRight = 0;
            lineWidths.clear();
            final int count = textLayout == null ? 0 : textLayout.getLineCount();
            final int corner = dp(11);
            final int cornerIn = dp(8);

            int prevLineWidth = 0;
            for (int a = 0; a < count; a++) {
                int lineWidth = (int) Math.ceil(textLayout.getLineWidth(a));
                if (a != 0) {
                    int diff = prevLineWidth - lineWidth;
                    if (diff > 0 && diff <= 1.5f * corner + cornerIn) {
                        lineWidth = prevLineWidth;
                    }
                }
                lineWidths.add(lineWidth);
                prevLineWidth = lineWidth;
            }
            for (int a = count - 2; a >= 0; a--) {
                int lineWidth = lineWidths.get(a);
                int diff = prevLineWidth - lineWidth;
                if (diff > 0 && diff <= 1.5f * corner + cornerIn) {
                    lineWidth = prevLineWidth;
                }
                lineWidths.set(a, lineWidth);
                prevLineWidth = lineWidth;
            }

            int y = dp(4);
            int x = getMeasuredWidth() / 2;
            int previousLineBottom = 0;

            final int cornerOffset = dp(3);
            final int cornerInSmall = dp(6);
            final int cornerRest = corner - cornerOffset;

            lineHeights.clear();
            backgroundPath.reset();
            backgroundPath.moveTo(x, y);

            for (int a = 0; a < count; a++) {
                int lineWidth = lineWidths.get(a);
                int lineBottom = textLayout.getLineBottom(a);
                int nextLineWidth = a < count - 1 ? lineWidths.get(a + 1) : 0;

                int height = lineBottom - previousLineBottom;
                if (a == 0 || lineWidth > prevLineWidth) {
                    height += dp(3);
                }
                if (a == count - 1 || lineWidth > nextLineWidth) {
                    height += dp(3);
                }

                previousLineBottom = lineBottom;

                float startX = x + lineWidth / 2.0f;

                int innerCornerRad;
                if (a != count - 1 && lineWidth < nextLineWidth && a != 0 && lineWidth < prevLineWidth) {
                    innerCornerRad = cornerInSmall;
                } else {
                    innerCornerRad = cornerIn;
                }

                if (a == 0 || lineWidth > prevLineWidth) {
                    rect.set(startX - cornerOffset - corner, y, startX + cornerRest, y + corner * 2);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, -90, 90);
                } else if (lineWidth < prevLineWidth) {
                    rect.set(startX + cornerRest, y, startX + cornerRest + innerCornerRad * 2, y + innerCornerRad * 2);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, -90, -90);
                }
                y += height;
                if (a != count - 1 && lineWidth < nextLineWidth) {
                    y -= dp(3);
                    height -= dp(3);
                }
                if (a != 0 && lineWidth < prevLineWidth) {
                    y -= dp(3);
                    height -= dp(3);
                }
                lineHeights.add(height);

                if (a == count - 1 || lineWidth > nextLineWidth) {
                    rect.set(startX - cornerOffset - corner, y - corner * 2, startX + cornerRest, y);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 0, 90);
                } else if (lineWidth < nextLineWidth) {
                    rect.set(startX + cornerRest, y - innerCornerRad * 2, startX + cornerRest + innerCornerRad * 2, y);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 180, -90);
                }

                prevLineWidth = lineWidth;
            }
            for (int a = count - 1; a >= 0; a--) {
                prevLineWidth = a != 0 ? lineWidths.get(a - 1) : 0;
                int lineWidth = lineWidths.get(a);
                int nextLineWidth = a != count - 1 ? lineWidths.get(a + 1) : 0;
                float startX = x - lineWidth / 2;

                int innerCornerRad;
                if (a != count - 1 && lineWidth < nextLineWidth && a != 0 && lineWidth < prevLineWidth) {
                    innerCornerRad = cornerInSmall;
                } else {
                    innerCornerRad = cornerIn;
                }

                if (a == count - 1 || lineWidth > nextLineWidth) {
                    rect.set(startX - cornerRest, y - corner * 2, startX + cornerOffset + corner, y);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 90, 90);
                } else if (lineWidth < nextLineWidth) {
                    rect.set(startX - cornerRest - innerCornerRad * 2, y - innerCornerRad * 2, startX - cornerRest, y);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 90, -90);
                }

                y -= lineHeights.get(a);

                if (a == 0 || lineWidth > prevLineWidth) {
                    rect.set(startX - cornerRest, y, startX + cornerOffset + corner, y + corner * 2);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 180, 90);
                } else if (lineWidth < prevLineWidth) {
                    rect.set(startX - cornerRest - innerCornerRad * 2, y, startX - cornerRest, y + innerCornerRad * 2);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 0, -90);
                }
            }
            backgroundPath.close();
        }
        float top = viewTop;
        int height = backgroundHeight;
        if (!visiblePartSet) {
            top = getY();
            height = getMeasuredHeight();
            if (getParent() instanceof View) {
                View parent = (View) getParent();
                top += parent.getY();
                height = parent.getMeasuredHeight();
                if (parent.getParent() instanceof ViewGroup) {
                    height = Math.max(height, ((ViewGroup) parent.getParent()).getMeasuredHeight());
                }
            }
        }
        Theme.applyServiceShaderMatrix(getMeasuredWidth(), height, 0, top + dp(4));

        int oldAlpha = -1;
        int oldAlpha2 = -1;
        if (floating) {
            oldAlpha = backgroundPaint.getAlpha();
            oldAlpha2 = darkenBackgroundPaint.getAlpha();
            backgroundPaint.setAlpha((int) (oldAlpha * .75f));
            darkenBackgroundPaint.setAlpha((int) (oldAlpha2 * .75f));
        }
        canvas.drawPath(backgroundPath, backgroundPaint);
        if (Theme.hasGradientService()) {
            canvas.drawPath(backgroundPath, darkenBackgroundPaint);
        }
        if (oldAlpha >= 0) {
            backgroundPaint.setAlpha(oldAlpha);
            darkenBackgroundPaint.setAlpha(oldAlpha2);
        }
    }
}
