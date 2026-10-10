// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VirusTotalResponseTest {
    @Test
    public void retryableErrorsAreRecognised() {
        assertTrue(response("QuotaExceededError").shouldRetry());
        assertTrue(response("NotFoundError").shouldRetry());
        assertTrue(response("NotAvailableYet").shouldRetry());
    }

    @Test
    public void permanentErrorsAreNotRetried() {
        assertFalse(response("InvalidArgumentError").shouldRetry());
        assertFalse(response(null).shouldRetry());
    }

    private static VirusTotal.ResponseV3<String> response(String code) {
        return new VirusTotal.ResponseV3<>(null, new VtError(429, code, "test"));
    }
}
