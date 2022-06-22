// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class WhatsNewDialogFragment extends DialogFragment {
    public static final String TAG = WhatsNewDialogFragment.class.getSimpleName();
    public static final String ARG_NEW_PKG_INFO = "ARG_NEW_PKG_INFO";
    public static final String ARG_OLD_PKG_INFO = "ARG_OLD_PKG_INFO";
    public static final String ARG_INSTALL_NAME = "ARG_INSTALL_NAME";
    public static final String ARG_VERSION_INFO = "ARG_VERSION_INFO";

    public interface InstallInterface {
        void triggerInstall();

        void triggerCancel();
    }

    public void setOnTriggerInstall(InstallInterface installInterface) {
        this.mInstallInterface = installInterface;
    }

    private InstallInterface mInstallInterface;
    private FragmentActivity mActivity;
    private WhatsNewRecyclerAdapter mAdapter;
    private PackageInfo mNewPkgInfo;
    private PackageInfo mOldPkgInfo;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        WhatsNewDialogViewModel viewModel = new ViewModelProvider(this).get(WhatsNewDialogViewModel.class);
        mActivity = requireActivity();
        mNewPkgInfo = requireArguments().getParcelable(ARG_NEW_PKG_INFO);
        mOldPkgInfo = requireArguments().getParcelable(ARG_OLD_PKG_INFO);
        final String installName = requireArguments().getString(ARG_INSTALL_NAME);
        String versionInfo = requireArguments().getString(ARG_VERSION_INFO);
        View view = View.inflate(mActivity, R.layout.dialog_whats_new, null);
        RecyclerView recyclerView = (RecyclerView) view;
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        mAdapter = new WhatsNewRecyclerAdapter();
        recyclerView.setAdapter(mAdapter);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.whats_new)
                .setView(view);
        if (mInstallInterface != null) {
            PackageManager pm = mActivity.getPackageManager();
            builder.setCustomTitle(UIUtils.getDialogTitle(requireActivity(),
                            pm.getApplicationLabel(mNewPkgInfo.applicationInfo),
                            pm.getApplicationIcon(mNewPkgInfo.applicationInfo),
                            versionInfo))
                    .setNegativeButton(R.string.cancel, (dialog, which) -> mInstallInterface.triggerCancel())
                    .setPositiveButton(installName, (dialog, which) -> mInstallInterface.triggerInstall());
        } else {
            builder.setNegativeButton(R.string.ok, null);
        }
        viewModel.getChangesLiveData().observe(this, changes -> mAdapter.setAdapterList(changes));
        viewModel.loadChanges(mNewPkgInfo, mOldPkgInfo);
        return builder.create();
    }

    @Override
    public void show(@NonNull FragmentManager manager, @Nullable String tag) {
        FragmentTransaction ft = manager.beginTransaction();
        ft.add(this, tag);
        ft.commitAllowingStateLoss();
    }

    class WhatsNewRecyclerAdapter extends RecyclerView.Adapter<WhatsNewRecyclerAdapter.ViewHolder> {
        private final List<ApkWhatsNewFinder.Change> mAdapterList = new ArrayList<>();
        private final int colorAdd;
        private final int colorRemove;
        private final int colorNeutral;
        private final Typeface typefaceNormal;
        private final Typeface typefaceMedium;

        WhatsNewRecyclerAdapter() {
            colorAdd = ContextCompat.getColor(mActivity, R.color.stopped);
            colorRemove = ContextCompat.getColor(mActivity, R.color.electric_red);
            colorNeutral = UIUtils.getTextColorPrimary(mActivity);
            typefaceNormal = Typeface.create("sans-serif", Typeface.NORMAL);
            typefaceMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }

        void setAdapterList(List<ApkWhatsNewFinder.Change> list) {
            mAdapterList.clear();
            mAdapterList.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_text_view, parent, false);
            return new ViewHolder(view);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ApkWhatsNewFinder.Change change = mAdapterList.get(position);
            if (change.value.startsWith(mNewPkgInfo.packageName)) {
                change.value = change.value.replaceFirst(mNewPkgInfo.packageName, "");
            }
            switch (change.changeType) {
                case ApkWhatsNewFinder.CHANGE_ADD:
                    holder.textView.setText("+ " + change.value);
                    holder.textView.setTextColor(colorAdd);
                    holder.textView.setTypeface(typefaceNormal);
                    holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    break;
                case ApkWhatsNewFinder.CHANGE_INFO:
                    holder.textView.setText(change.value);
                    holder.textView.setTextColor(colorNeutral);
                    holder.textView.setTypeface(typefaceMedium);
                    holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    break;
                case ApkWhatsNewFinder.CHANGE_REMOVED:
                    holder.textView.setText("- " + change.value);
                    holder.textView.setTextColor(colorRemove);
                    holder.textView.setTypeface(typefaceNormal);
                    holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return mAdapterList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialTextView textView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = (MaterialTextView) itemView;
            }
        }
    }
}
