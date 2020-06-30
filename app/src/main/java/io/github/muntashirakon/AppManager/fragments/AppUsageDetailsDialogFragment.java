package io.github.muntashirakon.AppManager.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.activities.AppUsageActivity;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;

public class AppUsageDetailsDialogFragment extends DialogFragment {
    public static final String TAG = "AppUsageDetailsDialogFragment";
    public static final String ARG_PACKAGE_US = "ARG_PACKAGE_US";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (getActivity() == null || getContext() == null || getArguments() == null)
            return super.onCreateDialog(savedInstanceState);
        AppUsageStatsManager.PackageUS packageUS = getArguments().getParcelable(ARG_PACKAGE_US);
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (packageUS == null || inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_app_usage_details, null);
        ListView listView = view.findViewById(android.R.id.list);
        listView.setDividerHeight(0);
        TextView emptyView = view.findViewById(android.R.id.empty);
        listView.setEmptyView(emptyView);
        AppUsageDetailsAdapter adapter = new AppUsageDetailsAdapter(getActivity());
        listView.setAdapter(adapter);
        adapter.setDefaultList(packageUS.entries);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.CustomDialog)
                .setTitle(packageUS.packageName)
                .setView(view)
                .setNegativeButton(android.R.string.ok, (dialog, which) -> {
                    if (getDialog() == null) dismiss();
                });
        try {
            PackageManager packageManager = getActivity().getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageUS.packageName, PackageManager.GET_META_DATA);
            builder.setIcon(applicationInfo.loadIcon(packageManager));
            builder.setTitle(applicationInfo.loadLabel(packageManager));
        } catch (PackageManager.NameNotFoundException e) {
            builder.setTitle(packageUS.packageName);
        }
        return builder.create();
    }

    static class AppUsageDetailsAdapter extends BaseAdapter {
        static DateFormat sSimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

        private LayoutInflater mLayoutInflater;
        private List<AppUsageStatsManager.USEntry> mDefaultList;
        private List<AppUsageStatsManager.USEntry> mAdapterList;
        private Context context;

        private int mColorTransparent;
        private int mColorSemiTransparent;

        AppUsageDetailsAdapter(@NonNull Activity activity) {
            context = activity;
            mLayoutInflater = activity.getLayoutInflater();
            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.SEMI_TRANSPARENT);
        }

        void setDefaultList(List<AppUsageStatsManager.USEntry> list) {
            mDefaultList = list;
            mAdapterList = list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        @Override
        public AppUsageStatsManager.USEntry getItem(int position) {
            return mAdapterList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mDefaultList.indexOf(mAdapterList.get(position));
        }

        static class ViewHolder {
            TextView title;
            TextView subtitle;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_usage_details, parent, false);
                holder = new ViewHolder();
                holder.title = convertView.findViewById(R.id.item_title);
                holder.subtitle = convertView.findViewById(R.id.item_subtitle);
                convertView.setTag(holder);
            } else holder = (ViewHolder) convertView.getTag();
            AppUsageStatsManager.USEntry usEntry = mAdapterList.get(position);
            holder.title.setText(String.format(Locale.ROOT, "%s - %s", sSimpleDateFormat.format(usEntry.startTime), sSimpleDateFormat.format(usEntry.endTime)));
            holder.subtitle.setText(AppUsageActivity.formattedTime(context, usEntry.getDuration()));
            convertView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            return convertView;
        }
    }
}
