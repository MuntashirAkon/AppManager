// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.Manifest;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class UninstallAction extends PackageScopedAction {
    public static final String PARAM_KEEP_DATA = "keep_data";

    public UninstallAction(@NonNull ActionTargetSpec target, boolean keepData) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target,
                new ActionParameters().put(PARAM_KEEP_DATA, keepData), Strictness.STRICT,
                RevertMode.NONE);
    }

    public UninstallAction(@NonNull String id, @Nullable String name, int schemaVersion,
                           @NonNull ActionTargetSpec target,
                           @NonNull ActionParameters onParameters,
                           @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.UNINSTALL, target, onParameters, null,
                strictness, revertMode);
    }

    private UninstallAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static UninstallAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new UninstallAction(parcel, kind);
    }

    private boolean mKeepData;
    private boolean mAccessibilityEnabled;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) {
        if (mode == ActionMode.OFF) {
            return;
        }
        mKeepData = requireBoolean(requireParameters(mode), PARAM_KEEP_DATA);
        if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DELETE_PACKAGES)) {
            AccessibilityMultiplexer.getInstance().enableUninstall(true);
            mAccessibilityEnabled = true;
        }
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords) {
        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
        if (installer.uninstall(target.getPackageName(), target.getUserId(), mKeepData)) {
            return actionResult(target, ActionResultStatus.SUCCESS, null);
        }
        return actionResult(target, ActionResultStatus.FAILED, "Package uninstallation failed");
    }

    @Override
    protected void onPostExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                 @NonNull List<UserPackagePair> targets,
                                 @NonNull List<ActionResult> results) {
        if (mAccessibilityEnabled) {
            AccessibilityMultiplexer.getInstance().enableUninstall(false);
            mAccessibilityEnabled = false;
        }
    }
}
