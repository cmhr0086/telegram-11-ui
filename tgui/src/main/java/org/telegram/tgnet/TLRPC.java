package org.telegram.tgnet;

/**
 * tgui: business-boundary stub. Only the message-entity types that the text formatting code
 * (EditTextCaption, MediaDataController.getEntities / addStyleToText, TextStyleSpan) produces and
 * reads are kept, as plain data classes without TL serialization. Hosts read them from
 * {@code ChatActivityEnterView.Delegate#onSendMessage}.
 */
public class TLRPC {

    public static abstract class TLObject {
    }

    public static abstract class MessageEntity extends TLObject {
        public int flags;
        public boolean collapsed;
        public int offset;
        public int length;
        public String url;
        public String language;
    }

    public static class TL_messageEntityTextUrl extends MessageEntity {
    }

    public static class TL_messageEntityBotCommand extends MessageEntity {
    }

    public static class TL_messageEntityEmail extends MessageEntity {
    }

    public static class TL_messageEntityPre extends MessageEntity {
    }

    public static class TL_messageEntityUnknown extends MessageEntity {
    }

    public static class TL_messageEntityUrl extends MessageEntity {
    }

    public static class TL_messageEntityItalic extends MessageEntity {
    }

    public static class TL_messageEntityMention extends MessageEntity {
    }

    public static class TL_messageEntitySpoiler extends MessageEntity {
    }

    public static class TL_messageEntityCashtag extends MessageEntity {
    }

    public static class TL_messageEntityBold extends MessageEntity {
    }

    public static class TL_messageEntityHashtag extends MessageEntity {
    }

    public static class TL_messageEntityCode extends MessageEntity {
    }

    public static class TL_messageEntityStrike extends MessageEntity {
    }

    public static class TL_messageEntityBlockquote extends MessageEntity {
    }

    public static class TL_messageEntityUnderline extends MessageEntity {
    }

    public static class TL_messageEntityBankCard extends MessageEntity {
    }

    public static class TL_messageEntityPhone extends MessageEntity {
    }

    public static class TL_messageEntityMentionName extends MessageEntity {
        public long user_id;
    }

    public static class InputUser extends TLObject {
        public long user_id;
        public long access_hash;
    }

    public static class TL_inputMessageEntityMentionName extends MessageEntity {
        public InputUser user_id;
    }

    /** Custom emoji: only the id is kept; rendering animated emoji is a later phase. */
    public static class TL_messageEntityCustomEmoji extends MessageEntity {
        public long document_id;
        public Document document;
    }

    /** Opaque placeholder for documents referenced by custom emoji; no file loading in tgui. */
    public static class Document extends TLObject {
        public long id;
        public long access_hash;
    }
}
