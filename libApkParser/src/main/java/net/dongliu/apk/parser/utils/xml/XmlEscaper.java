package net.dongliu.apk.parser.utils.xml;

import net.dongliu.apk.parser.utils.*;

/**
 * Utils method to escape xml string, copied from apache commons lang3
 *
 * @author Liu Dong {@literal <dongliu@live.cn>}
 */
public class XmlEscaper {

    /**
     * <p>Escapes the characters in a {@code String} using XML entities.</p>
     */
    public static String escapeXml10(final String input) {
        return ESCAPE_XML10.translate(input);
    }

    public static final CharSequenceTranslator ESCAPE_XML10 =
            new AggregateTranslator(
                    new LookupTranslator(EntityArrays.BASIC_ESCAPE()),
                    new LookupTranslator(EntityArrays.APOS_ESCAPE()),
                    new LookupTranslator(
                            new String[][]{
                                    {"\u0000", ""},
                                    {"\u0001", ""},
                                    {"\u0002", ""},
                                    {"\u0003", ""},
                                    {"\u0004", ""},
                                    {"\u0005", ""},
                                    {"\u0006", ""},
                                    {"\u0007", ""},
                                    {"\u0008", ""},
                                    {"\u000b", ""},
                                    {"\u000c", ""},
                                    {"\u000e", ""},
                                    {"\u000f", ""},
                                    {"\u0010", ""},
                                    {"\u0011", ""},
                                    {"\u0012", ""},
                                    {"\u0013", ""},
                                    {"\u0014", ""},
                                    {"\u0015", ""},
                                    {"\u0016", ""},
                                    {"\u0017", ""},
                                    {"\u0018", ""},
                                    {"\u0019", ""},
                                    {"\u001a", ""},
                                    {"\u001b", ""},
                                    {"\u001c", ""},
                                    {"\u001d", ""},
                                    {"\u001e", ""},
                                    {"\u001f", ""},
                                    {"\ufffe", ""},
                                    {"\uffff", ""}
                            }),
                    NumericEntityEscaper.between(0x7f, 0x84),
                    NumericEntityEscaper.between(0x86, 0x9f),
                    new UnicodeUnpairedSurrogateRemover()
            );
}
