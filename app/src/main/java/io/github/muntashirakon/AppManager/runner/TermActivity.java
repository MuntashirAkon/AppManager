// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ProcessCompat;

public class TermActivity extends BaseActivity {
    private final Object lock = new Object();
    private AppCompatEditText commandInput;
    private AppCompatTextView commandOutput;
    private Process proc;
    private OutputStream processOutputStream;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_term);
        setSupportActionBar(findViewById(R.id.toolbar));
        commandInput = findViewById(R.id.command_input);
        commandOutput = findViewById(R.id.command_output);
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String command = Objects.requireNonNull(commandInput.getText()) + "\n";
                if (processOutputStream != null) {
                    if (!ProcessCompat.isAlive(proc)) {
                        // Process is dead
                        return false;
                    }
                    try {
                        processOutputStream.write(command.getBytes(StandardCharsets.UTF_8));
                        processOutputStream.flush();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
                return true;
            }
            return false;
        });
        initShell();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void initShell() {
        executor.submit(() -> {
            try {
                proc = ProcessCompat.exec(new String[]{"sh"}/*, new String[]{"TERM=xterm-256color"}*/);
                processOutputStream = new BufferedOutputStream(proc.getOutputStream());
                executor.submit(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            synchronized (lock) {
                                String finalLine = line;
                                runOnUiThread(() -> commandOutput.append(finalLine + "\n"));
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
                executor.submit(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            synchronized (lock) {
                                String finalLine = line;
                                runOnUiThread(() -> commandOutput.append(finalLine + "\n"));
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
                proc.waitFor();
                runOnUiThread(this::finishAndRemoveTask);
            } catch (Throwable e) {
                e.printStackTrace();
                // TODO: 23/1/23 Handle error
            }
        });
    }

    @AnyThread
    private void appendOutput(String line) {
        commandOutput.append(line + "\n");
    }
}
