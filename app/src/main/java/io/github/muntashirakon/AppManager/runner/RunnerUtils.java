// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.security.InvalidParameterException;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;

public final class RunnerUtils {
    public static final String TAG = RunnerUtils.class.getSimpleName();

    public static final String CMD_AM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? "cmd activity" : "am";
    public static final String CMD_PM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? "cmd package" : "pm";

    private static final String EMPTY = "";

    /**
     * Translator object for escaping Shell command language.
     *
     * @see <a href="http://pubs.opengroup.org/onlinepubs/7908799/xcu/chap2.html">Shell Command Language</a>
     */
    public static final LookupTranslator ESCAPE_XSI;

    static {
        final Map<CharSequence, CharSequence> escapeXsiMap = new HashMap<>();
        escapeXsiMap.put("|", "\\|");
        escapeXsiMap.put("&", "\\&");
        escapeXsiMap.put(";", "\\;");
        escapeXsiMap.put("<", "\\<");
        escapeXsiMap.put(">", "\\>");
        escapeXsiMap.put("(", "\\(");
        escapeXsiMap.put(")", "\\)");
        escapeXsiMap.put("$", "\\$");
        escapeXsiMap.put("`", "\\`");
        escapeXsiMap.put("\\", "\\\\");
        escapeXsiMap.put("\"", "\\\"");
        escapeXsiMap.put("'", "\\'");
        escapeXsiMap.put(" ", "\\ ");
        escapeXsiMap.put("\t", "\\\t");
        escapeXsiMap.put("\r\n", EMPTY);
        escapeXsiMap.put("\n", EMPTY);
        escapeXsiMap.put("*", "\\*");
        escapeXsiMap.put("?", "\\?");
        escapeXsiMap.put("[", "\\[");
        escapeXsiMap.put("#", "\\#");
        escapeXsiMap.put("~", "\\~");
        escapeXsiMap.put("=", "\\=");
        escapeXsiMap.put("%", "\\%");
        ESCAPE_XSI = new LookupTranslator(Collections.unmodifiableMap(escapeXsiMap));
    }

    /**
     * <p>Escapes the characters in a {@code String} using XSI rules.</p>
     *
     * <p><b>Beware!</b> In most cases you don't want to escape shell commands but use multi-argument
     * methods provided by {@link java.lang.ProcessBuilder} or {@link java.lang.Runtime#exec(String[])}
     * instead.</p>
     *
     * <p>Example:</p>
     * <pre>
     * input string: He didn't say, "Stop!"
     * output string: He\ didn\'t\ say,\ \"Stop!\"
     * </pre>
     *
     * @param input String to escape values in, may be null
     * @return String with escaped values, {@code null} if null string input
     * @see <a href="http://pubs.opengroup.org/onlinepubs/7908799/xcu/chap2.html">Shell Command Language</a>
     */
    public static String escape(final String input) {
        return ESCAPE_XSI.translate(input);
    }

    @NoOps
    public static boolean isRootGiven() {
        return Boolean.TRUE.equals(isAppGrantedRoot());
    }

    @NoOps
    public static boolean isRootAvailable() {
        return !Boolean.FALSE.equals(isAppGrantedRoot());
    }

    /**
     * @see Shell#isAppGrantedRoot()
     */
    @Nullable
    @NoOps
    public static Boolean isAppGrantedRoot() {
        if (Runner.getRootInstance().isRoot()) {
            // Root granted
            return true;
        }
        // Check if root is available
        String pathEnv = System.getenv("PATH");
        Log.d(TAG, "PATH=%s", pathEnv);
        if (pathEnv == null) return false;
        for (String pathDir : pathEnv.split(":")) {
            File suFile = new File(pathDir, "su");
            Log.d(TAG, "SU(file=%s, exists=%s, executable=%s)", suFile, suFile.exists(), suFile.canExecute());
            if (new File(pathDir, "su").canExecute()) {
                // Root available but App Manager is not granted root
                return null;
            }
        }
        return false;
    }

    /**
     * An API for translating text.
     * Its core use is to escape and unescape text. Because escaping and unescaping
     * is completely contextual, the API does not present two separate signatures.
     *
     * @since 1.0
     */
    public static class LookupTranslator {
        /**
         * The mapping to be used in translation.
         */
        private final Map<String, String> mLookupMap;
        /**
         * The first character of each key in the lookupMap.
         */
        private final BitSet mPrefixSet;
        /**
         * The length of the shortest key in the lookupMap.
         */
        private final int mShortest;
        /**
         * The length of the longest key in the lookupMap.
         */
        private final int mLongest;

        /**
         * Define the lookup table to be used in translation
         * <p>
         * Note that, as of Lang 3.1 (the origin of this code), the key to the lookup
         * table is converted to a java.lang.String. This is because we need the key
         * to support hashCode and equals(Object), allowing it to be the key for a
         * HashMap. See LANG-882.
         *
         * @param lookupMap Map&lt;CharSequence, CharSequence&gt; table of translator
         *                  mappings
         */
        public LookupTranslator(final Map<CharSequence, CharSequence> lookupMap) {
            if (lookupMap == null) {
                throw new InvalidParameterException("lookupMap cannot be null");
            }
            mLookupMap = new HashMap<>();
            mPrefixSet = new BitSet();
            int currentShortest = Integer.MAX_VALUE;
            int currentLongest = 0;

            for (final Map.Entry<CharSequence, CharSequence> pair : lookupMap.entrySet()) {
                mLookupMap.put(pair.getKey().toString(), pair.getValue().toString());
                mPrefixSet.set(pair.getKey().charAt(0));
                final int sz = pair.getKey().length();
                if (sz < currentShortest) {
                    currentShortest = sz;
                }
                if (sz > currentLongest) {
                    currentLongest = sz;
                }
            }
            mShortest = currentShortest;
            mLongest = currentLongest;
        }

        /**
         * Translate a set of codepoints, represented by an int index into a CharSequence,
         * into another set of codepoints. The number of codepoints consumed must be returned,
         * and the only IOExceptions thrown must be from interacting with the Writer so that
         * the top level API may reliably ignore StringWriter IOExceptions.
         *
         * @param input CharSequence that is being translated
         * @param index int representing the current point of translation
         * @param out   Writer to translate the text to
         * @return int count of codepoints consumed
         * @throws IOException if and only if the Writer produces an IOException
         */
        public int translate(@NonNull final CharSequence input, final int index, final Writer out) throws IOException {
            // check if translation exists for the input at position index
            if (mPrefixSet.get(input.charAt(index))) {
                int max = mLongest;
                if (index + mLongest > input.length()) {
                    max = input.length() - index;
                }
                // implement greedy algorithm by trying maximum match first
                for (int i = max; i >= mShortest; i--) {
                    final CharSequence subSeq = input.subSequence(index, index + i);
                    final String result = mLookupMap.get(subSeq.toString());

                    if (result != null) {
                        out.write(result);
                        return i;
                    }
                }
            }
            return 0;
        }

        /**
         * Helper for non-Writer usage.
         *
         * @param input CharSequence to be translated
         * @return String output of translation
         */
        public final String translate(final CharSequence input) {
            if (input == null) {
                return null;
            }
            try {
                final StringWriter writer = new StringWriter(input.length() * 2);
                translate(input, writer);
                return writer.toString();
            } catch (final IOException ioe) {
                // this should never ever happen while writing to a StringWriter
                throw new RuntimeException(ioe);
            }
        }

        /**
         * Translate an input onto a Writer. This is intentionally final as its algorithm is
         * tightly coupled with the abstract method of this class.
         *
         * @param input CharSequence that is being translated
         * @param out   Writer to translate the text to
         * @throws IOException if and only if the Writer produces an IOException
         */
        public final void translate(final CharSequence input, final Writer out) throws IOException {
            if (input == null) {
                return;
            }
            int pos = 0;
            final int len = input.length();
            while (pos < len) {
                final int consumed = translate(input, pos, out);
                if (consumed == 0) {
                    // inlined implementation of Character.toChars(Character.codePointAt(input, pos))
                    // avoids allocating temp char arrays and duplicate checks
                    final char c1 = input.charAt(pos);
                    out.write(c1);
                    pos++;
                    if (Character.isHighSurrogate(c1) && pos < len) {
                        final char c2 = input.charAt(pos);
                        if (Character.isLowSurrogate(c2)) {
                            out.write(c2);
                            pos++;
                        }
                    }
                    continue;
                }
                // contract with translators is that they have to understand codepoints
                // and they just took care of a surrogate pair
                for (int pt = 0; pt < consumed; pt++) {
                    pos += Character.charCount(Character.codePointAt(input, pos));
                }
            }
        }
    }
}
