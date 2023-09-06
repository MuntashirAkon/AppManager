// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.settings.InstallerPreferences.INSTALL_LOCATIONS;
import static io.github.muntashirakon.AppManager.settings.InstallerPreferences.INSTALL_LOCATION_NAMES;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.Manifest;
import android.app.Application;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.adapters.SelectedArrayAdapter;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;
import io.github.muntashirakon.view.TextInputLayoutCompat;
import io.github.muntashirakon.widget.MaterialSpinner;

public class InstallerOptionsFragment extends DialogFragment {
    public static final String TAG = InstallerOptionsFragment.class.getSimpleName();

    private static final String ARG_PACKAGE_NAME = "pkg";
    private static final String ARG_TEST_ONLY_APP = "test_only";
    private static final String ARG_REF_INSTALLER_OPTIONS = "ref_opt";

    public interface OnClickListener {
        void onClick(DialogInterface dialog, int which, @Nullable InstallerOptions options);
    }

    @NonNull
    public static InstallerOptionsFragment getInstance(@Nullable String packageName,
                                                       @Nullable Boolean isTestOnly,
                                                       @NonNull InstallerOptions options,
                                                       @Nullable OnClickListener clickListener) {
        InstallerOptionsFragment dialog = new InstallerOptionsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE_NAME, packageName);
        if (isTestOnly != null) {
            args.putBoolean(ARG_TEST_ONLY_APP, isTestOnly);
        }
        args.putParcelable(ARG_REF_INSTALLER_OPTIONS, options);
        dialog.setArguments(args);
        dialog.setOnClickListener(clickListener);
        return dialog;
    }

    private InstallerOptionsViewModel mModel;
    private View mDialogView;
    private MaterialSpinner mUserSelectionSpinner;
    private MaterialSpinner mInstallLocationSpinner;
    private TextInputLayout mInstallerAppLayout;
    private EditText mInstallerAppField;
    private MaterialSwitch mBlockTrackersSwitch;
    @Nullable
    private OnClickListener mClickListener;
    private String mPackageName;
    private boolean mIsTestOnly;
    private InstallerOptions mOptions;
    private PackageManager mPm;

    public void setOnClickListener(@Nullable OnClickListener clickListener) {
        this.mClickListener = clickListener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(this).get(InstallerOptionsViewModel.class);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mPackageName = requireArguments().getString(ARG_PACKAGE_NAME);
        mIsTestOnly = requireArguments().getBoolean(ARG_TEST_ONLY_APP, true);
        mOptions = Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_REF_INSTALLER_OPTIONS, InstallerOptions.class));
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_installer_options, null);
        mUserSelectionSpinner = mDialogView.findViewById(R.id.user);
        mInstallLocationSpinner = mDialogView.findViewById(R.id.install_location);
        mInstallerAppLayout = mDialogView.findViewById(R.id.installer);
        mInstallerAppField = Objects.requireNonNull(mInstallerAppLayout.getEditText());
        MaterialSwitch signApkSwitch = mDialogView.findViewById(R.id.action_sign_apk);
        MaterialSwitch forceDexOptSwitch = mDialogView.findViewById(R.id.action_optimize);
        mBlockTrackersSwitch = mDialogView.findViewById(R.id.action_block_trackers);
        // Set values and defaults
        mPm = requireContext().getPackageManager();
        boolean canInstallForOtherUsers = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL);
        int selectedUser = getSelectedUserId(canInstallForOtherUsers);
        boolean canBlockTrackers = SelfPermissions.canModifyAppComponentStates(selectedUser, mPackageName, mIsTestOnly);
        initUserSpinner(canInstallForOtherUsers);
        initInstallLocationSpinner();
        initInstallerAppSpinner();
        signApkSwitch.setChecked(mOptions.isSignApkFiles());
        signApkSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.setSignApkFiles(isChecked));
        forceDexOptSwitch.setVisibility(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? View.VISIBLE : View.GONE);
        forceDexOptSwitch.setChecked(mOptions.isForceDexOpt());
        forceDexOptSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.setForceDexOpt(isChecked));
        mBlockTrackersSwitch.setChecked(canBlockTrackers && mOptions.isBlockTrackers());
        mBlockTrackersSwitch.setEnabled(canBlockTrackers);
        mBlockTrackersSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.setBlockTrackers(isChecked));
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.installer_options)
                .setView(mDialogView)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    if (mClickListener != null) {
                        mClickListener.onClick(dialog, which, mOptions);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    if (mClickListener != null) {
                        mClickListener.onClick(dialog, which, null);
                    }
                })
                .create();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return mDialogView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mModel.getPackageNameLabelPairLiveData().observe(getViewLifecycleOwner(), this::displayInstallerAppSelectionDialog);
    }

    private int getSelectedUserId(boolean canInstallForOtherUsers) {
        return canInstallForOtherUsers ? mOptions.getUserId() : UserHandleHidden.myUserId();
    }

    private void initUserSpinner(boolean canInstallForOtherUsers) {
        int selectedUser = getSelectedUserId(canInstallForOtherUsers);
        List<UserInfo> userInfoList = Users.getUsers();
        CharSequence[] userNames = new String[userInfoList.size() + 1];
        Integer[] userIds = new Integer[userInfoList.size() + 1];
        userNames[0] = getString(R.string.backup_all_users);
        userIds[0] = UserHandleHidden.USER_ALL;
        int i = 1;
        int selectedUserPosition = 0;
        for (UserInfo info : userInfoList) {
            userNames[i] = info.toLocalizedString(requireContext());
            userIds[i] = info.id;
            if (selectedUser == info.id) {
                selectedUserPosition = i;
            }
            ++i;
        }
        ArrayAdapter<CharSequence> userAdapter = new SelectedArrayAdapter<>(requireContext(),
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item_small, userNames);
        mUserSelectionSpinner.setAdapter(userAdapter);
        mUserSelectionSpinner.setSelection(selectedUserPosition);
        mUserSelectionSpinner.setOnItemClickListener((parent, view, position, id) -> {
            mOptions.setUserId(userIds[position]);
            // Update block trackers option
            boolean canBlockTrackers = SelfPermissions.canModifyAppComponentStates(selectedUser, mPackageName, mIsTestOnly);
            mBlockTrackersSwitch.setChecked(canBlockTrackers && mOptions.isBlockTrackers());
            mBlockTrackersSwitch.setEnabled(canBlockTrackers);
        });
        mUserSelectionSpinner.setEnabled(canInstallForOtherUsers);
    }

    private void initInstallLocationSpinner() {
        int installLocation = mOptions.getInstallLocation();
        int installLocationPosition = installLocation;
        CharSequence[] installLocationNames = new CharSequence[INSTALL_LOCATIONS.length];
        for (int i = 0; i < INSTALL_LOCATIONS.length; ++i) {
            installLocationNames[i] = getString(INSTALL_LOCATION_NAMES[i]);
            if (INSTALL_LOCATIONS[i] == installLocation) {
                installLocationPosition = i;
            }
        }
        ArrayAdapter<CharSequence> installerLocationAdapter = new SelectedArrayAdapter<>(requireContext(),
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item_small, installLocationNames);
        mInstallLocationSpinner.setAdapter(installerLocationAdapter);
        mInstallLocationSpinner.setSelection(installLocationPosition);
        mInstallLocationSpinner.setOnItemClickListener((parent, view, position, id) ->
                mOptions.setInstallLocation(INSTALL_LOCATIONS[position]));
    }

    private void initInstallerAppSpinner() {
        boolean canInstallApps = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES);
        String installer = canInstallApps ? mOptions.getInstallerName() : BuildConfig.APPLICATION_ID;
        mInstallerAppField.setText(PackageUtils.getPackageLabel(mPm, installer));
        TextInputLayoutCompat.fixEndIcon(mInstallerAppLayout);
        mInstallerAppLayout.setEnabled(canInstallApps);
        mInstallerAppLayout.setEndIconOnClickListener(view -> new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.installer_app)
                .setMessage(R.string.installer_app_message)
                .setPositiveButton(R.string.choose, (dialog1, which1) -> mModel.loadPackageNameLabelPair())
                .setNegativeButton(R.string.specify_custom_name, (dialog, which) ->
                        new TextInputDialogBuilder(requireActivity(), R.string.installer_app)
                                .setTitle(R.string.installer_app)
                                .setInputText(mOptions.getInstallerName())
                                .setPositiveButton(R.string.ok, (dialog1, which1, inputText, isChecked) -> {
                                    if (inputText == null) return;
                                    String installerApp = inputText.toString().trim();
                                    if (!TextUtils.isEmpty(installerApp)) {
                                        mOptions.setInstallerName(installerApp);
                                        mInstallerAppField.setText(PackageUtils.getPackageLabel(mPm, installerApp));
                                    }
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .show())
                .setNeutralButton(R.string.reset_to_default, (dialog, which) -> {
                    String installerApp = Prefs.Installer.getInstallerPackageName();
                    mOptions.setInstallerName(installerApp);
                    mInstallerAppField.setText(PackageUtils.getPackageLabel(mPm, installerApp));
                })
                .show());
    }

    public void displayInstallerAppSelectionDialog(@NonNull List<Pair<String, CharSequence>> appInfo) {
        ArrayList<String> items = new ArrayList<>(appInfo.size());
        ArrayList<CharSequence> itemNames = new ArrayList<>(appInfo.size());
        for (Pair<String, CharSequence> pair : appInfo) {
            items.add(pair.first);
            itemNames.add(new SpannableStringBuilder(pair.second)
                    .append("\n")
                    .append(getSecondaryText(requireContext(), getSmallerText(pair.first))));
        }
        new SearchableSingleChoiceDialogBuilder<>(requireActivity(), items, itemNames)
                .setTitle(R.string.installer_app)
                .setSelection(mOptions.getInstallerName())
                .setPositiveButton(R.string.save, (dialog, which, selectedInstallerApp) -> {
                    if (selectedInstallerApp != null) {
                        String installerApp = selectedInstallerApp.trim();
                        if (!TextUtils.isEmpty(installerApp)) {
                            mOptions.setInstallerName(installerApp);
                            mInstallerAppField.setText(PackageUtils.getPackageLabel(mPm, installerApp));
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static class InstallerOptionsViewModel extends AndroidViewModel {
        private final MutableLiveData<List<Pair<String, CharSequence>>> mPackageNameLabelPairLiveData = new SingleLiveEvent<>();

        public InstallerOptionsViewModel(@NonNull Application application) {
            super(application);
        }

        public LiveData<List<Pair<String, CharSequence>>> getPackageNameLabelPairLiveData() {
            return mPackageNameLabelPairLiveData;
        }

        public void loadPackageNameLabelPair() {
            ThreadUtils.postOnBackgroundThread(() -> {
                List<App> appList = new AppDb().getAllApplications();
                Map<String, CharSequence> packageNameLabelMap = new HashMap<>(appList.size());
                for (App app : appList) {
                    packageNameLabelMap.put(app.packageName, app.packageLabel);
                }
                List<Pair<String, CharSequence>> appInfo = new ArrayList<>();
                for (String packageName : packageNameLabelMap.keySet()) {
                    appInfo.add(new Pair<>(packageName, packageNameLabelMap.get(packageName)));
                }
                Collections.sort(appInfo, (o1, o2) -> o1.second.toString().compareTo(o2.second.toString()));
                mPackageNameLabelPairLiveData.postValue(appInfo);
            });
        }
    }
}
