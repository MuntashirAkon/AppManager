package io.github.muntashirakon.AppManager.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.utils.Utils;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.jaredrummler.android.shell.CommandResult;
import com.jaredrummler.android.shell.Shell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunningAppsActivity extends AppCompatActivity {
    // "^(?<label>[^\\t\\s]+)[\\t\\s]+(?<pid>\\d+)[\\t\\s]+(?<ppid>\\d+)[\\t\\s]+(?<rss>\\d+)[\\t\\s]+(?<vsz>\\d+)[\\t\\s]+(?<user>[^\\t\\s]+)[\\t\\s]+(?<uid>\\d+)[\\t\\s]+(?<state>\\w)(?<stateplus>[\\w\\+<])?[\\t\\s]+(?<name>[^\\t\\s]+)$"
    private static final Pattern PROCESS_MATCHER = Pattern.compile("^(?<label>[^\\t\\s]+)[\\t\\s]+(?<pid>\\d+)[\\t\\s]+(?<ppid>\\d+)[\\t\\s]+(?<rss>\\d+)[\\t\\s]+(?<vsz>\\d+)[\\t\\s]+(?<user>[^\\t\\s]+)[\\t\\s]+(?<uid>\\d+)[\\t\\s]+(?<state>\\w)(?<stateplus>[\\w\\+<])?[\\t\\s]+(?<name>[^\\t\\s]+)$");

    private static String mConstraint;

    private ListView mListView;
    private RunningAppsAdapter mAdapter;
    private PackageManager mPackageManager;
    private ProgressBar mProgressBar;

    static class ProcessItem {
        int pid;
        int ppid;
        long rss;
        long vsz;
        String user;
        int uid;
        String state;
        String state_extra;
        String name;
        ApplicationInfo applicationInfo = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running_apps);

        mPackageManager = getPackageManager();
        mProgressBar = findViewById(R.id.progress_horizontal);
        mListView = findViewById(android.R.id.list);
        mListView.setTextFilterEnabled(true);
        mListView.setDividerHeight(0);
        mListView.setEmptyView(findViewById(android.R.id.empty));
        mAdapter = new RunningAppsAdapter(this);
        mListView.setAdapter(mAdapter);
        new Thread(() -> {
            List<ApplicationInfo> applicationInfoList = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            List<String> pkgNames = new ArrayList<>();
            CommandResult result = Shell.SU.run("ps -dwZ -o PID,PPID,RSS,VSZ,USER,UID,STAT,NAME | grep -v :kernel:");
            if (result.isSuccessful()) {
                List<String> processInfoLines = result.stdout;
                HashMap<String, ProcessItem> processList = new HashMap<>();
                for (int i = 1; i<processInfoLines.size(); ++i) {
                    try {
                        ProcessItem processItem = parseProcess(processInfoLines.get(i));
                        processList.put(processItem.name, processItem);
                    } catch (Exception ignore) {}
                }
                Set<String> processNames = processList.keySet();
                for (ApplicationInfo applicationInfo : applicationInfoList) {
                    if (processNames.contains(applicationInfo.packageName)) {
                        //noinspection ConstantConditions
                        processList.get(applicationInfo.packageName).applicationInfo = applicationInfo;
                        pkgNames.add(applicationInfo.loadLabel(mPackageManager).toString());
                    }
                }
                Collection<ProcessItem> processItemList = processList.values();
                runOnUiThread(() -> {
                    mAdapter.setDefaultList(pkgNames);
                    mProgressBar.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    @NonNull
    private static ProcessItem parseProcess(String line) throws Exception {
        Matcher matcher = PROCESS_MATCHER.matcher(line);
        if (matcher.find()) {
            ProcessItem processItem = new ProcessItem();
            //noinspection ConstantConditions
            processItem.pid = Integer.parseInt(matcher.group(2));
            //noinspection ConstantConditions
            processItem.ppid = Integer.parseInt(matcher.group(3));
            //noinspection ConstantConditions
            processItem.rss = Integer.parseInt(matcher.group(4));
            //noinspection ConstantConditions
            processItem.vsz = Integer.parseInt(matcher.group(5));
            processItem.user = matcher.group(6);
            //noinspection ConstantConditions
            processItem.uid = Integer.parseInt(matcher.group(7));
            //noinspection ConstantConditions
            processItem.state = Utils.getProcessStateName(matcher.group(8));
            processItem.state_extra = Utils.getProcessStateExtraName(matcher.group(9));
            processItem.name = matcher.group(10);
            return processItem;
        }
        throw new Exception("Failed to parse line");
    }

    static class RunningAppsAdapter extends BaseAdapter implements Filterable {
        private LayoutInflater mLayoutInflater;
        private Filter mFilter;
        private String mConstraint;
        private List<String> mDefaultList;
        private List<String> mAdapterList;

        private int mColorTransparent;
        private int mColorSemiTransparent;
        private int mColorRed;

        RunningAppsAdapter(@NonNull Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.SEMI_TRANSPARENT);
            mColorRed = ContextCompat.getColor(activity, R.color.red);
        }

        void setDefaultList(List<String> list) {
            mDefaultList = list;
            mAdapterList = list;
            if(RunningAppsActivity.mConstraint != null
                    && !RunningAppsActivity.mConstraint.equals("")) {
                getFilter().filter(RunningAppsActivity.mConstraint);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        @Override
        public String getItem(int position) {
            return mAdapterList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mDefaultList.indexOf(mAdapterList.get(position));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            String className = mAdapterList.get(position);
            TextView textView = (TextView) convertView;
            if (mConstraint != null && className.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                textView.setText(Utils.getHighlightedText(className, mConstraint, mColorRed));
            } else {
                textView.setText(className);
            }
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

                        List<String> list = new ArrayList<>(mDefaultList.size());
                        for (String item : mDefaultList) {
                            if (item.toLowerCase(Locale.ROOT).contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list;
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            //noinspection unchecked
                            mAdapterList = (List<String>) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }
    }
}
