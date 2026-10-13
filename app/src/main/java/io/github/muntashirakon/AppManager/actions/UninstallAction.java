// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.Manifest;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
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

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {
        List<UserPackagePair> targets = resolveTargets(context);
        if (mode == ActionMode.OFF) {
            return unsupportedResults(targets, "Uninstall has no reverse operation");
        }

        boolean keepData = requireBoolean(requireParameters(mode), PARAM_KEEP_DATA);
        AccessibilityMultiplexer accessibility = AccessibilityMultiplexer.getInstance();
        boolean accessibilityEnabled = false;
        if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DELETE_PACKAGES)) {
            accessibility.enableUninstall(true);
            accessibilityEnabled = true;
        }

        List<ActionResult> results = new ArrayList<>(targets.size());
        try {
            for (UserPackagePair target : targets) {
                try {
                    PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                    if (installer.uninstall(target.getPackageName(), target.getUserId(), keepData)) {
                        results.add(actionResult(target, ActionResultStatus.SUCCESS, null));
                    } else {
                        results.add(actionResult(target, ActionResultStatus.FAILED,
                                "Package uninstallation failed"));
                    }
                } catch (Throwable exception) {
                    results.add(actionResult(target, ActionResultStatus.FAILED,
                            exception.getMessage()));
                }
            }
        } finally {
            if (accessibilityEnabled) {
                accessibility.enableUninstall(false);
            }
        }
        return results;
    }
}
