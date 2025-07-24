// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;

import com.google.android.material.color.MaterialColors;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ProcessCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.CpuUtils;

// TODO: 11/9/23 Replace it with an actual terminal
public class TermActivity extends BaseActivity {
    public static final String TAG = TermActivity.class.getSimpleName();
    private final Object mLock = new Object();
    private AppCompatEditText mCommandInput;
    private AppCompatTextView mCommandOutput;
    private Process mProc;
    private OutputStream mProcessOutputStream;
    private PowerManager.WakeLock mWakeLock;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(3);
    private int mDefaultForegroundColor;
    private int mDefaultBackgroundColor;
    private final AnsiState mAnsiState = new AnsiState();

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_term);
        setSupportActionBar(findViewById(R.id.toolbar));
        mCommandInput = findViewById(R.id.command_input);
        mCommandOutput = findViewById(R.id.command_output);
        mCommandOutput.setText("", TextView.BufferType.EDITABLE);
        mDefaultForegroundColor = mCommandInput.getCurrentTextColor();
        mDefaultBackgroundColor = MaterialColors.getColor(mCommandInput, com.google.android.material.R.attr.colorSurface);
        mCommandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String command = Objects.requireNonNull(mCommandInput.getText()).toString();
                appendBoldOutput(command);
                appendOutput("\n");
                mAnsiState.homePosition = mCommandOutput.getEditableText().length();
                return sendToStdin(command, true);
            }
            return false;
        });
        mCommandInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                // Only handle key down
                return false;
            }

            // TAB
            if (keyCode == KeyEvent.KEYCODE_TAB) {
                // TODO: 7/21/25 Support minimal completion
            }

            // Arrow Keys (UP/DOWN/PGUP/PGDOWN)
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    // TODO: 7/21/25 Support history
            }

            // CTRL + KEY
            if (event.isCtrlPressed()) {
                int c = event.getUnicodeChar();
                char ctrlChar;
                if (c >= 'a' && c <= 'z') {
                    ctrlChar = (char)(c - 'a' + 1);
                } else if (c >= 'A' && c <= 'Z') {
                    ctrlChar = (char)(c - 'A' + 1);
                } else return false;
                appendBoldOutput("^" + (char) (c + 'A'));
                appendOutput("\n");
                sendToStdin(String.valueOf(ctrlChar), true);
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
                mProc = ProcessCompat.exec(new String[]{"sh", "-i"}, new String[]{"TERM=xterm-256color", "HOME=/"});
                mProcessOutputStream = new BufferedOutputStream(mProc.getOutputStream());
                mExecutor.submit(() -> {
                    try (InputStream in = mProc.getInputStream()) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            synchronized (mLock) {
                                String chunk = new String(buffer, 0, len, StandardCharsets.UTF_8);
                                runOnUiThread(() -> processChunk(chunk, mAnsiState));
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
                                runOnUiThread(() -> processChunk(chunk, mAnsiState));
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
                // TODO: 7/21/25 Support init script
                mProc.waitFor();
                runOnUiThread(this::finishAndRemoveTask);
            } catch (Throwable e) {
                e.printStackTrace();
                // TODO: 23/1/23 Handle error
            }
        });
    }

    static class AnsiState {
        @ColorInt
        int foreground = -1;
        @ColorInt
        int background = -1;
        boolean bold = false;
        boolean underline = false;
        boolean italic = false;
        boolean strike = false;
        boolean reverse = false;
        boolean hide = false;
        int savedPosition = -1;
        boolean navigateToHome = false;
        int homePosition = 0;

        void reset() {
            foreground = -1;
            background = -1;
            bold = false;
            underline = false;
            italic = false;
            strike = false;
            reverse = false;
            hide = false;
            savedPosition = -1;
            navigateToHome = false;
        }
    }

    @UiThread
    private void processChunk(@NonNull String chunk, @NonNull AnsiState state) {
        StringBuilder plainTextBuffer = new StringBuilder();
        for (int i = 0; i < chunk.length(); ++i) {
            char ch = chunk.charAt(i);
            if (ch == '\u001b') {
                // Start escape sequence
                // Flush plain text
                if (plainTextBuffer.length() > 0) {
                    appendOutput(plainTextBuffer.toString(), state);
                    plainTextBuffer.setLength(0);
                }
                ++i;
                if (i >= chunk.length()) {
                    // Invalid sequence
                    break;
                }
                ch = chunk.charAt(i);
                if (ch == '[') {
                    // Start of special sequences
                    StringBuilder numberBuilder = new StringBuilder();
                    numberBuilder.append(ch);
                    for (++i; i < chunk.length(); ++i) {
                        char c = chunk.charAt(i);
                        if (c >= '0' && c <= '9') {
                            // A number
                            numberBuilder.append(c);
                        } else if (c == ';' || c == '?') {
                            // Separator
                            numberBuilder.append(c);
                        } else {
                            // Any other ANSI character
                            numberBuilder.append(c);
                            processAnsiSeq(numberBuilder.toString(), state);
                            // We're done
                            break;
                        }
                    }
                } else if (ch == 'M') {
                    // ENTER
                    processAnsiSeq("M", state);
                } // else Invalid sequence
            } else {
                plainTextBuffer.append(ch);
            }
        }
        if (plainTextBuffer.length() > 0) {
            appendOutput(plainTextBuffer.toString(), state);
        }
    }

    @MainThread
    void processAnsiSeq(@NonNull String seq, @NonNull AnsiState state) {
        if (seq.matches("\\[[0-2]?J")) {
            String code = seq.substring(1, seq.length() - 1);
            switch (code) {
                case "2": // Clear entire screen
                    resetOutput(state);
                    break;
                case "1": // Clear screen from cursor up
                    Log.i(TAG, "Unhandled escape sequence: " + seq);
                    break;
                case "0": // Clear screen from cursor down
                default:
                    if (state.navigateToHome) {
                        resetOutputToPosition(state.homePosition);
                    } // else Not handled
            }
        } else if (seq.equals("[H") || seq.equals("[;H") || seq.equals("[f")) {
            state.navigateToHome = true;
        } else if (seq.equals("[2K")) {
            clearLastLine();
        } else if (seq.equals("[s")) {
            Editable editable = mCommandOutput.getEditableText();
            state.savedPosition = editable.length();
        } else if (seq.equals("[u")) {
            Editable editable = mCommandOutput.getEditableText();
            if (state.savedPosition >= 0 && state.savedPosition <= editable.length()) {
                editable.delete(state.savedPosition, editable.length());
            }
        } else if (seq.matches("\\[[0-9;]*m")) {
            // Parse the numbers
            String params = seq.substring(1, seq.length() - 1);
            String[] codes = params.split(";");
            for (String code : codes) {
                int value;
                try {
                    value = Integer.parseInt(code);
                } catch (Exception e) {
                    if (code.isEmpty()) {
                        value = 0; // Same as RESET
                    } else continue;
                }
                switch (value) {
                    case 0: // RESET
                        state.reset();
                        break;
                    case 1: // BOLD
                        state.bold = true;
                        break;
                    case 22: // RESET BOLD
                        state.bold = false;
                        break;
                    case 3: // ITALIC
                        state.italic = true;
                        break;
                    case 23: // RESET ITALIC
                        state.italic = false;
                        break;
                    case 4: // UNDERLINE
                        state.underline = true;
                        break;
                    case 24: // RESET UNDERLINE
                        state.underline = false;
                        break;
                    case 7: // REVERSE VIDEO
                        state.reverse = true;
                        break;
                    case 27: // RESET REVERSE VIDEO
                        state.reverse = false;
                        break;
                    case 8: // HIDE
                        state.hide = true;
                        break;
                    case 28: // RESET HIDE
                        state.hide = false;
                        break;
                    case 9: // STRIKETHROUGH
                        state.strike = true;
                        break;
                    case 29: // RESET STRIKETHROUGH
                        state.strike = false;
                        break;
                    case 39: // RESET FOREGROUND
                        state.foreground = -1;
                        break;
                    case 30:
                        state.foreground = Color.BLACK;
                        break;
                    case 31:
                        state.foreground = Color.RED;
                        break;
                    case 32:
                        state.foreground = Color.GREEN;
                        break;
                    case 33:
                        state.foreground = Color.YELLOW;
                        break;
                    case 34:
                        state.foreground = Color.BLUE;
                        break;
                    case 35:
                        state.foreground = Color.MAGENTA;
                        break;
                    case 36:
                        state.foreground = Color.CYAN;
                        break;
                    case 37:
                        state.foreground = Color.WHITE;
                        break;
                    case 49: // RESET BACKGROUND
                        state.background = -1;
                        break;
                    case 40:
                        state.background = Color.BLACK;
                        break;
                    case 41:
                        state.background = Color.RED;
                        break;
                    case 42:
                        state.background = Color.GREEN;
                        break;
                    case 43:
                        state.background = Color.YELLOW;
                        break;
                    case 44:
                        state.background = Color.BLUE;
                        break;
                    case 45:
                        state.background = Color.MAGENTA;
                        break;
                    case 46:
                        state.background = Color.CYAN;
                        break;
                    case 47:
                        state.background = Color.WHITE;
                        break;
                    default:
                        Log.i(TAG, "Unhandled escape sequence: " + seq);
                }
            }
        } else {
            Log.i(TAG, "Unhandled escape sequence: " + seq);
        }
    }

    @MainThread
    private void resetOutput(@NonNull AnsiState state) {
        mCommandOutput.getEditableText().clear();
        state.homePosition = 0;
    }

    @MainThread
    private void resetOutputToPosition(int position) {
        Editable editable = mCommandOutput.getEditableText();
        if (position <= 0) {
            editable.clear();
        } else {
            int length = editable.length();
            if (position < length) {
                editable.delete(position, length);
            }
        }
    }

    private void clearLastLine() {
        Editable editable = mCommandOutput.getEditableText();
        int length = editable.length();
        if (length > 0) {
            // Find the index of the last newline character
            int lastNewline = editable.toString().lastIndexOf('\n', length - 2);
            // If no newline found, delete everything
            resetOutputToPosition(lastNewline + 1);
        }
    }

    @MainThread
    private void appendOutput(@NonNull String text) {
        mCommandOutput.getEditableText().append(text);
    }

    @MainThread
    private void appendOutput(@NonNull String text, @NonNull AnsiState state) {
        state.navigateToHome = false;
        if (state.hide) {
            // Replace with spaces
            char[] blank = new char[text.length()];
            Arrays.fill(blank, ' ');
            text = new String(blank);
        }
        Editable editable = mCommandOutput.getEditableText();
        int start = editable.length();
        editable.append(text);
        int end = editable.length();
        if (start == end) {
            return;
        }
        if (state.bold) {
            editable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (state.italic) {
            editable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (state.underline) {
            editable.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (state.strike) {
            editable.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int foregroundColor = state.foreground != -1 ? state.foreground : mDefaultForegroundColor;
        int backgroundColor = state.background != -1 ? state.background : mDefaultBackgroundColor;
        editable.setSpan(new ForegroundColorSpan(state.reverse ? backgroundColor : foregroundColor),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(new BackgroundColorSpan(state.reverse ? foregroundColor : backgroundColor),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @MainThread
    private void appendBoldOutput(@NonNull String boldText) {
        Editable editable = mCommandOutput.getEditableText();
        int start = editable.length();
        editable.append(boldText);
        editable.setSpan(new StyleSpan(Typeface.BOLD), start, editable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private boolean sendToStdin(@NonNull String command, boolean newLine) {
        if (mProcessOutputStream != null) {
            if (!ProcessCompat.isAlive(mProc)) {
                // Process is dead
                return false;
            }
            try {
                mProcessOutputStream.write(command.getBytes(StandardCharsets.UTF_8));
                if (newLine) {
                    mProcessOutputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                }
                mProcessOutputStream.flush();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return true;
    }
}
