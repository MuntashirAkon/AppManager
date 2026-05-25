// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CertUtils {
    public static String getReadableSubject(X509Certificate cert) {
        return Stream.of(BCStyle.CN, BCStyle.O, BCStyle.L, BCStyle.ST, BCStyle.C)
                .map(oid -> X500Name.getInstance(cert.getSubjectX500Principal().getEncoded()).getRDNs(oid))
                .filter(rdns -> rdns.length > 0)
                .map(rdns -> rdns[0].getFirst().getValue().toString())
                .collect(Collectors.joining(", "));
    }

    public static String getReadableSubject(String subjectLine) {
        Map<String, String> rdnMap = new HashMap<>();
        Matcher matcher = Pattern.compile("([^=,]+)=(((?<=\\\\),|[^,])+)").matcher(subjectLine);
        while (matcher.find()) {
            String key = matcher.group(1).trim().toUpperCase();
            String value = matcher.group(2).trim().replaceAll("\\\\,", ",");
            rdnMap.put(key, value);
        }

        return Stream.of("CN", "O", "L", "ST", "C")
                .map(rdnMap::get)
                .filter(value -> value != null && !value.isEmpty())
                .collect(Collectors.joining(", "));
    }
}
