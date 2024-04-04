// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;

public class ModeOfOpsPreference extends Fragment {
    private static final List<String> MODE_NAMES = Arrays.asList(
            Ops.MODE_AUTO,
            Ops.MODE_ROOT,
            Ops.MODE_ADB_OVER_TCP,
            Ops.MODE_ADB_WIFI,
            Ops.MODE_NO_ROOT);

    private MaterialTextView mInferredModeView;
    private MaterialTextView mRemoteServerStatusView;
    private MaterialTextView mRemoteServicesStatusView;
    private MaterialTextView mModeOfOpsView;
    private MainPreferencesViewModel mModel;
    private AlertDialog mModeOfOpsAlertDialog;
    private String[] mModes;
    @Ops.Mode
    private String mCurrentMode;
    private boolean mConnecting;
    @Nullable
    private ColorStateList mColorActive;
    @Nullable
    private ColorStateList mColorInactive;
    @Nullable
    private ColorStateList mColorError;
    @DrawableRes
    private int mIconActive;
    @DrawableRes
    private int mIconInactive;
    @DrawableRes
    private int mIconProgress;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mode_of_ops, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mColorActive = MaterialColors.getColorStateListOrNull(view.getContext(), com.google.android.material.R.attr.colorOnPrimaryContainer);
        mColorInactive = MaterialColors.getColorStateListOrNull(view.getContext(), com.google.android.material.R.attr.colorOutline);
        mColorError = MaterialColors.getColorStateListOrNull(view.getContext(), com.google.android.material.R.attr.colorOnErrorContainer);
        mIconActive = R.drawable.ic_check_circle;
        mIconInactive = io.github.muntashirakon.ui.R.drawable.ic_caution;
        mIconProgress = R.drawable.ic_sync;
        mModeOfOpsAlertDialog = UIUtils.getProgressDialog(requireActivity(), getString(R.string.loading), true);
        mModes = getResources().getStringArray(R.array.modes);
        mCurrentMode = Ops.getMode();
        mInferredModeView = view.findViewById(R.id.inferred_mode);
        mRemoteServerStatusView = view.findViewById(R.id.remote_server_status);
        mRemoteServicesStatusView = view.findViewById(R.id.remote_services_status);
        mModeOfOpsView = view.findViewById(R.id.op_name);
        MaterialButton changeModeView = view.findViewById(R.id.action_settings);
        changeModeView.setOnClickListener(v -> new SearchableSingleChoiceDialogBuilder<>(requireActivity(), MODE_NAMES, mModes)
                .setTitle(R.string.pref_mode_of_operations)
                .setSelection(mCurrentMode)
                .addDisabledItems(Build.VERSION.SDK_INT < Build.VERSION_CODES.R ?
                        Collections.singletonList(Ops.MODE_ADB_WIFI) : Collections.emptyList())
                .setPositiveButton(R.string.apply, (dialog, which, selectedItem) -> {
                    if (selectedItem != null) {
                        mCurrentMode = selectedItem;
                        if (Ops.MODE_ADB_OVER_TCP.equals(mCurrentMode)) {
                            ServerConfig.setAdbPort(ServerConfig.DEFAULT_ADB_PORT);
                        }
                        Ops.setMode(mCurrentMode);
                        mModeOfOpsAlertDialog.show();
                        mConnecting = true;
                        updateViews();
                        mModel.setModeOfOps();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
        updateViews();
        // Mode of ops
        mModel.getModeOfOpsStatus().observe(getViewLifecycleOwner(), status -> {
            switch (status) {
                case Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        updateViews();
                        mModel.autoConnectAdb(Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED);
                        return;
                    } // fall-through
                case Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mModeOfOpsAlertDialog.dismiss();
                        updateViews();
                        Ops.connectWirelessDebugging(requireActivity(), mModel);
                        return;
                    } // fall-through
                case Ops.STATUS_ADB_CONNECT_REQUIRED:
                    mModeOfOpsAlertDialog.dismiss();
                    updateViews();
                    Ops.connectAdbInput(requireActivity(), mModel);
                    return;
                case Ops.STATUS_ADB_PAIRING_REQUIRED:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mModeOfOpsAlertDialog.dismiss();
                        updateViews();
                        Ops.pairAdbInput(requireActivity(), mModel);
                        return;
                    } // fall-through
                case Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS:
                    Ops.displayIncompleteUsbDebuggingMessage(requireActivity());
                case Ops.STATUS_SUCCESS:
                case Ops.STATUS_FAILURE:
                    mConnecting = false;
                    mModeOfOpsAlertDialog.dismiss();
                    mCurrentMode = Ops.getMode();
                    updateViews();
            }
        });
    }

    private void updateViews() {
        boolean serverActive = LocalServer.alive(requireContext());
        boolean serverRequired = requireRemoteServer(mCurrentMode);
        boolean servicesActive = LocalServices.alive();
        boolean servicesRequired = requireRemoteServices(mCurrentMode);
        // Mode
        if (mConnecting) {
            mInferredModeView.setText(R.string.status_connecting);
            mInferredModeView.setTextColor(mColorActive);
            TextViewCompat.setCompoundDrawableTintList(mModeOfOpsView, mColorActive);
            mModeOfOpsView.setTextColor(mColorActive);
            mModeOfOpsView.setCompoundDrawablesRelativeWithIntrinsicBounds(mIconProgress, 0, 0, 0);
            mModeOfOpsView.setText(getString(R.string.status_connecting_via_mode, mModes[MODE_NAMES.indexOf(mCurrentMode)]));
        } else {
            boolean goodMode = !badInferredMode(mCurrentMode);
            mInferredModeView.setText(Ops.getInferredMode(requireContext()));
            if (goodMode) {
                mInferredModeView.setTextColor(mColorActive);
                TextViewCompat.setCompoundDrawableTintList(mModeOfOpsView, mColorActive);
                mModeOfOpsView.setTextColor(mColorActive);
                mModeOfOpsView.setCompoundDrawablesRelativeWithIntrinsicBounds(mIconActive, 0, 0, 0);
                mModeOfOpsView.setText(getString(R.string.status_connected_via_mode, mModes[MODE_NAMES.indexOf(mCurrentMode)]));
            } else {
                mInferredModeView.setTextColor(mColorError);
                TextViewCompat.setCompoundDrawableTintList(mModeOfOpsView, mColorError);
                mModeOfOpsView.setTextColor(mColorError);
                mModeOfOpsView.setCompoundDrawablesRelativeWithIntrinsicBounds(mIconInactive, 0, 0, 0);
                mModeOfOpsView.setText(getString(R.string.status_not_connected_via_mode, mModes[MODE_NAMES.indexOf(mCurrentMode)]));
            }
        }
        // Server
        if (serverRequired) {
            mRemoteServerStatusView.setTextColor(serverActive ? mColorActive : mColorError);
            TextViewCompat.setCompoundDrawableTintList(mRemoteServerStatusView, serverActive ? mColorActive : mColorError);
        } else {
            mRemoteServerStatusView.setTextColor(mColorInactive);
            TextViewCompat.setCompoundDrawableTintList(mRemoteServerStatusView, mColorInactive);
        }
        mRemoteServerStatusView.setCompoundDrawablesRelativeWithIntrinsicBounds(serverActive ? mIconActive : mIconInactive, 0, 0, 0);
        mRemoteServerStatusView.setText(serverActive ? R.string.status_remote_server_active : R.string.status_remote_server_inactive);
        // Services
        if (servicesRequired) {
            mRemoteServicesStatusView.setTextColor(servicesActive? mColorActive : mColorError);
            TextViewCompat.setCompoundDrawableTintList(mRemoteServicesStatusView, servicesActive ? mColorActive : mColorError);
        } else {
            mRemoteServicesStatusView.setTextColor(mColorInactive);
            TextViewCompat.setCompoundDrawableTintList(mRemoteServicesStatusView, mColorInactive);
        }
        mRemoteServicesStatusView.setCompoundDrawablesRelativeWithIntrinsicBounds(servicesActive ? mIconActive : mIconInactive, 0, 0, 0);
        mRemoteServicesStatusView.setText(servicesActive ? R.string.status_remote_services_active : R.string.status_remote_services_inactive);
    }

    private static boolean requireRemoteServer(@NonNull String mode) {
        return Ops.MODE_ADB_OVER_TCP.equals(mode) || Ops.MODE_ADB_WIFI.equals(mode);
    }

    private static boolean requireRemoteServices(@NonNull String mode) {
        return !Ops.MODE_AUTO.equals(mode) && !Ops.MODE_NO_ROOT.equals(mode);
    }

    private static boolean badInferredMode(@NonNull String mode) {
        int uid = Users.getSelfOrRemoteUid();
        switch (mode) {
            case Ops.MODE_ROOT:
                return uid != Ops.ROOT_UID;
            case Ops.MODE_ADB_OVER_TCP:
            case Ops.MODE_ADB_WIFI:
                return uid > Ops.SHELL_UID;
            default:
                return false;
        }
    }
}
