// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LocalServerManagerTest {
    @Test
    public void repeatedCommandsUseTheirOwnCompletionState() throws Exception {
        ByteArrayOutputStream startOutput = new ByteArrayOutputStream();
        LocalServerManager.executeAdbCommand(
                input("uid=2000(shell)\nSuccess! Server has started.\n"),
                startOutput,
                "start-server",
                "Success!",
                1,
                TimeUnit.SECONDS);

        ByteArrayOutputStream stopOutput = new ByteArrayOutputStream();
        LocalServerManager.executeAdbCommand(
                input("uid=2000(shell)\nStopped!\n"),
                stopOutput,
                "stop-server",
                "Stopped!",
                1,
                TimeUnit.SECONDS);

        assertTrue(startOutput.toString("UTF-8").contains("start-server\n"));
        assertTrue(stopOutput.toString("UTF-8").contains("stop-server\n"));
    }

    @Test
    public void unrelatedOldMarkerDoesNotCompleteCommand() throws Exception {
        LocalServerManager.executeAdbCommand(
                input("Success! Old operation\nStopped!\n"),
                new ByteArrayOutputStream(),
                "stop-server",
                "Stopped!",
                1,
                TimeUnit.SECONDS);
    }

    @Test
    public void errorMarkerFailsCommand() throws Exception {
        try {
            LocalServerManager.executeAdbCommand(
                    input("Error! Could not start server.\n"),
                    new ByteArrayOutputStream(),
                    "start-server",
                    "Success!",
                    1,
                    TimeUnit.SECONDS);
            fail("Expected an IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Success!"));
        }
    }

    @Test
    public void missingMarkerFailsCommand() throws Exception {
        try {
            LocalServerManager.executeAdbCommand(
                    input("uid=2000(shell)\n"),
                    new ByteArrayOutputStream(),
                    "start-server",
                    "Success!",
                    1,
                    TimeUnit.SECONDS);
            fail("Expected an IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Success!"));
        }
    }

    @Test
    public void commandTimesOutWithoutOutput() throws Exception {
        try {
            LocalServerManager.executeAdbCommand(
                    new NeverEndingInputStream(),
                    new ByteArrayOutputStream(),
                    "start-server",
                    "Success!",
                    10,
                    TimeUnit.MILLISECONDS);
            fail("Expected a SocketTimeoutException");
        } catch (SocketTimeoutException expected) {
            assertTrue(expected.getMessage().contains("start-server"));
        }
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class NeverEndingInputStream extends InputStream {
        @Override
        public synchronized int read(byte[] bytes, int offset, int length) throws IOException {
            try {
                wait();
                return -1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }

        @Override
        public int read() throws IOException {
            return read(new byte[1], 0, 1);
        }
    }
}
