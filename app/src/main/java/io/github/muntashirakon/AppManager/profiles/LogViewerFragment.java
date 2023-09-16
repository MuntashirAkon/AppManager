// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.UiUtils;

public class LogViewerFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ProfileViewModel model = new ViewModelProvider(requireActivity()).get(ProfileViewModel.class);
        AppCompatEditText tv = view.findViewById(R.id.log_content);
        tv.setKeyListener(null);
        ExtendedFloatingActionButton efab = view.findViewById(R.id.floatingActionButton);
        UiUtils.applyWindowInsetsAsMargin(efab, false, true);
        efab.setOnClickListener(v -> {
            ProfileLogger.clearLogs(model.getProfileId());
            tv.setText("");
        });
        model.getLogs().observe(getViewLifecycleOwner(), logs -> tv.setText(getFormattedLogs(logs)));
        model.observeProfileLoaded().observe(getViewLifecycleOwner(), profileName -> model.loadLogs());
    }

    @Override
    public void onResume() {
        AppsProfileActivity activity = (AppsProfileActivity) requireActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setSubtitle(R.string.log_viewer);
        }
        activity.fab.hide();
        super.onResume();
    }


    public CharSequence getFormattedLogs(String logs) {
        SpannableString str = new SpannableString(logs);
        int fIndex = 0;
        while(true) {
            fIndex = logs.indexOf("====> ", fIndex);
            if (fIndex == -1) {
                return str;
            }
            int lIndex = logs.indexOf('\n', fIndex);
            str.setSpan(new StyleSpan(Typeface.BOLD), fIndex, lIndex, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            fIndex = lIndex;
        }
    }
}
