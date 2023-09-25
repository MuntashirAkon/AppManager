// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.util.Optional;

public class AndroidFragment extends Fragment {
    @NonNull
    protected Optional<Context> getFragmentContext() {
        return Optional.ofNullable(getContext());
    }

    @NonNull
    protected Optional<FragmentActivity> getFragmentActivity() {
        return Optional.ofNullable(getActivity());
    }

    @NonNull
    protected Optional<ActionBar> getActionBar() {
        FragmentActivity activity = getActivity();
        if (activity instanceof AppCompatActivity) {
            return Optional.ofNullable(((AppCompatActivity) activity).getSupportActionBar());
        }
        return Optional.empty();
    }
}
