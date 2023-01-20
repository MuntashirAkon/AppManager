// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @see android.sun.security.x509.OIDMap
 */
public class OidMap {
    private static final Map<String, String> oidNameMap = new HashMap<String, String>() {{
        put("2.5.29.9", "subjectDirectoryAttributes");

        put("2.5.29.14", "subjectKeyIdentifier");
        put("2.5.29.15", "keyUsage");
        put("2.5.29.16", "privateKeyUsagePeriod");
        put("2.5.29.17", "subjectAltName");
        put("2.5.29.18", "issuerAltName");
        put("2.5.29.19", "basicConstraints");
        put("2.5.29.20", "cRLNumber");
        put("2.5.29.21", "reasonCode");

        put("2.5.29.23", "instructionCode");
        put("2.5.29.24", "invalidityDate");

        put("2.5.29.27", "deltaCRLIndicator");
        put("2.5.29.28", "issuingDistributionPoint");
        put("2.5.29.29", "certificateIssuer");
        put("2.5.29.30", "nameConstraints");
        put("2.5.29.31", "cRLDistributionPoints");
        put("2.5.29.32", "certificatePolicies");
        put("2.5.29.33", "policyMappings");

        put("2.5.29.35", "authorityKeyIdentifier");
        put("2.5.29.36", "policyConstraints");
        put("2.5.29.37", "extKeyUsage");
        put("2.5.29.38", "authorityAttributeIdentifier");
        put("2.5.29.39", "roleSpecCertIdentifier");
        put("2.5.29.40", "cRLStreamIdentifier");
        put("2.5.29.41", "basicAttConstraints");
        put("2.5.29.42", "delegatedNameConstraints");
        put("2.5.29.43", "timeSpecification");
        put("2.5.29.44", "cRLScope");
        put("2.5.29.45", "statusReferrals");
        put("2.5.29.46", "freshestCRL");
        put("2.5.29.47", "orderedList");
        put("2.5.29.48", "attributeDescriptor");
        put("2.5.29.49", "userNotice");
        put("2.5.29.50", "sOAIdentifier");
        put("2.5.29.51", "baseUpdateTime");
        put("2.5.29.52", "acceptableCertPolicies");
        put("2.5.29.53", "deltaInfo");
        put("2.5.29.54", "inhibitAnyPolicy");
        put("2.5.29.55", "targetInformation");
        put("2.5.29.56", "noRevAvail");
        put("2.5.29.57", "acceptablePrivilegePolicies");

        put("2.5.29.61", "indirectIssuer");

        put("1.3.6.1.5.5.7.1.1", "AuthorityInfoAccess");
        put("1.3.6.1.5.5.7.1.11", "SubjectInfoAccess");
        put("1.3.6.1.5.5.7.48.1.5", "OCSPNoCheck");
    }};

    @Nullable
    public static String getName(@NonNull String oid) {
        return oidNameMap.get(oid);
    }
}
