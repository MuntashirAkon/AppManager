// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.github.muntashirakon.AppManager.utils.RestartUtils;

public final class RebootAction extends GlobalScopedAction {
    public static final String PARAM_MODE = "mode";

    public static final String MODE_NORMAL = "normal";
    public static final String MODE_RECOVERY = "recovery";
    public static final String MODE_BOOTLOADER = "bootloader";
    public static final String MODE_USERSPACE = "userspace";
    public static final String MODE_DOWNLOAD = "download";
    public static final String MODE_EDL = "edl";

    public RebootAction(@NonNull String mode) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, emptyGlobalTarget(),
                new ActionParameters().put(PARAM_MODE, normalizeMode(mode)), Strictness.STRICT,
                RevertMode.NONE);
    }

    public RebootAction(@NonNull String id, @Nullable String name, int schemaVersion,
                        @NonNull ActionTargetSpec target,
                        @NonNull ActionParameters onParameters,
                        @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.REBOOT, target, onParameters, null,
                strictness, revertMode);
    }

    private RebootAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static RebootAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new RebootAction(parcel, kind);
    }

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {
        if (mode == ActionMode.OFF) {
            return unsupportedResults("Reboot has no reverse operation");
        }

        String modeName = normalizeMode(requireString(getOnParameters(), PARAM_MODE));
        try {
            switch (modeName) {
                case MODE_NORMAL:
                    RestartUtils.restart(RestartUtils.RESTART_NORMAL);
                    break;
                case MODE_RECOVERY:
                    RestartUtils.restart(RestartUtils.RESTART_RECOVERY);
                    break;
                case MODE_BOOTLOADER:
                    RestartUtils.restart(RestartUtils.RESTART_BOOTLOADER);
                    break;
                case MODE_USERSPACE:
                    RestartUtils.restart(RestartUtils.RESTART_USERSPACE);
                    break;
                case MODE_DOWNLOAD:
                    RestartUtils.restart(RestartUtils.RESTART_DOWNLOAD);
                    break;
                case MODE_EDL:
                    RestartUtils.restart(RestartUtils.RESTART_EDL);
                    break;
                default:
                    return Collections.singletonList(globalResult(ActionResultStatus.FAILED,
                            "Unsupported reboot mode: " + modeName));
            }
            return Collections.singletonList(globalResult(ActionResultStatus.SUCCESS, null));
        } catch (Throwable exception) {
            return Collections.singletonList(globalResult(ActionResultStatus.FAILED,
                    exception.getMessage()));
        }
    }

    @NonNull
    private static String normalizeMode(@NonNull String mode) {
        String normalizedMode = mode.toLowerCase(Locale.ROOT);
        switch (normalizedMode) {
            case MODE_NORMAL:
            case MODE_RECOVERY:
            case MODE_BOOTLOADER:
            case MODE_USERSPACE:
            case MODE_DOWNLOAD:
            case MODE_EDL:
                return normalizedMode;
            default:
                throw new IllegalArgumentException("Unsupported reboot mode: " + mode);
        }
    }
}
