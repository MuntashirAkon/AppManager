// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;

import java.io.BufferedOutputStream;
import java.io.InputStream;
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
    private boolean mCommandInProgress;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(3);

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_term);
        setSupportActionBar(findViewById(R.id.toolbar));
        mCommandInput = findViewById(R.id.command_input);
        mCommandOutput = findViewById(R.id.command_output);
        mCommandOutput.setText("", TextView.BufferType.EDITABLE);
        mCommandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String command = Objects.requireNonNull(mCommandInput.getText()).toString();
                appendBoldOutput((mCommandInProgress ? "  " : "$ ") + command);
                mCommandInProgress = command.endsWith("\\");
                appendOutput("\n");
                if (mProcessOutputStream != null) {
                    if (!ProcessCompat.isAlive(mProc)) {
                        // Process is dead
                        return false;
                    }
                    try {
                        mProcessOutputStream.write(command.getBytes(StandardCharsets.UTF_8));
                        mProcessOutputStream.write("\n".getBytes(StandardCharsets.UTF_8));
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
                    try (InputStream in = mProc.getInputStream()) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            synchronized (mLock) {
                                String chunk = new String(buffer, 0, len, StandardCharsets.UTF_8);
                                runOnUiThread(() -> appendOutput(chunk));
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
                mExecutor.submit(() -> {
                    try (InputStream in = mProc.getErrorStream()) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            synchronized (mLock) {
                                String chunk = new String(buffer, 0, len, StandardCharsets.UTF_8);
                                runOnUiThread(() -> appendOutput(chunk));
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

    @MainThread
    private void appendOutput(String text) {
        mCommandOutput.append(text);
    }

    @MainThread
    private void appendBoldOutput(String boldText) {
        Editable editable = mCommandOutput.getEditableText();
        int start = editable.length();
        editable.append(boldText);
        editable.setSpan(new StyleSpan(Typeface.BOLD), start, editable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
