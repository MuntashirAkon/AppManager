package io.github.muntashirakon.AppManager.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.Utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.util.Xml;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.jaredrummler.android.shell.Shell;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class SharedPrefsActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {
    public static final String EXTRA_PREF_LOCATION = "EXTRA_PREF_LOCATION";
    public static final String EXTRA_PREF_LABEL    = "EXTRA_PREF_LABEL";  // Optional

    private static final String TAG_ROOT     = "map";  // <map></map>
    private static final String TYPE_BOOLEAN = "boolean";  // <boolean name="bool" value="true" />
    private static final String TYPE_FLOAT   = "float";  // <float name="float" value="12.3" />
    private static final String TYPE_INTEGER = "int";  // <int name="integer" value="123" />
    private static final String TYPE_LONG    = "long";  // <long name="long" value="123456789" />
    private static final String TYPE_STRING  = "string";  // <string name="string"></string> | <string name="string"><string></string></string>

    private String mSharedPrefFile;
    private File mTempSharedPrefFile;
    private ListView mListView;
    private SharedPrefsListingAdapter mAdapter;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shared_prefs);
        mSharedPrefFile = getIntent().getStringExtra(EXTRA_PREF_LOCATION);
        String appLabel = getIntent().getStringExtra(EXTRA_PREF_LABEL);
        if (mSharedPrefFile == null) {
            finish();
            return;
        }
        String fileName =  (new File(mSharedPrefFile)).getName();
        try {
            mTempSharedPrefFile = File.createTempFile(fileName, ".xml");
        } catch (IOException e) {
            e.printStackTrace();
            finish();
            return;
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(appLabel != null ? appLabel : "Shared Preferences Viewer");
            actionBar.setSubtitle(fileName);
            actionBar.setDisplayShowCustomEnabled(true);
            SearchView searchView = new SearchView(actionBar.getThemedContext());
            searchView.setOnQueryTextListener(this);
            searchView.setQueryHint(getString(R.string.search));

            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_button))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));
            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_close_btn))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));

            ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.END;
            actionBar.setCustomView(searchView, layoutParams);
        }
        mProgressBar = findViewById(R.id.progress_horizontal);
        mProgressBar.setVisibility(View.VISIBLE);
        mListView = findViewById(R.id.listView);
        mAdapter = new SharedPrefsListingAdapter(this);
        mListView.setAdapter(mAdapter);
        new SharedPrefsLoaderThread().start();
        // TODO:
        //  - Edit temporary file with Editor
        //  - A floating action button for adding new entry [INSERT]
        //  - Edit button in the right side of each entry [UPDATE]
        //  - Swipe right to delete an entry (also in the edit dialog) [DELETE]
        //  - Menu actions: save [COMMIT], delete, cancel
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
        super.onDestroy();
        if (mTempSharedPrefFile != null && mTempSharedPrefFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            mTempSharedPrefFile.delete();
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (mAdapter != null)
            mAdapter.getFilter().filter(newText);
        return true;
    }

    @NonNull
    private HashMap<String, Object> loadSharedPref(File sharedPrefsFile) {
        HashMap<String, Object> prefs = new HashMap<>();
        try {
            FileInputStream rulesStream = new FileInputStream(sharedPrefsFile);
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
                    attrValue = parser.getAttributeValue(null, "value");
                    switch (tagName) {
                        case TYPE_BOOLEAN:
                            if (attrValue != null) {
                                prefs.put(attrName, attrValue.equals("true"));
                            }
                            event = parser.nextTag();
                            continue;
                        case TYPE_FLOAT:
                            if (attrValue != null) {
                                prefs.put(attrName, Float.valueOf(attrValue));
                            }
                            event = parser.nextTag();
                            continue;
                        case TYPE_INTEGER:
                            if (attrValue != null) {
                                prefs.put(attrName, Integer.valueOf(attrValue));
                            }
                            event = parser.nextTag();
                            continue;
                        case TYPE_LONG:
                            if (attrValue != null) {
                                prefs.put(attrName, Long.valueOf(attrValue));
                            }
                            event = parser.nextTag();
                            continue;
                        case TYPE_STRING:
                            prefs.put(attrName, parser.nextText());
                    }
                }
                event = parser.nextTag();
            }
            rulesStream.close();
        } catch (IOException | XmlPullParserException ignored) {}
        return prefs;
    }

    private class SharedPrefsLoaderThread extends Thread {
        @Override
        public void run() {
            String sharedPrefPath = mTempSharedPrefFile.getAbsolutePath();
            if(!Shell.SU.run(String.format("cp '%s' '%s' && chmod 0666 '%s'", mSharedPrefFile,
                    sharedPrefPath, sharedPrefPath)).isSuccessful()) {
                runOnUiThread(SharedPrefsActivity.this::finish);
            }
            HashMap<String, Object> hashMap = loadSharedPref(mTempSharedPrefFile);
            runOnUiThread(() -> {
                mAdapter.setDefaultList(hashMap);
                mProgressBar.setVisibility(View.GONE);
            });
        }
    }

    static class SharedPrefsListingAdapter extends BaseAdapter implements Filterable {
        static final Spannable.Factory sSpannableFactory = Spannable.Factory.getInstance();

        private LayoutInflater mLayoutInflater;
        private Filter mFilter;
        private String mConstraint;
        private String[] mDefaultList;
        private String[] mAdapterList;
        private HashMap<String, Object> mAdapterMap;

        private int mColorTransparent;
        private int mColorSemiTransparent;
        private int mColorRed;

        static class ViewHolder {
            TextView item_name;
            TextView item_value;
        }

        SharedPrefsListingAdapter(@NonNull Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = activity.getResources().getColor(R.color.SEMI_TRANSPARENT);
            mColorRed = activity.getResources().getColor(R.color.red);
        }

        void setDefaultList(@NonNull HashMap<String, Object> list) {
            mDefaultList = list.keySet().toArray(new String[0]);
            mAdapterList = mDefaultList;
            mAdapterMap = list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.length;
        }

        @Override
        public Object getItem(int position) {
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

            if (mConstraint != null && prefName.toLowerCase().contains(mConstraint)) {
                // Highlight searched query
                viewHolder.item_name.setText(getHighlightedText(prefName));
            } else {
                viewHolder.item_name.setText(prefName);
            }
            viewHolder.item_value.setText(Objects.requireNonNull(mAdapterMap.get(mAdapterList[position])).toString());
            convertView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            return convertView;
        }

        Spannable getHighlightedText(String s) {
            Spannable spannable = sSpannableFactory.newSpannable(s);
            int start = s.toLowerCase().indexOf(mConstraint);
            int end = start + mConstraint.length();
            spannable.setSpan(new BackgroundColorSpan(mColorRed), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spannable;
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = charSequence.toString().toLowerCase();
                        mConstraint = constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<String> list = new ArrayList<>(mDefaultList.length);
                        for (String item : mDefaultList) {
                            if (item.toLowerCase().contains(constraint))
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