/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.sharedpref;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Xml;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyInputStream;
import io.github.muntashirakon.io.ProxyOutputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class SharedPrefsActivity extends BaseActivity implements
        SearchView.OnQueryTextListener, EditPrefItemFragment.InterfaceCommunicator {
    public static final String EXTRA_PREF_LOCATION = "EXTRA_PREF_LOCATION";
    public static final String EXTRA_PREF_LABEL    = "EXTRA_PREF_LABEL";  // Optional

    public static final String TAG_ROOT    = "map";  // <map></map>
    public static final String TAG_BOOLEAN = "boolean";  // <boolean name="bool" value="true" />
    public static final String TAG_FLOAT   = "float";  // <float name="float" value="12.3" />
    public static final String TAG_INTEGER = "int";  // <int name="integer" value="123" />
    public static final String TAG_LONG    = "long";  // <long name="long" value="123456789" />
    public static final String TAG_STRING  = "string";  // <string name="string"></string> | <string name="string"><string></string></string>

    public static final int REASONABLE_STR_SIZE = 200;

    private ProxyFile mSharedPrefFile;
    private SharedPrefsListingAdapter mAdapter;
    private LinearProgressIndicator mProgressIndicator;
    private HashMap<String, Object> mSharedPrefMap;

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_shared_prefs);
        setSupportActionBar(findViewById(R.id.toolbar));
        String sharedPrefFile = getIntent().getStringExtra(EXTRA_PREF_LOCATION);
        String appLabel = getIntent().getStringExtra(EXTRA_PREF_LABEL);
        if (sharedPrefFile == null) {
            finish();
            return;
        }
        mSharedPrefFile = new ProxyFile(sharedPrefFile);
        String fileName =  mSharedPrefFile.getName();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(appLabel != null ? appLabel : "Shared Preferences Viewer");
            actionBar.setSubtitle(fileName);
            actionBar.setDisplayShowCustomEnabled(true);
            UIUtils.setupSearchView(this, actionBar, this);
        }
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        ListView listView = findViewById(android.R.id.list);
        listView.setTextFilterEnabled(true);
        listView.setDividerHeight(0);
        listView.setEmptyView(findViewById(android.R.id.empty));
        mAdapter = new SharedPrefsListingAdapter(this);
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            EditPrefItemFragment.PrefItem prefItem = new EditPrefItemFragment.PrefItem();
            prefItem.keyName = mAdapter.getItem(position);
            prefItem.keyValue = mSharedPrefMap.get(prefItem.keyName);
            EditPrefItemFragment dialogFragment = new EditPrefItemFragment();
            Bundle args = new Bundle();
            args.putParcelable(EditPrefItemFragment.ARG_PREF_ITEM, prefItem);
            args.putInt(EditPrefItemFragment.ARG_MODE, EditPrefItemFragment.MODE_EDIT);
            dialogFragment.setArguments(args);
            dialogFragment.show(getSupportFragmentManager(), EditPrefItemFragment.TAG);
        });
        FloatingActionButton fab = findViewById(R.id.floatingActionButton);
        fab.setOnClickListener(v -> {
            DialogFragment dialogFragment = new EditPrefItemFragment();
            Bundle args = new Bundle();
            args.putInt(EditPrefItemFragment.ARG_MODE, EditPrefItemFragment.MODE_CREATE);
            dialogFragment.setArguments(args);
            dialogFragment.show(getSupportFragmentManager(), EditPrefItemFragment.TAG);
        });
        new SharedPrefsReaderThread().start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_shared_prefs_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void sendInfo(@EditPrefItemFragment.Mode int mode, EditPrefItemFragment.PrefItem prefItem) {
        if (prefItem != null) {
            switch (mode) {
                case EditPrefItemFragment.MODE_CREATE:
                case EditPrefItemFragment.MODE_EDIT:
                    mSharedPrefMap.put(prefItem.keyName, prefItem.keyValue);
                    break;
                case EditPrefItemFragment.MODE_DELETE:
                    mSharedPrefMap.remove(prefItem.keyName);
                    break;
            }
            mAdapter.setDefaultList(mSharedPrefMap);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home || id == R.id.action_discard) {
            finish();
        } else if (id == R.id.action_delete) {
            if (mSharedPrefFile.delete()) {
                Toast.makeText(this, R.string.deleted_successfully, Toast.LENGTH_LONG).show();
                finish();
            } else {
                Toast.makeText(this, R.string.deletion_failed, Toast.LENGTH_LONG).show();
            }
        } else if (id == R.id.action_save) {
            new Thread(() -> {
                if (writeSharedPref(mSharedPrefFile, mSharedPrefMap)) {
                    runOnUiThread(() -> UIUtils.displayLongToast(R.string.saved_successfully));
                } else {
                    runOnUiThread(() -> UIUtils.displayLongToast(R.string.saving_failed));
                }
            }).start();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null && !TextUtils.isEmpty(mAdapter.mConstraint)) {
            mAdapter.getFilter().filter(mAdapter.mConstraint);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (mAdapter != null) mAdapter.getFilter().filter(newText.toLowerCase(Locale.ROOT));
        return true;
    }

    @WorkerThread
    @NonNull
    private HashMap<String, Object> readSharedPref(ProxyFile sharedPrefsFile) {
        HashMap<String, Object> prefs = new HashMap<>();
        try {
            InputStream rulesStream = new ProxyInputStream(sharedPrefsFile);
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(rulesStream, null);
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, TAG_ROOT);
            int event = parser.nextTag();
            String tagName, attrName, attrValue;
            while (event != XmlPullParser.END_DOCUMENT) {
                tagName = parser.getName();
                if (event == XmlPullParser.START_TAG) {
                    attrName = parser.getAttributeValue(null, "name");
                    if (attrName == null) attrName = "";
                    attrValue = parser.getAttributeValue(null, "value");
                    switch (tagName) {
                        case TAG_BOOLEAN:
                            if (attrValue != null) {
                                prefs.put(attrName, attrValue.equals("true"));
                            }
                            event = parser.nextTag();
                            continue;
                        case TAG_FLOAT:
                            if (attrValue != null) {
                                prefs.put(attrName, Float.valueOf(attrValue));
                            }
                            event = parser.nextTag();
                            continue;
                        case TAG_INTEGER:
                            if (attrValue != null) {
                                prefs.put(attrName, Integer.valueOf(attrValue));
                            }
                            event = parser.nextTag();
                            continue;
                        case TAG_LONG:
                            if (attrValue != null) {
                                prefs.put(attrName, Long.valueOf(attrValue));
                            }
                            event = parser.nextTag();
                            continue;
                        case TAG_STRING:
                            prefs.put(attrName, parser.nextText());
                    }
                }
                event = parser.nextTag();
            }
            rulesStream.close();
        } catch (IOException | RemoteException | XmlPullParserException ignored) {}
        return prefs;
    }

    private class SharedPrefsReaderThread extends Thread {
        @Override
        public void run() {
            mSharedPrefMap = readSharedPref(mSharedPrefFile);
            runOnUiThread(() -> {
                mAdapter.setDefaultList(mSharedPrefMap);
                mProgressIndicator.hide();
            });
        }
    }

    @WorkerThread
    private boolean writeSharedPref(ProxyFile sharedPrefsFile, @NonNull HashMap<String, Object> hashMap) {
        try {
            OutputStream xmlFile = new ProxyOutputStream(sharedPrefsFile);
            XmlSerializer xmlSerializer = Xml.newSerializer();
            StringWriter stringWriter = new StringWriter();
            xmlSerializer.setOutput(stringWriter);
            xmlSerializer.startDocument("UTF-8", true);
            xmlSerializer.startTag("", TAG_ROOT);
            // Add values
            for(String name: hashMap.keySet()) {
                Object value = hashMap.get(name);
                if (value instanceof Boolean) {
                    xmlSerializer.startTag("", TAG_BOOLEAN);
                    xmlSerializer.attribute("", "name", name);
                    xmlSerializer.attribute("", "value", value.toString());
                    xmlSerializer.endTag("", TAG_BOOLEAN);
                } else if (value instanceof Float) {
                    xmlSerializer.startTag("", TAG_FLOAT);
                    xmlSerializer.attribute("", "name", name);
                    xmlSerializer.attribute("", "value", value.toString());
                    xmlSerializer.endTag("", TAG_FLOAT);
                } else if (value instanceof Integer) {
                    xmlSerializer.startTag("", TAG_INTEGER);
                    xmlSerializer.attribute("", "name", name);
                    xmlSerializer.attribute("", "value", value.toString());
                    xmlSerializer.endTag("", TAG_INTEGER);
                } else if (value instanceof Long) {
                    xmlSerializer.startTag("", TAG_LONG);
                    xmlSerializer.attribute("", "name", name);
                    xmlSerializer.attribute("", "value", value.toString());
                    xmlSerializer.endTag("", TAG_LONG);
                } else if (value instanceof String) {
                    xmlSerializer.startTag("", TAG_STRING);
                    xmlSerializer.attribute("", "name", name);
                    xmlSerializer.text(value.toString());
                    xmlSerializer.endTag("", TAG_STRING);
                }
            }
            xmlSerializer.endTag("", TAG_ROOT);
            xmlSerializer.endDocument();
            xmlSerializer.flush();
            xmlFile.write(stringWriter.toString().getBytes());
            xmlFile.close();
            return Runner.runCommand(new String[]{"chmod", "0666", sharedPrefsFile.getAbsolutePath()}).isSuccessful();
        } catch (IOException | RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    static class SharedPrefsListingAdapter extends BaseAdapter implements Filterable {
        private final LayoutInflater mLayoutInflater;
        private Filter mFilter;
        private String mConstraint;
        private String[] mDefaultList;
        private String[] mAdapterList;
        private HashMap<String, Object> mAdapterMap;

        private final int mColorTransparent;
        private final int mColorSemiTransparent;
        private final int mColorRed;

        static class ViewHolder {
            TextView item_name;
            TextView item_value;
        }

        SharedPrefsListingAdapter(@NonNull Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.semi_transparent);
            mColorRed = ContextCompat.getColor(activity, R.color.red);
        }

        void setDefaultList(@NonNull HashMap<String, Object> list) {
            mDefaultList = list.keySet().toArray(new String[0]);
            mAdapterList = mDefaultList;
            mAdapterMap = list;
            if(!TextUtils.isEmpty(mConstraint)) {
                getFilter().filter(mConstraint);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.length;
        }

        @Override
        public String getItem(int position) {
            return mAdapterList[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.item_shared_pref, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.item_name = convertView.findViewById(R.id.item_title);
                viewHolder.item_value = convertView.findViewById(R.id.item_subtitle);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            String prefName = mAdapterList[position];
            if (mConstraint != null && prefName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                viewHolder.item_name.setText(UIUtils.getHighlightedText(prefName, mConstraint, mColorRed));
            } else {
                viewHolder.item_name.setText(prefName);
            }
            Object value = mAdapterMap.get(prefName);
            String strValue = (value != null) ? value.toString() : "";
            viewHolder.item_value.setText(strValue.length() > REASONABLE_STR_SIZE ?
                    strValue.substring(0, REASONABLE_STR_SIZE) : strValue);
            convertView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            return convertView;
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = charSequence.toString().toLowerCase(Locale.ROOT);
                        mConstraint = constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<String> list = new ArrayList<>(mDefaultList.length);
                        for (String item : mDefaultList) {
                            if (item.toLowerCase(Locale.ROOT).contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list.toArray(new String[0]);
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            mAdapterList = (String[]) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }
    }
}