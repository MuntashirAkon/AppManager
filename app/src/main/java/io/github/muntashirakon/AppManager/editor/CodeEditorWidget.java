// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.inputmethod.BaseInputConnection;
import android.widget.Toast;

import java.lang.reflect.Field;

import io.github.muntashirakon.AppManager.utils.ClipboardUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.text.TextRange;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.DirectAccessProps;

public class CodeEditorWidget extends CodeEditor {
    public static final String TAG = CodeEditorWidget.class.getSimpleName();

    public CodeEditorWidget(Context context) {
        super(context);
    }

    public CodeEditorWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CodeEditorWidget(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CodeEditorWidget(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void pasteText() {
        try {
            CharSequence data = ClipboardUtils.readClipboard(getContext());
            BaseInputConnection inputConnection = getInputConnection();
            TextRange lastInsertion = getLastInsertion();
            if (data != null && inputConnection != null) {
                String text = data.toString();
                inputConnection.commitText(text, 1);
                if (getProps().formatPastedText) {
                    formatCodeAsync(lastInsertion.getStart(), lastInsertion.getEnd());
                }
                notifyIMEExternalCursorChange();
            }
        } catch (Exception e) {
            Log.w(TAG, e);
            Toast.makeText(getContext(), e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    public void copyText() {
        copyText(true);
    }

    public void copyText(boolean shouldCopyLine) {
        Cursor cursor = getCursor();
        if (cursor.isSelected()) {
            String clip = getText().substring(cursor.getLeft(), cursor.getRight());
            Utils.copyToClipboard(getContext(), "text", clip);
        } else if (shouldCopyLine) {
            copyLine();
        }
    }

    private void copyLine() {
        final Cursor cursor = getCursor();
        if (cursor.isSelected()) {
            copyText();
            return;
        }
        final int line = cursor.left().line;
        setSelectionRegion(line, 0, line, getText().getColumnCount(line));
        copyText(false);
    }

    public BaseInputConnection getInputConnection() {
        try {
            // Get the Class object of the superclass
            Class<?> superClass = this.getClass().getSuperclass();

            // Get the private field from the superclass
            Field field = superClass.getDeclaredField("inputConnection");

            // Make the field accessible
            field.setAccessible(true);

            // Read the value of the private field for this object
            return (BaseInputConnection) field.get(this);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public TextRange getLastInsertion() {
        try {
            // Get the Class object of the superclass
            Class<?> superClass = this.getClass().getSuperclass();

            // Get the private field from the superclass
            Field field = superClass.getDeclaredField("lastInsertion");

            // Make the field accessible
            field.setAccessible(true);

            // Read the value of the private field for this object
            return (TextRange) field.get(this);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public DirectAccessProps getProps() {
        try {
            // Get the Class object of the superclass
            Class<?> superClass = this.getClass().getSuperclass();

            // Get the private field from the superclass
            Field field = superClass.getDeclaredField("props");

            // Make the field accessible
            field.setAccessible(true);

            // Read the value of the private field for this object
            return (DirectAccessProps) field.get(this);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
