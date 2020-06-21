// This is a modified version of IconPickerDialogFragment.java taken
// from https://github.com/butzist/ActivityLauncher/commit/dfb7fe271dae9379b5453bbb6e88f30a1adc94a9
// and was authored by Adam M. Szalkowski with ISC License.
// All derivative works are licensed under GPLv3.0.

package io.github.muntashirakon.AppManager.fragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.GridView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import io.github.muntashirakon.AppManager.AsyncProvider;
import io.github.muntashirakon.AppManager.IconListAdapter;
import io.github.muntashirakon.AppManager.IconListAsyncProvider;
import io.github.muntashirakon.AppManager.R;

public class IconPickerDialogFragment extends DialogFragment implements AsyncProvider.Listener<IconListAdapter> {
    static final String TAG = "IconPickerDialogFragment";

    private GridView grid;
    private IconPickerListener listener = null;

    @Override
    public void onAttach(@NonNull Context activity) {
        super.onAttach(activity);
        IconListAsyncProvider provider = new IconListAsyncProvider(this.getActivity(), this);
        provider.execute();
    }

    void attachIconPickerListener(IconPickerListener listener) {
        this.listener = listener;
    }

    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        assert getActivity() != null;
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomDialog);

        if (getActivity() == null) return builder.create();
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (inflater == null) return builder.create();
        grid = (GridView) inflater.inflate(R.layout.dialog_icon_picker, null);

        grid.setOnItemClickListener((view, item, index, id) -> {
            if (listener != null) {
                listener.iconPicked(view.getAdapter().getItem(index).toString());
                if (getDialog() != null) getDialog().dismiss();
            }
        });

        builder.setTitle(R.string.icon_picker)
                .setView(grid)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    if (getDialog() != null) getDialog().cancel();
                });

        return builder.create();
    }

    @Override
    public void onProviderFinished(AsyncProvider<IconListAdapter> task, IconListAdapter value) {
        try {
            this.grid.setAdapter(value);
        } catch (Exception e) {
            Toast.makeText(this.getActivity(), R.string.error_loading_icons, Toast.LENGTH_SHORT).show();
        }
    }

    public interface IconPickerListener {
        void iconPicked(String icon);
    }
}
