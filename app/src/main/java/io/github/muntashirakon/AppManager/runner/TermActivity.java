// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.os.Bundle;
import android.os.PowerManager;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
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
import io.github.muntashirakon.AppManager.utils.CpuUtils;

// TODO: 11/9/23 Replace it with an actual terminal
public class TermActivity extends BaseActivity {
    private final Object mLock = new Object();
    private AppCompatEditText mCommandInput;
    private AppCompatTextView mCommandOutput;
    private Process mProc;
    private OutputStream mProcessOutputStream;
    private PowerManager.WakeLock mWakeLock;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(3);

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_term);
        setSupportActionBar(findViewById(R.id.toolbar));
        mCommandInput = findViewById(R.id.command_input);
        mCommandOutput = findViewById(R.id.command_output);
        mCommandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String command = Objects.requireNonNull(mCommandInput.getText()) + "\n";
                if (mProcessOutputStream != null) {
                    if (!ProcessCompat.isAlive(mProc)) {
                        // Process is dead
                        return false;
                    }
                    try {
                        mProcessOutputStream.write(command.getBytes(StandardCharsets.UTF_8));
                        mProcessOutputStream.flush();
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        CpuUtils.releaseWakeLock(mWakeLock);
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    private void initShell() {
        mWakeLock = CpuUtils.getPartialWakeLock("term");
        mWakeLock.acquire();
        mExecutor.submit(() -> {
            try {
                mProc = ProcessCompat.exec(new String[]{"sh"}/*, new String[]{"TERM=xterm-256color"}*/);
                mProcessOutputStream = new BufferedOutputStream(mProc.getOutputStream());
                mExecutor.submit(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(mProc.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            synchronized (mLock) {
                                String finalLine = line;
                                runOnUiThread(() -> mCommandOutput.append(finalLine + "\n"));
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
                mExecutor.submit(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(mProc.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            synchronized (mLock) {
                                String finalLine = line;
                                runOnUiThread(() -> mCommandOutput.append(finalLine + "\n"));
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
                mProc.waitFor();
                runOnUiThread(this::finishAndRemoveTask);
            } catch (Throwable e) {
                e.printStackTrace();
                // TODO: 23/1/23 Handle error
            }
        });
    }

    @AnyThread
    private void appendOutput(String line) {
        mCommandOutput.append(line + "\n");
    }
}
