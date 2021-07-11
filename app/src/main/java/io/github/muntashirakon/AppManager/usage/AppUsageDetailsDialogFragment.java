// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class AppUsageDetailsDialogFragment extends DialogFragment {
    public static final String TAG = "AppUsageDetailsDialogFragment";
    public static final String ARG_PACKAGE_US = "ARG_PACKAGE_US";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        PackageUsageInfo packageUS = requireArguments().getParcelable(ARG_PACKAGE_US);
        LayoutInflater inflater = LayoutInflater.from(activity);
        if (packageUS == null || inflater == null) return super.onCreateDialog(savedInstanceState);
        View view = inflater.inflate(R.layout.dialog_app_usage_details, null);
        ListView listView = view.findViewById(android.R.id.list);
        listView.setDividerHeight(0);
        TextView emptyView = view.findViewById(android.R.id.empty);
        listView.setEmptyView(emptyView);
        AppUsageDetailsAdapter adapter = new AppUsageDetailsAdapter(activity);
        listView.setAdapter(adapter);
        adapter.setDefaultList(packageUS.entries);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(packageUS.packageName)
                .setView(view)
                .setNegativeButton(R.string.ok, (dialog, which) -> {
                    if (getDialog() == null) dismiss();
                });
        try {
            PackageManager packageManager = activity.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageUS.packageName, PackageManager.GET_META_DATA);
            builder.setIcon(applicationInfo.loadIcon(packageManager));
            builder.setTitle(applicationInfo.loadLabel(packageManager));
        } catch (PackageManager.NameNotFoundException e) {
            builder.setTitle(packageUS.packageName);
        }
        return builder.create();
    }

    static class AppUsageDetailsAdapter extends BaseAdapter {
        private final LayoutInflater mLayoutInflater;
        private List<PackageUsageInfo.Entry> mDefaultList;
        private List<PackageUsageInfo.Entry> mAdapterList;
        private final Context context;

        private final int mColorTransparent;
        private final int mColorSemiTransparent;

        AppUsageDetailsAdapter(@NonNull Activity activity) {
            context = activity;
            mLayoutInflater = activity.getLayoutInflater();
            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.semi_transparent);
        }

        void setDefaultList(List<PackageUsageInfo.Entry> list) {
            mDefaultList = list;
            mAdapterList = list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        @Override
        public PackageUsageInfo.Entry getItem(int position) {
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
            PackageUsageInfo.Entry entry = mAdapterList.get(position);
            holder.title.setText(String.format(Locale.ROOT, "%s - %s", DateUtils.formatDateTime(entry.startTime), DateUtils.formatDateTime(entry.endTime)));
            holder.subtitle.setText(Utils.getFormattedDuration(context, entry.getDuration()));
            convertView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            return convertView;
        }
    }
}
