// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class SignatureOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put("all", TYPE_NONE);
        put("no_signer", TYPE_NONE);
        put("with_source_stamp", TYPE_NONE);
        put("with_lineage", TYPE_NONE);
        put("sub_eq", TYPE_STR_SINGLE);
        put("sub_contains", TYPE_STR_SINGLE);
        put("sub_starts_with", TYPE_STR_SINGLE);
        put("sub_ends_with", TYPE_STR_SINGLE);
        put("sub_regex", TYPE_REGEX);
        put("sha256", TYPE_STR_SINGLE);
    }};

    public SignatureOption() {
        super("signature");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull FilterableAppInfo info, @NonNull TestResult result) {
        SignerInfo signerInfo = info.fetchSignerInfo();
        if (signerInfo == null || signerInfo.getCurrentSignerCerts() == null) {
            // No singer
            return result.setMatched(key.equals("no_signer")).setMatchedSubjectLines(Collections.emptyList());
        }
        List<String> subjectLines = result.getMatchedSubjectLines() != null
                ? result.getMatchedSubjectLines() : Arrays.asList(info.getSignatureSubjectLines());
        switch (key) {
            default:
                return result.setMatched(true).setMatchedSubjectLines(subjectLines);
            case "no_signer":
                // Signer exists at this point
                return result.setMatched(false).setMatchedSubjectLines(subjectLines);
            case "with_source_stamp":
                return result.setMatched(signerInfo.getSourceStampCert() != null).setMatchedSubjectLines(subjectLines);
            case "with_lineage":
                return result.setMatched(signerInfo.getSignerCertsInLineage() != null).setMatchedSubjectLines(subjectLines);
            case "sub_eq": {
                List<String> matchedSubjectLines = new ArrayList<>();
                for (String subject : subjectLines) {
                    if (subject.equals(value)) {
                        matchedSubjectLines.add(subject);
                    }
                }
                return result.setMatched(!matchedSubjectLines.isEmpty())
                        .setMatchedSubjectLines(matchedSubjectLines);
            }
            case "sub_contains": {
                Objects.requireNonNull(value);
                List<String> matchedSubjectLines = new ArrayList<>();
                for (String subject : subjectLines) {
                    if (subject.contains(value)) {
                        matchedSubjectLines.add(subject);
                    }
                }
                return result.setMatched(!matchedSubjectLines.isEmpty())
                        .setMatchedSubjectLines(matchedSubjectLines);
            }
            case "sub_starts_with": {
                Objects.requireNonNull(value);
                List<String> matchedSubjectLines = new ArrayList<>();
                for (String subject : subjectLines) {
                    if (subject.startsWith(value)) {
                        matchedSubjectLines.add(subject);
                    }
                }
                return result.setMatched(!matchedSubjectLines.isEmpty())
                        .setMatchedSubjectLines(matchedSubjectLines);
            }
            case "sub_ends_with": {
                Objects.requireNonNull(value);
                List<String> matchedSubjectLines = new ArrayList<>();
                for (String subject : subjectLines) {
                    if (subject.endsWith(value)) {
                        matchedSubjectLines.add(subject);
                    }
                }
                return result.setMatched(!matchedSubjectLines.isEmpty())
                        .setMatchedSubjectLines(matchedSubjectLines);
            }
            case "sub_regex": {
                Objects.requireNonNull(value);
                List<String> matchedSubjectLines = new ArrayList<>();
                for (String subject : subjectLines) {
                    if (regexValue.matcher(subject).matches()) {
                        matchedSubjectLines.add(subject);
                    }
                }
                return result.setMatched(!matchedSubjectLines.isEmpty())
                        .setMatchedSubjectLines(matchedSubjectLines);
            }
            case "sha256": {
                String[] sha256sums = info.getSignatureSha256Checksums();
                for (int i = 0; i < sha256sums.length; ++i) {
                    if (sha256sums[i].equals(value)) {
                        return result.setMatched(true)
                                .setMatchedSubjectLines(Collections.singletonList(info.getSignatureSubjectLines()[i]));
                    }
                }
            }
        }
        return result.setMatched(false)
                .setMatchedSubjectLines(Collections.emptyList());
    }
}
