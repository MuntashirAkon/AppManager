// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.LauncherIconCreator;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

public class ProfilesActivity extends BaseActivity {
    private static final String TAG = "ProfilesActivity";

    private ProfilesAdapter adapter;
    private ProfilesViewModel model;
    private LinearProgressIndicator progressIndicator;
    @Nullable
    private String profileName;

    private final ActivityResultLauncher<String> exportProfile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                if (profileName != null) {
                    // Export profile
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        ProfileMetaManager manager = new ProfileMetaManager(profileName);
                        manager.writeProfile(os);
                        Toast.makeText(this, R.string.the_export_was_successful, Toast.LENGTH_SHORT).show();
                    } catch (IOException | JSONException e) {
                        Log.e(TAG, "Error: ", e);
                        Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            });
    private final ActivityResultLauncher<String> importProfile = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                try {
                    // Verify
                    Path profilePath = Paths.get(uri);
                    String fileName = profilePath.getName();
                    fileName = Paths.trimPathExtension(fileName);
                    String fileContent = profilePath.getContentAsString();
                    ProfileMetaManager manager = ProfileMetaManager.fromJSONString(fileName, fileContent);
                    // Save
                    manager.writeProfile();
                    Toast.makeText(this, R.string.the_import_was_successful, Toast.LENGTH_SHORT).show();
                    // Reload page
                    new Thread(() -> model.loadProfiles()).start();
                    // Load imported profile
                    startActivity(AppsProfileActivity.getProfileIntent(this, manager.getProfileName()));
                } catch (IOException | JSONException | RemoteException e) {
                    Log.e(TAG, "Error: ", e);
                    Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_profiles);
        setSupportActionBar(findViewById(R.id.toolbar));
        model = new ViewModelProvider(this).get(ProfilesViewModel.class);
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        RecyclerView listView = findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setEmptyView(findViewById(android.R.id.empty));
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        adapter = new ProfilesAdapter(this);
        listView.setAdapter(adapter);
        FloatingActionButton fab = findViewById(R.id.floatingActionButton);
        UiUtils.applyWindowInsetsAsMargin(fab);
        fab.setOnClickListener(v -> new TextInputDialogBuilder(this, R.string.input_profile_name)
                .setTitle(R.string.new_profile)
                .setHelperText(R.string.input_profile_name_description)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.go, (dialog, which, profName, isChecked) -> {
                    if (!TextUtils.isEmpty(profName)) {
                        //noinspection ConstantConditions
                        startActivity(AppsProfileActivity.getNewProfileIntent(this, profName.toString()));
                    }
                })
                .show());
        model.getProfiles().observe(this, profiles -> {
            progressIndicator.hide();
            adapter.setDefaultList(profiles);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (progressIndicator != null) {
            progressIndicator.show();
        }
        new Thread(() -> {
            if (model != null) {
                model.loadProfiles();
            }
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_profiles_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_import) {
            importProfile.launch("application/json");
        } else if (id == R.id.action_refresh) {
            progressIndicator.show();
            new Thread(() -> model.loadProfiles()).start();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    static class ProfilesAdapter extends RecyclerView.Adapter<ProfilesAdapter.ViewHolder> implements Filterable {
        private Filter mFilter;
        private String mConstraint;
        private String[] mDefaultList;
        private String[] mAdapterList;
        private HashMap<String, CharSequence> mAdapterMap;
        private final ProfilesActivity activity;
        private final int mQueryStringHighlightColor;

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView summary;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.title);
                summary = itemView.findViewById(android.R.id.summary);
                itemView.findViewById(R.id.icon_frame).setVisibility(View.GONE);
            }
        }

        ProfilesAdapter(@NonNull ProfilesActivity activity) {
            this.activity = activity;
            mQueryStringHighlightColor = ColorCodes.getQueryStringHighlightColor(activity);
        }

        void setDefaultList(@NonNull HashMap<String, CharSequence> list) {
            mDefaultList = list.keySet().toArray(new String[0]);
            mAdapterList = mDefaultList;
            mAdapterMap = list;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mAdapterList == null ? 0 : mAdapterList.length;
        }

        @Override
        public long getItemId(int position) {
            return mAdapterList[position].hashCode();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String profName = mAdapterList[position];
            if (mConstraint != null && profName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.title.setText(UIUtils.getHighlightedText(profName, mConstraint, mQueryStringHighlightColor));
            } else {
                holder.title.setText(profName);
            }
            CharSequence value = mAdapterMap.get(profName);
            holder.summary.setText(value != null ? value : "");
            holder.itemView.setOnClickListener(v ->
                    activity.startActivity(AppsProfileActivity.getProfileIntent(activity, profName)));
            holder.itemView.setOnLongClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(activity, v);
                popupMenu.inflate(R.menu.activity_profiles_popup_actions);
                popupMenu.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.action_apply) {
                        final String[] statesL = new String[]{
                                activity.getString(R.string.on),
                                activity.getString(R.string.off)
                        };
                        @ProfileMetaManager.ProfileState
                        final List<String> states = Arrays.asList(ProfileMetaManager.STATE_ON, ProfileMetaManager.STATE_OFF);
                        new SearchableSingleChoiceDialogBuilder<>(activity, states, statesL)
                                .setTitle(R.string.profile_state)
                                .setOnSingleChoiceClickListener((dialog, which, selectedState, isChecked) -> {
                                    if (!isChecked) {
                                        return;
                                    }
                                    Intent aIntent = new Intent(activity, ProfileApplierService.class);
                                    aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_NAME, profName);
                                    aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_STATE, selectedState);
                                    ContextCompat.startForegroundService(activity, aIntent);
                                    dialog.dismiss();
                                })
                                .show();
                    } else if (id == R.id.action_delete) {
                        ProfileMetaManager manager = new ProfileMetaManager(profName);
                        if (manager.deleteProfile()) {
                            Toast.makeText(activity, R.string.deleted_successfully, Toast.LENGTH_SHORT).show();
                            new Thread(() -> activity.model.loadProfiles()).start();
                        } else {
                            Toast.makeText(activity, R.string.deletion_failed, Toast.LENGTH_SHORT).show();
                        }
                    } else if (id == R.id.action_routine_ops) {
                        // TODO(7/11/20): Setup routine operations for this profile
                        Toast.makeText(activity, "Not yet implemented", Toast.LENGTH_SHORT).show();
                    } else if (id == R.id.action_duplicate) {
                        new TextInputDialogBuilder(activity, R.string.input_profile_name)
                                .setTitle(R.string.new_profile)
                                .setHelperText(R.string.input_profile_name_description)
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.go, (dialog, which, newProfName, isChecked) -> {
                                    if (!TextUtils.isEmpty(newProfName)) {
                                        //noinspection ConstantConditions
                                        activity.startActivity(AppsProfileActivity.getCloneProfileIntent(activity,
                                                profName, newProfName.toString()));
                                    }
                                })
                                .show();
                    } else if (id == R.id.action_export) {
                        activity.profileName = profName;
                        activity.exportProfile.launch(profName + ".am.json");
                    } else if (id == R.id.action_shortcut) {
                        final String[] shortcutTypesL = new String[]{
                                activity.getString(R.string.simple),
                                activity.getString(R.string.advanced)
                        };
                        final String[] shortcutTypes = new String[]{AppsProfileActivity.ST_SIMPLE, AppsProfileActivity.ST_ADVANCED};
                        new SearchableSingleChoiceDialogBuilder<>(activity, shortcutTypes, shortcutTypesL)
                                .setTitle(R.string.profile_state)
                                .setOnSingleChoiceClickListener((dialog, which, item1, isChecked) -> {
                                    if (!isChecked) {
                                        return;
                                    }
                                    Intent intent = AppsProfileActivity.getShortcutIntent(activity, profName, shortcutTypes[which], null);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    LauncherIconCreator.createLauncherIcon(activity,
                                            profName + " - " + shortcutTypesL[which], ContextCompat
                                                    .getDrawable(activity, R.drawable.ic_launcher_foreground), intent);
                                    dialog.dismiss();
                                })
                                .show();
                    } else return false;
                    return true;
                });
                popupMenu.show();
                return true;
            });
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = charSequence.toString().toLowerCase(Locale.ROOT);
                        mConstraint = constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<String> list = new ArrayList<>(mDefaultList.length);
                        for (String item : mDefaultList) {
                            if (item.toLowerCase(Locale.ROOT).contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list.toArray(new String[0]);
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            mAdapterList = (String[]) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }
    }
}
