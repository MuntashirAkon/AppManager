// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.transition.MaterialSharedAxis;

import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.changelog.ChangelogRecyclerAdapter;
import io.github.muntashirakon.AppManager.misc.HelpActivity;
import io.github.muntashirakon.dialog.AlertDialogBuilder;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;

public class AboutPreferences extends PreferenceFragment {
    private MainPreferencesViewModel mModel;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_about, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        mModel = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        Preference versionPref = Objects.requireNonNull(findPreference("version"));
        versionPref.setSummary(String.format(Locale.getDefault(), "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        versionPref.setOnPreferenceClickListener(preference -> {
            mModel.loadChangeLog();
            return true;
        });
        // User manual
        ((Preference) Objects.requireNonNull(findPreference("user_manual")))
                .setOnPreferenceClickListener(preference -> {
                    Intent helpIntent = new Intent(requireContext(), HelpActivity.class);
                    startActivity(helpIntent);
                    return true;
                });
        // Website
        ((Preference) Objects.requireNonNull(findPreference("website")))
                .setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.website_message)));
                    startActivity(intent);
                    return true;
                });
        // Get help
        ((Preference) Objects.requireNonNull(findPreference("get_help")))
                .setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.discussions_site)));
                    startActivity(intent);
                    return true;
                });
        // Third-party libraries
        ((Preference) Objects.requireNonNull(findPreference("third_party_libraries")))
                .setOnPreferenceClickListener(preference -> {
                    new ScrollableDialogBuilder(requireActivity())
                            .setTitle(R.string.third_party)
                            .setMessage(R.string.third_party_message)
                            .enableAnchors()
                            .setNegativeButton(R.string.close, null)
                            .show();
                    return true;
                });
        // Credits
        ((Preference) Objects.requireNonNull(findPreference("credits")))
                .setOnPreferenceClickListener(preference -> {
                    new ScrollableDialogBuilder(requireActivity())
                            .setTitle(R.string.credits)
                            .setMessage(R.string.credits_message)
                            .enableAnchors()
                            .setNegativeButton(R.string.close, null)
                            .show();
                    return true;
                });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Observe Changelog
        mModel.getChangeLog().observe(getViewLifecycleOwner(), changelog -> {
            View v = View.inflate(requireContext(), R.layout.dialog_whats_new, null);
            RecyclerView recyclerView = v.findViewById(android.R.id.list);
            recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
            ChangelogRecyclerAdapter adapter = new ChangelogRecyclerAdapter();
            recyclerView.setAdapter(adapter);
            adapter.setAdapterList(changelog.getChangelogItems());
            new AlertDialogBuilder(requireActivity(), true)
                    .setTitle(R.string.changelog)
                    .setView(recyclerView)
                    .show();
        });
    }

    @Override
    public int getTitle() {
        return R.string.about;
    }
}
