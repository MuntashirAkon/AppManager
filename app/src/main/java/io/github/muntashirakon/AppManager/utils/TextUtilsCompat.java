// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;

public class TextUtilsCompat {
    /**
     * Returns a string containing the tokens joined by delimiters.
     *
     * @param delimiter a CharSequence that will be inserted between the tokens. If null, the string
     *                  "null" will be used as the delimiter.
     * @param tokens    an array objects to be joined. Strings will be formed from the objects by
     *                  calling object.toString() except CharSequence. If tokens is null, a
     *                  NullPointerException will be thrown. If tokens is empty, an empty string
     *                  will be returned.
     */
    @NonNull
    public static Spannable joinSpannable(@NonNull CharSequence delimiter, @NonNull Iterable<?> tokens) {
        final Iterator<?> it = tokens.iterator();
        if (!it.hasNext()) {
            return new SpannableString("");
        }
        final SpannableStringBuilder sb = new SpannableStringBuilder();
        Object object = it.next();
        if (object instanceof CharSequence) {
            sb.append((CharSequence) object);
        } else sb.append(object.toString());
        while (it.hasNext()) {
            sb.append(delimiter);
            object = it.next();
            if (object instanceof CharSequence) {
                sb.append((CharSequence) object);
            } else sb.append(object.toString());
        }
        return sb;
    }

    /**
     * @return interned string if it's not null.
     */
    @Nullable
    public static String safeIntern(@Nullable String s) {
        return (s != null) ? s.intern() : null;
    }
}
