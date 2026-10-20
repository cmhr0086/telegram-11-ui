package org.telegram.messenger;

import org.telegram.tgnet.SerializedData;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;

import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import android.text.style.CharacterStyle;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import android.text.style.URLSpan;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import android.text.util.Linkify;










import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import org.telegram.tgnet.TLRPC;



import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import org.telegram.ui.Components.AnimatedEmojiSpan;



import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import org.telegram.ui.Components.QuoteSpan;




import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import org.telegram.ui.Components.TextStyleSpan;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import org.telegram.ui.Components.URLSpanReplacement;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import org.telegram.ui.Components.URLSpanUserMention;



import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import java.util.ArrayList;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import java.util.Collections;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import java.util.Comparator;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import java.util.List;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import java.util.regex.Matcher;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import java.util.regex.Pattern;

/**
 * tgui: business-boundary stub. Upstream MediaDataController handles stickers, drafts, recent
 * emoji and so on; only the static text-entity helpers used by the message input (formatting
 * menu, getEntities for sending, addStyleToText) are kept here, copied verbatim.
 */
public class MediaDataController {

    private static final MediaDataController instance = new MediaDataController();

    public static MediaDataController getInstance(int num) {
        return instance;
    }

    private static Comparator<TLRPC.MessageEntity> entityComparator = (entity1, entity2) -> {
        if (entity1.offset > entity2.offset) {
            return 1;
        } else if (entity1.offset < entity2.offset) {
            return -1;
        }
        return 0;
    };

    public static void sortEntities(ArrayList<TLRPC.MessageEntity> entities) {
        Collections.sort(entities, entityComparator);
    }

    private static boolean checkInclusion(int index, List<TLRPC.MessageEntity> entities, boolean end) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        int count = entities.size();
        for (int a = 0; a < count; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if ((end ? entity.offset < index : entity.offset <= index) && entity.offset + entity.length > index) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkIntersection(int start, int end, List<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        int count = entities.size();
        for (int a = 0; a < count; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if (entity.offset > start && entity.offset + entity.length <= end) {
                return true;
            }
        }
        return false;
    }

    public static void addStyleToText(TextStyleSpan span, int start, int end, Spannable editable, boolean allowIntersection) {
        try {
            CharacterStyle[] spans = editable.getSpans(start, end, CharacterStyle.class);
            if (spans != null && spans.length > 0) {
                for (int a = 0; a < spans.length; a++) {
                    CharacterStyle oldSpan = spans[a];
                    TextStyleSpan.TextStyleRun textStyleRun;
                    TextStyleSpan.TextStyleRun newStyleRun = span != null ? span.getTextStyleRun() : new TextStyleSpan.TextStyleRun();
                    if (oldSpan instanceof TextStyleSpan) {
                        TextStyleSpan textStyleSpan = (TextStyleSpan) oldSpan;
                        textStyleRun = textStyleSpan.getTextStyleRun();
                    } else if (oldSpan instanceof URLSpanReplacement) {
                        URLSpanReplacement urlSpanReplacement = (URLSpanReplacement) oldSpan;
                        textStyleRun = urlSpanReplacement.getTextStyleRun();
                        if (textStyleRun == null) {
                            textStyleRun = new TextStyleSpan.TextStyleRun();
                        }
                    } else {
                        continue;
                    }
                    if (textStyleRun == null) {
                        continue;
                    }
                    int spanStart = editable.getSpanStart(oldSpan);
                    int spanEnd = editable.getSpanEnd(oldSpan);
                    editable.removeSpan(oldSpan);
                    if (spanStart > start && end > spanEnd) {
                        editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (span != null) {
                            editable.setSpan(new TextStyleSpan(new TextStyleSpan.TextStyleRun(newStyleRun)), spanEnd, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        end = spanStart;
                    } else {
                        int startTemp = start;
                        if (spanStart <= start) {
                            if (spanStart != start) {
                                editable.setSpan(createNewSpan(oldSpan, textStyleRun, null, allowIntersection), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                            if (spanEnd > start) {
                                if (span != null) {
                                    editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), start, Math.min(spanEnd, end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                start = spanEnd;
                            }
                        }
                        if (spanEnd >= end) {
                            if (spanEnd != end) {
                                editable.setSpan(createNewSpan(oldSpan, textStyleRun, null, allowIntersection), end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                            if (end > spanStart && spanEnd <= startTemp) {
                                if (span != null) {
                                    editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), spanStart, Math.min(spanEnd, end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                end = spanStart;
                            }
                        }
                    }
                }
            }
            if (span != null && start < end && start < editable.length()) {
                editable.setSpan(span, start, Math.min(editable.length(), end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void addTextStyleRuns(ArrayList<TLRPC.MessageEntity> entities, CharSequence messageText, Spannable text) {
        addTextStyleRuns(entities, messageText, text, -1);
    }

    public static void addTextStyleRuns(ArrayList<TLRPC.MessageEntity> entities, CharSequence messageText, Spannable text, int allowedFlags) {
        for (TextStyleSpan prevSpan : text.getSpans(0, text.length(), TextStyleSpan.class))
            text.removeSpan(prevSpan);
        for (TextStyleSpan.TextStyleRun run : MediaDataController.getTextStyleRuns(entities, messageText, allowedFlags)) {
            MediaDataController.addStyleToText(new TextStyleSpan(run), run.start, run.end, text, true);
        }
    }

    public static void addAnimatedEmojiSpans(ArrayList<TLRPC.MessageEntity> entities, CharSequence messageText, Paint.FontMetricsInt fontMetricsInt) {
        if (!(messageText instanceof Spannable) || entities == null) {
            return;
        }
        Spannable spannable = (Spannable) messageText;
        AnimatedEmojiSpan[] emojiSpans = spannable.getSpans(0, spannable.length(), AnimatedEmojiSpan.class);
        for (int j = 0; j < emojiSpans.length; ++j) {
            AnimatedEmojiSpan span = emojiSpans[j];
            if (span != null) {
                spannable.removeSpan(span);
            }
        }
        for (int i = 0; i < entities.size(); ++i) {
            TLRPC.MessageEntity messageEntity = entities.get(i);
            if (messageEntity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                TLRPC.TL_messageEntityCustomEmoji entity = (TLRPC.TL_messageEntityCustomEmoji) messageEntity;

                int start = messageEntity.offset;
                int end = messageEntity.offset + messageEntity.length;
                if (start < end && end <= spannable.length()) {
                    AnimatedEmojiSpan span;
                    if (entity.document != null) {
                        span = new AnimatedEmojiSpan(entity.document, fontMetricsInt);
                    } else {
                        span = new AnimatedEmojiSpan(entity.document_id, fontMetricsInt);
                    }
                    spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
    }

    public void addStyle(int flags, int spanStart, int spanEnd, ArrayList<TLRPC.MessageEntity> entities) {
        if ((flags & TextStyleSpan.FLAG_STYLE_SPOILER) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntitySpoiler(), spanStart, spanEnd));
        if ((flags & TextStyleSpan.FLAG_STYLE_BOLD) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityBold(), spanStart, spanEnd));
        if ((flags & TextStyleSpan.FLAG_STYLE_ITALIC) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityItalic(), spanStart, spanEnd));
        if ((flags & TextStyleSpan.FLAG_STYLE_MONO) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityCode(), spanStart, spanEnd));
        if ((flags & TextStyleSpan.FLAG_STYLE_STRIKE) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityStrike(), spanStart, spanEnd));
        if ((flags & TextStyleSpan.FLAG_STYLE_UNDERLINE) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityUnderline(), spanStart, spanEnd));
    }

    public ArrayList<TLRPC.MessageEntity> getEntities(CharSequence[] message, boolean allowStrike) {
        if (message == null || message[0] == null) {
            return null;
        }
        ArrayList<TLRPC.MessageEntity> entities = null;
        int index;
        int start = -1;
        int lastIndex = 0;
        boolean isPre = false;
        final String mono = "`";
        final String pre = "```";
        while ((index = TextUtils.indexOf(message[0], !isPre ? mono : pre, lastIndex)) != -1) {
            if (start == -1) {
                isPre = message[0].length() - index > 2 && message[0].charAt(index + 1) == '`' && message[0].charAt(index + 2) == '`';
                start = index;
                lastIndex = index + (isPre ? 3 : 1);
            } else {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int a = index + (isPre ? 3 : 1); a < message[0].length(); a++) {
                    if (message[0].charAt(a) == '`') {
                        index++;
                    } else {
                        break;
                    }
                }
                lastIndex = index + (isPre ? 3 : 1);
                if (isPre) {
                    int firstChar = start > 0 ? message[0].charAt(start - 1) : 0;
                    boolean replacedFirst = firstChar == ' ' || firstChar == '\n';
                    String language = "";
                    int firstNewLine = TextUtils.indexOf(message[0], '\n', start + 3);
                    if (firstNewLine >= 0 && firstNewLine - (start + 3) > 0) {
                        language = message[0].toString().substring(start + 3, firstNewLine);
                    }
                    CharSequence startMessage = substring(message[0], 0, start - (replacedFirst ? 1 : 0));
                    int contentStart = start + 3 + language.length() + (!language.isEmpty() ? 1 : 0);
                    if (contentStart < 0 || contentStart >= message[0].length() || contentStart > index)
                        continue;
                    CharSequence content = substring(message[0], contentStart, index);
                    firstChar = index + 3 < message[0].length() ? message[0].charAt(index + 3) : 0;
                    CharSequence endMessage = substring(message[0], index + 3 + (firstChar == ' ' || firstChar == '\n' ? 1 : 0), message[0].length());
                    if (startMessage.length() != 0) {
                        startMessage = AndroidUtilities.concat(startMessage, "\n");
                    } else {
                        replacedFirst = true;
                    }
                    if (endMessage.length() > 0 && endMessage.charAt(0) != '\n') {
                        endMessage = AndroidUtilities.concat("\n", endMessage);
                    }
                    boolean contentWasTruncated = false;
                    if (content.length() > 0 && content.charAt(content.length() - 1) == '\n') {
                        contentWasTruncated = true;
                        content = substring(content, 0, content.length() - 1);
                    }
                    if (!TextUtils.isEmpty(content)) {
                        if (content.length() > 1 && content.charAt(0) == '\n') {
                            content = content.subSequence(1, content.length());
                            index--;
                        }
                        message[0] = AndroidUtilities.concat(startMessage, content, endMessage);
                        TLRPC.TL_messageEntityPre entity = new TLRPC.TL_messageEntityPre();
                        entity.offset = start + (replacedFirst ? 0 : 1);
                        entity.length = index - start - 3 - (language.length() + (!language.isEmpty() ? 1 : 0)) + (replacedFirst ? 0 : 1) - (contentWasTruncated ? 1 : 0);
                        entity.language = TextUtils.isEmpty(language) || language.trim().length() == 0 ? "" : language;
                        entities.add(entity);
                        lastIndex -= 6;
                    }
                } else {
                    if (start + 1 != index) {
                        if (message[0] instanceof Spanned && ((Spanned) message[0]).getSpans(Utilities.clamp(start, message[0].length(), 0), Utilities.clamp(start + 1, message[0].length(), 0), CodeHighlighting.Span.class).length > 0) {
                            continue;
                        }
                        message[0] = AndroidUtilities.concat(substring(message[0], 0, start), substring(message[0], start + 1, index), substring(message[0], index + 1, message[0].length()));
                        TLRPC.TL_messageEntityCode entity = new TLRPC.TL_messageEntityCode();
                        entity.offset = start;
                        entity.length = index - start - 1;
                        entities.add(entity);
                        lastIndex -= 2;
                    }
                }
                start = -1;
                isPre = false;
            }
        }
        if (start != -1 && isPre) {
            message[0] = AndroidUtilities.concat(substring(message[0], 0, start), substring(message[0], start + 2, message[0].length()));
            if (entities == null) {
                entities = new ArrayList<>();
            }
            TLRPC.TL_messageEntityCode entity = new TLRPC.TL_messageEntityCode();
            entity.offset = start;
            entity.length = 1;
            entities.add(entity);
        }

        if (message[0] instanceof Spanned) {
            Spanned spannable = (Spanned) message[0];
            TextStyleSpan[] spans = spannable.getSpans(0, message[0].length(), TextStyleSpan.class);
            if (spans != null && spans.length > 0) {
                for (int a = 0; a < spans.length; a++) {
                    TextStyleSpan span = spans[a];
                    int spanStart = spannable.getSpanStart(span);
                    int spanEnd = spannable.getSpanEnd(span);
                    if (checkInclusion(spanStart, entities, false) || checkInclusion(spanEnd, entities, true) || checkIntersection(spanStart, spanEnd, entities)) {
                        continue;
                    }
                    if (entities == null) {
                        entities = new ArrayList<>();
                    }
                    addStyle(span.getStyleFlags(), spanStart, spanEnd, entities);
                }
            }

            URLSpanUserMention[] spansMentions = spannable.getSpans(0, message[0].length(), URLSpanUserMention.class);
            if (spansMentions != null && spansMentions.length > 0) {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int b = 0; b < spansMentions.length; b++) {
                    TLRPC.TL_inputMessageEntityMentionName entity = new TLRPC.TL_inputMessageEntityMentionName();
                    entity.user_id = new TLRPC.InputUser();
                    entity.user_id.user_id = Utilities.parseLong(spansMentions[b].getURL());
                    if (entity.user_id != null) {
                        entity.offset = spannable.getSpanStart(spansMentions[b]);
                        entity.length = Math.min(spannable.getSpanEnd(spansMentions[b]), message[0].length()) - entity.offset;
                        if (message[0].charAt(entity.offset + entity.length - 1) == ' ') {
                            entity.length--;
                        }
                        entities.add(entity);
                    }
                }
            }

            URLSpanReplacement[] spansUrlReplacement = spannable.getSpans(0, message[0].length(), URLSpanReplacement.class);
            if (spansUrlReplacement != null && spansUrlReplacement.length > 0) {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int b = 0; b < spansUrlReplacement.length; b++) {
                    TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
                    entity.offset = spannable.getSpanStart(spansUrlReplacement[b]);
                    entity.length = Math.min(spannable.getSpanEnd(spansUrlReplacement[b]), message[0].length()) - entity.offset;
                    entity.url = spansUrlReplacement[b].getURL();
                    entities.add(entity);
                    TextStyleSpan.TextStyleRun style = spansUrlReplacement[b].getTextStyleRun();
                    if (style != null) {
                        addStyle(style.flags, entity.offset, entity.offset + entity.length, entities);
                    }
                }
            }

            AnimatedEmojiSpan[] animatedEmojiSpans = spannable.getSpans(0, message[0].length(), AnimatedEmojiSpan.class);
            if (animatedEmojiSpans != null && animatedEmojiSpans.length > 0) {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int b = 0; b < animatedEmojiSpans.length; ++b) {
                    AnimatedEmojiSpan span = animatedEmojiSpans[b];
                    if (span != null) {
                        try {
                            TLRPC.TL_messageEntityCustomEmoji entity = new TLRPC.TL_messageEntityCustomEmoji();
                            entity.offset = spannable.getSpanStart(span);
                            entity.length = Math.min(spannable.getSpanEnd(span), message[0].length()) - entity.offset;
                            entity.document_id = span.getDocumentId();
                            entity.document = span.document;
                            entities.add(entity);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            }

            CodeHighlighting.Span[] codeSpans = spannable.getSpans(0, message[0].length(), CodeHighlighting.Span.class);
            if (codeSpans != null && codeSpans.length > 0) {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int b = 0; b < codeSpans.length; ++b) {
                    CodeHighlighting.Span span = codeSpans[b];
                    if (span != null) {
                        try {
                            TLRPC.TL_messageEntityPre entity = new TLRPC.TL_messageEntityPre();
                            entity.offset = spannable.getSpanStart(span);
                            entity.length = Math.min(spannable.getSpanEnd(span), message[0].length()) - entity.offset;
                            entity.language = span.lng;
                            entities.add(entity);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            }

            QuoteSpan[] quoteSpans = spannable.getSpans(0, message[0].length(), QuoteSpan.class);
            if (quoteSpans != null && quoteSpans.length > 0) {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int b = 0; b < quoteSpans.length; ++b) {
                    QuoteSpan span = quoteSpans[b];
                    if (span != null) {
                        try {
                            TLRPC.TL_messageEntityBlockquote entity = new TLRPC.TL_messageEntityBlockquote();
                            entity.offset = spannable.getSpanStart(span);
                            entity.length = Math.min(spannable.getSpanEnd(span), message[0].length()) - entity.offset;
                            entity.collapsed = span.isCollapsing;
                            entities.add(entity);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            }

            if (spannable instanceof Spannable) {
                Spannable s = (Spannable) spannable;
                AndroidUtilities.addLinksSafe(s, Linkify.WEB_URLS, false, false);
                URLSpan[] spansUrl = s.getSpans(0, message[0].length(), URLSpan.class);
                if (spansUrl != null && spansUrl.length > 0) {
                    if (entities == null) {
                        entities = new ArrayList<>();
                    }
                    for (int b = 0; b < spansUrl.length; b++) {
                        if (spansUrl[b] instanceof URLSpanReplacement || spansUrl[b] instanceof URLSpanUserMention) {
                            continue;
                        }
                        TLRPC.TL_messageEntityUrl entity = new TLRPC.TL_messageEntityUrl();
                        entity.offset = spannable.getSpanStart(spansUrl[b]);
                        entity.length = Math.min(spannable.getSpanEnd(spansUrl[b]), message[0].length()) - entity.offset;
                        entity.url = spansUrl[b].getURL();
                        entities.add(entity);
                        s.removeSpan(spansUrl[b]);
                    }
                }
//
//                AndroidUtilities.doSafe(() -> Linkify.addLinks(s, Linkify.PHONE_NUMBERS));
//                spansUrl = s.getSpans(0, message[0].length(), URLSpan.class);
//                if (spansUrl != null && spansUrl.length > 0) {
//                    if (entities == null) {
//                        entities = new ArrayList<>();
//                    }
//                    for (int b = 0; b < spansUrl.length; b++) {
//                        if (spansUrl[b] instanceof URLSpanReplacement || spansUrl[b] instanceof URLSpanUserMention) {
//                            continue;
//                        }
//                        TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
//                        entity.offset = spannable.getSpanStart(spansUrl[b]);
//                        entity.length = Math.min(spannable.getSpanEnd(spansUrl[b]), message[0].length()) - entity.offset;
//                        entity.url = spansUrl[b].getURL();
//                        entities.add(entity);
//                        s.removeSpan(spansUrl[b]);
//                    }
//                }
            }
        }

        CharSequence cs = message[0];
        if (entities == null) entities = new ArrayList<>();
        cs = parsePattern(cs, BOLD_PATTERN, entities, obj -> new TLRPC.TL_messageEntityBold());
        cs = parsePattern(cs, ITALIC_PATTERN, entities, obj -> new TLRPC.TL_messageEntityItalic());
        cs = parsePattern(cs, SPOILER_PATTERN, entities, obj -> new TLRPC.TL_messageEntitySpoiler());
        if (allowStrike) {
            cs = parsePattern(cs, STRIKE_PATTERN, entities, obj -> new TLRPC.TL_messageEntityStrike());
        }

        // trim again in case some whitespace inside tags
        while (cs.length() > 0 && (cs.charAt(0) == '\n' || cs.charAt(0) == ' ')) {
            cs = cs.subSequence(1, cs.length());
            for (int i = 0; i < entities.size(); ++i) {
                TLRPC.MessageEntity entity = entities.get(i);
                if (entity.offset == 0) entity.length--;
                entity.offset = Math.max(0, entity.offset - 1);
            }
        }
        while (cs.length() > 0 && (cs.charAt(cs.length() - 1) == '\n' || cs.charAt(cs.length() - 1) == ' ')) {
            cs = cs.subSequence(0, cs.length() - 1);
            for (int i = 0; i < entities.size(); ++i) {
                TLRPC.MessageEntity entity = entities.get(i);
                if (entity.offset + entity.length > cs.length()) {
                    entity.length--;
                }
            }
        }

        message[0] = cs;

        return entities;
    }

    public static boolean entitiesEqual(TLRPC.MessageEntity entity1, TLRPC.MessageEntity entity2) {
        if (entity1.getClass() != entity2.getClass() ||
                entity1.offset != entity2.offset ||
                entity1.length != entity2.length ||
                !TextUtils.equals(entity1.url, entity2.url) ||
                !TextUtils.equals(entity1.language, entity2.language)) {
            return false;
        }
        if (entity1 instanceof TLRPC.TL_inputMessageEntityMentionName && ((TLRPC.TL_inputMessageEntityMentionName) entity1).user_id != ((TLRPC.TL_inputMessageEntityMentionName) entity2).user_id) {
            return false;
        }
        if (entity1 instanceof TLRPC.TL_messageEntityMentionName && ((TLRPC.TL_messageEntityMentionName) entity1).user_id != ((TLRPC.TL_messageEntityMentionName) entity2).user_id) {
            return false;
        }
        if (entity1 instanceof TLRPC.TL_messageEntityCustomEmoji && ((TLRPC.TL_messageEntityCustomEmoji) entity1).document_id != ((TLRPC.TL_messageEntityCustomEmoji) entity2).document_id) {
            return false;
        }

        return true;
    }

    public static boolean entitiesEqual(ArrayList<TLRPC.MessageEntity> entities1, ArrayList<TLRPC.MessageEntity> entities2) {
        if (entities1.size() != entities2.size()) {
            return false;
        }

        for (int i = 0; i < entities1.size(); i++) {
            TLRPC.MessageEntity entity1 = entities1.get(i);
            TLRPC.MessageEntity entity2 = entities2.get(i);

            if (!MediaDataController.entitiesEqual(entity1, entity2)) {
                return false;
            }
        }

        return true;
    }

    private CharSequence parsePattern(CharSequence cs, Pattern pattern, ArrayList<TLRPC.MessageEntity> entities, GenericProvider<Void, TLRPC.MessageEntity> entityProvider) {
        Matcher m = pattern.matcher(cs);
        int offset = 0;
        while (m.find()) {
            String gr = m.group(1);
            boolean allowEntity = true;
            if (cs instanceof Spannable) {
                // check if it is inside a link: do not convert __ ** to styles inside links
                URLSpan[] spansUrl = ((Spannable) cs).getSpans(m.start() - offset, m.end() - offset, URLSpan.class);
                if (spansUrl != null && spansUrl.length > 0) {
                    allowEntity = false;
                }
            }
            if (allowEntity) {
                // check if it is inside a code block: do not convert __ ** || to styles inside code
                for (int i = 0; i < entities.size(); ++i) {
                    final TLRPC.MessageEntity entity = entities.get(i);
                    if (entity instanceof TLRPC.TL_messageEntityPre || entity instanceof TLRPC.TL_messageEntityCode) {
                        if (AndroidUtilities.intersect1d(m.start() - offset, m.end() - offset, entity.offset, entity.offset + entity.length)) {
                            allowEntity = false;
                            break;
                        }
                    }
                }
            }

            if (allowEntity) {
                cs = cs.subSequence(0, m.start() - offset) + gr + cs.subSequence(m.end() - offset, cs.length());

                TLRPC.MessageEntity entity = entityProvider.provide(null);
                entity.offset = m.start() - offset;
                entity.length = gr.length();

                removeOffset4After(entity.offset, entity.offset + entity.length, entities);
                entities.add(entity);
            }

            offset += m.end() - m.start() - gr.length();
        }
        return cs;
    }

    private static CharacterStyle createNewSpan(CharacterStyle baseSpan, TextStyleSpan.TextStyleRun textStyleRun, TextStyleSpan.TextStyleRun newStyleRun, boolean allowIntersection) {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun(textStyleRun);
        if (newStyleRun != null) {
            if (allowIntersection) {
                run.merge(newStyleRun);
            } else {
                run.replace(newStyleRun);
            }
        }
        if (baseSpan instanceof TextStyleSpan) {
            return new TextStyleSpan(run);
        } else if (baseSpan instanceof URLSpanReplacement) {
            URLSpanReplacement span = (URLSpanReplacement) baseSpan;
            return new URLSpanReplacement(span.getURL(), run);
        }
        return null;
    }

    public static ArrayList<TextStyleSpan.TextStyleRun> getTextStyleRuns(ArrayList<TLRPC.MessageEntity> entities, CharSequence text, int allowedFlags) {
        ArrayList<TextStyleSpan.TextStyleRun> runs = new ArrayList<>();
        ArrayList<TLRPC.MessageEntity> entitiesCopy = new ArrayList<>(entities);

        Collections.sort(entitiesCopy, (o1, o2) -> {
            if (o1.offset > o2.offset) {
                return 1;
            } else if (o1.offset < o2.offset) {
                return -1;
            }
            return 0;
        });
        for (int a = 0, N = entitiesCopy.size(); a < N; a++) {
            TLRPC.MessageEntity entity = entitiesCopy.get(a);
            if (entity == null || entity.length <= 0 || entity.offset < 0 || entity.offset >= text.length()) {
                continue;
            } else if (entity.offset + entity.length > text.length()) {
                entity.length = text.length() - entity.offset;
            }

            if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                continue;
            }

            TextStyleSpan.TextStyleRun newRun = new TextStyleSpan.TextStyleRun();
            newRun.start = entity.offset;
            newRun.end = newRun.start + entity.length;
            TLRPC.MessageEntity urlEntity = null;
            if (entity instanceof TLRPC.TL_messageEntitySpoiler) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_SPOILER;
            } else if (entity instanceof TLRPC.TL_messageEntityStrike) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_STRIKE;
            } else if (entity instanceof TLRPC.TL_messageEntityUnderline) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_UNDERLINE;
            } else if (entity instanceof TLRPC.TL_messageEntityBold) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_BOLD;
            } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_ITALIC;
            } else if (entity instanceof TLRPC.TL_messageEntityCode || entity instanceof TLRPC.TL_messageEntityPre) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_MONO;
            } else if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_MENTION;
                newRun.urlEntity = entity;
            } else if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_MENTION;
                newRun.urlEntity = entity;
            } else {
                newRun.flags = TextStyleSpan.FLAG_STYLE_URL;
                newRun.urlEntity = entity;
            }
            if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                newRun.flags |= TextStyleSpan.FLAG_STYLE_TEXT_URL;
            }

            newRun.flags &= allowedFlags;

            for (int b = 0, N2 = runs.size(); b < N2; b++) {
                TextStyleSpan.TextStyleRun run = runs.get(b);

                if (newRun.start > run.start) {
                    if (newRun.start >= run.end) {
                        continue;
                    }

                    if (newRun.end < run.end) {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(newRun);
                        r.merge(run);
                        b++;
                        N2++;
                        runs.add(b, r);

                        r = new TextStyleSpan.TextStyleRun(run);
                        r.start = newRun.end;
                        b++;
                        N2++;
                        runs.add(b, r);
                    } else {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(newRun);
                        r.merge(run);
                        r.end = run.end;
                        b++;
                        N2++;
                        runs.add(b, r);
                    }

                    int temp = newRun.start;
                    newRun.start = run.end;
                    run.end = temp;
                } else {
                    if (run.start >= newRun.end) {
                        continue;
                    }
                    int temp = run.start;
                    if (newRun.end == run.end) {
                        run.merge(newRun);
                    } else if (newRun.end < run.end) {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(run);
                        r.merge(newRun);
                        r.end = newRun.end;
                        b++;
                        N2++;
                        runs.add(b, r);

                        run.start = newRun.end;
                    } else {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(newRun);
                        r.start = run.end;
                        b++;
                        N2++;
                        runs.add(b, r);

                        run.merge(newRun);
                    }
                    newRun.end = temp;
                }
            }
            if (newRun.start < newRun.end) {
                runs.add(newRun);
            }
        }
        return runs;
    }

    private TLRPC.MessageEntity setEntityStartEnd(TLRPC.MessageEntity entity, int spanStart, int spanEnd) {
        entity.offset = spanStart;
        entity.length = spanEnd - spanStart;
        return entity;
    }

    public CharSequence substring(CharSequence source, int start, int end) {
        if (source instanceof SpannableStringBuilder) {
            return source.subSequence(start, end);
        } else if (source instanceof SpannedString) {
            return source.subSequence(start, end);
        } else {
            return TextUtils.substring(source, start, end);
        }
    }

    private static Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*"),
            ITALIC_PATTERN = Pattern.compile("__(.+?)__"),
            SPOILER_PATTERN = Pattern.compile("\\|\\|(.+?)\\|\\|"),
            STRIKE_PATTERN = Pattern.compile("~~(.+?)~~");

    private static void removeOffset4After(int start, int end, ArrayList<TLRPC.MessageEntity> entities) {
        int count = entities.size();
        for (int a = 0; a < count; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if (entity.offset > end) {
                entity.offset -= 4;
            } else if (entity.offset > start) {
                entity.offset -= 2;
            }
        }
    }
}
