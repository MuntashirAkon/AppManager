package io.github.muntashirakon.AppManager.fragments;

import android.app.Dialog;
import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.storage.backup.BackupStorageManager;
import io.github.muntashirakon.AppManager.usage.Utils;

public class BackupDialogFragment extends DialogFragment {
    public static final String ARG_PACKAGES = "ARG_PACKAGES";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        Bundle args = requireArguments();
        List<String> packageNames = args.getStringArrayList(ARG_PACKAGES);
        if (packageNames == null) return super.onCreateDialog(savedInstanceState);
        boolean[] checkedItems = new boolean[5];
        Arrays.fill(checkedItems, true);
        // Set external data to false
        checkedItems[2] = false;
        AtomicInteger flags = new AtomicInteger(BackupStorageManager.BACKUP_NOTHING);
        return new MaterialAlertDialogBuilder(activity, R.style.AppTheme_AlertDialog)
                .setTitle(packageNames.size() == 1 ? Utils.getPackageLabel(activity
                        .getPackageManager(), packageNames.get(0)) : getString(R.string.backup_options))
                .setMultiChoiceItems(R.array.backup_flags, checkedItems, (dialog, which, isChecked) -> {
                    if (isChecked) flags.set(flags.get() | (1 << which));
                    else flags.set(flags.get() & ~(1 << which));
                })
                .setPositiveButton(R.string.backup, (dialog, which) -> {
                    // TODO backup data
                })
                .setNegativeButton(R.string.restore, (dialog, which) -> {
                    // TODO restore data
                })
                .setNeutralButton(R.string.delete_backup, (dialog, which) -> {
                    // TODO delete backup
                })
                .create();
    }
}
