// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity.ST_ADVANCED;
import static io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity.ST_SIMPLE;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

public class ProfilesActivity extends BaseActivity {
    private static final String TAG = "ProfilesActivity";

    private ProfilesAdapter mAdapter;
    private ProfilesViewModel mModel;
    private LinearProgressIndicator mProgressIndicator;
    @Nullable
    private String mProfileId;

    private final ActivityResultLauncher<String> mExportProfile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                if (mProfileId != null) {
                    // Export profile
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        Path profilePath = ProfileManager.findProfilePathById(mProfileId);
                        AppsProfile profile = AppsProfile.fromPath(profilePath);
                        profile.write(os);
                        UIUtils.displayShortToast(R.string.the_export_was_successful);
                    } catch (IOException | JSONException e) {
                        Log.e(TAG, "Error: ", e);
                        UIUtils.displayShortToast(R.string.export_failed);
                    }
                }
            });
    private final ActivityResultLauncher<String> mImportProfile = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                try {
                    // Verify
                    Path profilePath = Paths.get(uri);
                    AppsProfile profile = AppsProfile.fromPath(profilePath);
                    AppsProfile newProfile = AppsProfile.newProfile(profile.name, profile);
                    Path innerProfilePath = ProfileManager.requireProfilePathById(newProfile.profileId);
                    // Save
                    try (OutputStream os = innerProfilePath.openOutputStream()) {
                        newProfile.write(os);
                    }
                    UIUtils.displayShortToast(R.string.the_import_was_successful);
                    // Load imported profile
                    startActivity(AppsProfileActivity.getProfileIntent(this, newProfile.profileId));
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Error: ", e);
                    UIUtils.displayShortToast(R.string.import_failed);
                }
            });

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_profiles);
        setSupportActionBar(findViewById(R.id.toolbar));
        mModel = new ViewModelProvider(this).get(ProfilesViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        RecyclerView listView = findViewById(android.R.id.list);
        listView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        listView.setEmptyView(findViewById(android.R.id.empty));
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mAdapter = new ProfilesAdapter(this);
        listView.setAdapter(mAdapter);
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
        mModel.getProfilesLiveData().observe(this, profiles -> {
            mProgressIndicator.hide();
            mAdapter.setDefaultList(profiles);
        });
        mProgressIndicator.show();
        mModel.loadProfiles();
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
            mImportProfile.launch("application/json");
        } else if (id == R.id.action_refresh) {
            mProgressIndicator.show();
            mModel.loadProfiles();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    static class ProfilesAdapter extends RecyclerView.Adapter<ProfilesAdapter.ViewHolder> implements Filterable {
        private Filter mFilter;
        private String mConstraint;
        private AppsProfile[] mDefaultList;
        private AppsProfile[] mAdapterList;
        private HashMap<AppsProfile, CharSequence> mAdapterMap;
        private final ProfilesActivity mActivity;
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
            mActivity = activity;
            mQueryStringHighlightColor = ColorCodes.getQueryStringHighlightColor(activity);
        }

        void setDefaultList(@NonNull HashMap<AppsProfile, CharSequence> list) {
            mDefaultList = list.keySet().toArray(new AppsProfile[0]);
            int previousCount = getItemCount();
            mAdapterList = mDefaultList;
            mAdapterMap = list;
            AdapterUtils.notifyDataSetChanged(this, previousCount, mAdapterList.length);
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
            AppsProfile profile = mAdapterList[position];
            if (mConstraint != null && profile.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.title.setText(UIUtils.getHighlightedText(profile.name, mConstraint, mQueryStringHighlightColor));
            } else {
                holder.title.setText(profile.name);
            }
            CharSequence value = mAdapterMap.get(profile);
            holder.summary.setText(value != null ? value : "");
            holder.itemView.setOnClickListener(v ->
                    mActivity.startActivity(AppsProfileActivity.getProfileIntent(mActivity, profile.profileId)));
            holder.itemView.setOnLongClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(mActivity, v);
                popupMenu.setForceShowIcon(true);
                popupMenu.inflate(R.menu.activity_profiles_popup_actions);
                popupMenu.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.action_apply) {
                        Intent intent = ProfileApplierActivity.getApplierIntent(mActivity, profile.profileId);
                        mActivity.startActivity(intent);
                    } else if (id == R.id.action_delete) {
                        new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(mActivity.getString(R.string.delete_filename, profile.name))
                                .setMessage(R.string.are_you_sure)
                                .setPositiveButton(R.string.cancel, null)
                                .setNegativeButton(R.string.ok, (dialog, which) -> {
                                    if (ProfileManager.deleteProfile(profile.profileId)) {
                                        UIUtils.displayShortToast(R.string.deleted_successfully);
                                    } else {
                                        UIUtils.displayShortToast(R.string.deletion_failed);
                                    }
                                })
                                .show();
                    } else if (id == R.id.action_routine_ops) {
                        // TODO(7/11/20): Setup routine operations for this profile
                        UIUtils.displayShortToast("Not yet implemented");
                    } else if (id == R.id.action_duplicate) {
                        new TextInputDialogBuilder(mActivity, R.string.input_profile_name)
                                .setTitle(R.string.new_profile)
                                .setHelperText(R.string.input_profile_name_description)
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.go, (dialog, which, newProfName, isChecked) -> {
                                    if (!TextUtils.isEmpty(newProfName)) {
                                        //noinspection ConstantConditions
                                        mActivity.startActivity(AppsProfileActivity.getCloneProfileIntent(mActivity,
                                                profile.profileId, newProfName.toString()));
                                    }
                                })
                                .show();
                    } else if (id == R.id.action_export) {
                        mActivity.mProfileId = profile.profileId;
                        mActivity.mExportProfile.launch(profile.name + ".am.json");
                    }  else if (id == R.id.action_copy) {
                        Utils.copyToClipboard(mActivity, profile.name, profile.profileId);
                    } else if (id == R.id.action_shortcut) {
                        final String[] shortcutTypesL = new String[]{
                                mActivity.getString(R.string.simple),
                                mActivity.getString(R.string.advanced)
                        };
                        final String[] shortcutTypes = new String[]{ST_SIMPLE, ST_ADVANCED};
                        new SearchableSingleChoiceDialogBuilder<>(mActivity, shortcutTypes, shortcutTypesL)
                                .setTitle(R.string.create_shortcut)
                                .setOnSingleChoiceClickListener((dialog, which, item1, isChecked) -> {
                                    if (!isChecked) {
                                        return;
                                    }
                                    Drawable icon = Objects.requireNonNull(ContextCompat.getDrawable(mActivity, R.drawable.ic_launcher_foreground));
                                    ProfileShortcutInfo shortcutInfo = new ProfileShortcutInfo(profile.profileId,
                                            profile.name, shortcutTypes[which], shortcutTypesL[which]);
                                    shortcutInfo.setIcon(UIUtils.getBitmapFromDrawable(icon));
                                    CreateShortcutDialogFragment dialog1 = CreateShortcutDialogFragment.getInstance(shortcutInfo);
                                    dialog1.show(mActivity.getSupportFragmentManager(), CreateShortcutDialogFragment.TAG);
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
                        if (constraint.isEmpty()) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<AppsProfile> list = new ArrayList<>(mDefaultList.length);
                        for (AppsProfile item : mDefaultList) {
                            if (item.name.toLowerCase(Locale.ROOT).contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list.toArray(new AppsProfile[0]);
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        int previousCount = mAdapterList != null ? mAdapterList.length : 0;
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            mAdapterList = (AppsProfile[]) filterResults.values;
                        }
                        AdapterUtils.notifyDataSetChanged(ProfilesAdapter.this, previousCount, mAdapterList.length);
                    }
                };
            return mFilter;
        }
    }
}
