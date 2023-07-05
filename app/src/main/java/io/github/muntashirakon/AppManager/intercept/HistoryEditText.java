// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.adapters.NoFilterArrayAdapter;
import io.github.muntashirakon.widget.MaterialAutoCompleteTextView;

public class HistoryEditText {
    private static final String DELIMITER = "';'";
    private static final int MAX_HISTORY_SIZE = 8;
    private static final String HISTORY_PREFIX = "ActivityInterceptor_history_";

    private final Activity mContext;
    private final EditorHandler[] mEditorHandlers;

    /**
     * ContextActionBar for one EditText
     */
    protected class EditorHandler {
        private final MaterialAutoCompleteTextView mEditor;
        private final String mId;

        public EditorHandler(String id, MaterialAutoCompleteTextView editor) {
            mId = id;
            mEditor = editor;
            showHistory();
        }

        public String toString(SharedPreferences pref) {
            return mId + " : '" + getHistory(pref) + "'";
        }

        protected void showHistory() {
            List<String> items = getHistoryItems();
            ArrayAdapter<String> adapter = new NoFilterArrayAdapter<>(mContext,
                    io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, items);
            mEditor.setAdapter(adapter);
        }

        @NonNull
        private List<String> getHistoryItems() {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            return getHistory(sharedPref);
        }

        @NonNull
        private List<String> getHistory(@NonNull SharedPreferences sharedPref) {
            String history = sharedPref.getString(mId, "");
            return asList(history == null ? "" : history);
        }

        protected void saveHistory(@NonNull SharedPreferences sharedPref, @NonNull SharedPreferences.Editor edit) {
            List<String> history = getHistory(sharedPref);
            history = include(history, mEditor.getText().toString().trim());
            String result = toString(history);
            edit.putString(mId, result);
        }

        @NonNull
        private List<String> asList(@NonNull String serialistedListElements) {
            String[] items = serialistedListElements.split(DELIMITER);
            return Arrays.asList(items);
        }

        @NonNull
        private String toString(List<String> list) {
            StringBuilder result = new StringBuilder();
            if (list != null) {
                String nextDelim = "";
                for (Object instance : list) {
                    if (instance != null) {
                        String instanceString = instance.toString().trim();
                        if (instanceString.length() > 0) {
                            result.append(nextDelim).append(instanceString);
                            nextDelim = DELIMITER;
                        }
                    }
                }
            }
            return result.toString();
        }

        @NonNull
        private List<String> include(List<String> history_, String newValue) {
            List<String> history = new ArrayList<>(history_);
            if ((newValue != null) && (newValue.length() > 0)) {
                history.remove(newValue);
                history.add(0, newValue);
            }

            int len = history.size();

            // forget oldest entries if maxHisotrySize is reached
            while (len > MAX_HISTORY_SIZE) {
                len--;
                history.remove(len);
            }
            return history;
        }
    }

    /**
     * define history function for these editors
     */
    public HistoryEditText(@NonNull Activity context, @NonNull MaterialAutoCompleteTextView... editors) {
        mContext = context;
        mEditorHandlers = new EditorHandler[editors.length];

        for (int i = 0; i < editors.length; i++) {
            mEditorHandlers[i] = createHandler(HISTORY_PREFIX + i, editors[i]);
        }
    }

    protected EditorHandler createHandler(String id, MaterialAutoCompleteTextView editor) {
        return new EditorHandler(id, editor);
    }

    /**
     * include current editor-content to history and save to settings
     */
    public void saveHistory() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor edit = sharedPref.edit();

        for (EditorHandler instance : mEditorHandlers) {
            instance.saveHistory(sharedPref, edit);
        }
        edit.apply();
    }

    @NonNull
    @Override
    public String toString() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        StringBuilder result = new StringBuilder();
        for (EditorHandler instance : mEditorHandlers) {
            result.append(instance.toString(sharedPref)).append("\n");
        }
        return result.toString();
    }
}
