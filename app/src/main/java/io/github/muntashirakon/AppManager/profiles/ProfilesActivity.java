/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.profiles;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.ProgressIndicator;

import org.json.JSONException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.types.TextInputDialogBuilder;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ProfilesActivity extends BaseActivity {
    private static final String TAG = "ProfilesActivity";

    private ProfilesAdapter adapter;
    private ProfilesViewModel model;
    private ProgressIndicator progressIndicator;
    @Nullable
    private String profileName;

    private ActivityResultLauncher<String> exportProfile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
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
    private ActivityResultLauncher<String> importProfile = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                try {
                    // Verify
                    String fileName = IOUtils.getFileName(getContentResolver(), uri);
                    if (fileName == null) throw new IOException("File name cannot be empty.");
                    fileName = IOUtils.trimExtension(fileName);
                    String fileContent = IOUtils.getFileContent(getContentResolver(), uri);
                    ProfileMetaManager manager = ProfileMetaManager.readProfile(fileName, fileContent);
                    // Save
                    manager.writeProfile();
                    Toast.makeText(this, R.string.the_import_was_successful, Toast.LENGTH_SHORT).show();
                    // Reload page
                    new Thread(() -> model.loadProfiles()).start();
                    // Load imported profile
                    Intent intent = new Intent(this, AppsProfileActivity.class);
                    intent.putExtra(AppsProfileActivity.EXTRA_PROFILE_NAME, manager.getProfileName());
                    startActivity(intent);
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Error: ", e);
                    Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profiles);
        setSupportActionBar(findViewById(R.id.toolbar));
        model = new ViewModelProvider(this).get(ProfilesViewModel.class);
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        ListView listView = findViewById(android.R.id.list);
        listView.setEmptyView(findViewById(android.R.id.empty));
        adapter = new ProfilesAdapter(this);
        listView.setAdapter(adapter);
        FloatingActionButton fab = findViewById(R.id.floatingActionButton);
        fab.setOnClickListener(v -> new TextInputDialogBuilder(this, R.string.input_profile_name)
                .setTitle(R.string.new_profile)
                .setHelperText(R.string.input_profile_name_description)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.go, (dialog, which, profName, isChecked) -> {
                    if (!TextUtils.isEmpty(profName)) {
                        Intent intent = new Intent(this, AppsProfileActivity.class);
                        //noinspection ConstantConditions
                        intent.putExtra(AppsProfileActivity.EXTRA_NEW_PROFILE_NAME, profName.toString());
                        intent.putExtra(AppsProfileActivity.EXTRA_NEW_PROFILE, true);
                        startActivity(intent);
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
        progressIndicator.show();
        new Thread(() -> model.loadProfiles()).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_profiles_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_import:
                importProfile.launch("application/json");
                return true;
            case R.id.action_refresh:
                progressIndicator.show();
                new Thread(() -> model.loadProfiles()).start();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    static class ProfilesAdapter extends BaseAdapter implements Filterable {
        private LayoutInflater mLayoutInflater;
        private Filter mFilter;
        private String mConstraint;
        private String[] mDefaultList;
        private String[] mAdapterList;
        private HashMap<String, String> mAdapterMap;
        private ProfilesActivity activity;

        private int mColorTransparent;
        private int mColorSemiTransparent;
        private int mColorRed;

        static class ViewHolder {
            TextView item_name;
            TextView item_value;
        }

        ProfilesAdapter(@NonNull ProfilesActivity activity) {
            this.activity = activity;
            mLayoutInflater = activity.getLayoutInflater();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.semi_transparent);
            mColorRed = ContextCompat.getColor(activity, R.color.red);
        }

        void setDefaultList(@NonNull HashMap<String, String> list) {
            mDefaultList = list.keySet().toArray(new String[0]);
            mAdapterList = mDefaultList;
            mAdapterMap = list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.length;
        }

        @Override
        public String getItem(int position) {
            return mAdapterList[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.item_shared_pref, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.item_name = convertView.findViewById(R.id.item_title);
                viewHolder.item_value = convertView.findViewById(R.id.item_subtitle);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            String profName = mAdapterList[position];
            if (mConstraint != null && profName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                viewHolder.item_name.setText(UIUtils.getHighlightedText(profName, mConstraint, mColorRed));
            } else {
                viewHolder.item_name.setText(profName);
            }
            Object value = mAdapterMap.get(profName);
            String strValue = (value != null) ? value.toString() : "";
            viewHolder.item_value.setText(strValue);
            convertView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            convertView.setOnClickListener(v -> {
                Intent intent = new Intent(activity, AppsProfileActivity.class);
                intent.putExtra(AppsProfileActivity.EXTRA_PROFILE_NAME, profName);
                activity.startActivity(intent);
            });
            View finalConvertView = convertView;
            convertView.setOnLongClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(activity, finalConvertView);
                popupMenu.inflate(R.menu.activity_profiles_popup_actions);
                popupMenu.setOnMenuItemClickListener(item -> {
                    switch (item.getItemId()) {
                        case R.id.action_apply:
                            // TODO(7/11/20): Apply the profile: display enable/disable first
                            Toast.makeText(activity, "Not yet implemented", Toast.LENGTH_SHORT).show();
                            return true;
                        case R.id.action_delete:
                            ProfileMetaManager manager = new ProfileMetaManager(profName);
                            if (manager.deleteProfile()) {
                                Toast.makeText(activity, R.string.deleted_successfully, Toast.LENGTH_SHORT).show();
                                new Thread(() -> activity.model.loadProfiles()).start();
                            } else {
                                Toast.makeText(activity, R.string.deletion_failed, Toast.LENGTH_SHORT).show();
                            }
                            return true;
                        case R.id.action_routine_ops:
                            // TODO(7/11/20): Setup routine operations for this profile
                            Toast.makeText(activity, "Not yet implemented", Toast.LENGTH_SHORT).show();
                            return true;
                        case R.id.action_duplicate: {
                            new TextInputDialogBuilder(activity, R.string.input_profile_name)
                                    .setTitle(R.string.new_profile)
                                    .setHelperText(R.string.input_profile_name_description)
                                    .setNegativeButton(R.string.cancel, null)
                                    .setPositiveButton(R.string.go, (dialog, which, newProfName, isChecked) -> {
                                        if (!TextUtils.isEmpty(newProfName)) {
                                            Intent intent = new Intent(activity, AppsProfileActivity.class);
                                            intent.putExtra(AppsProfileActivity.EXTRA_PROFILE_NAME, profName);
                                            //noinspection ConstantConditions
                                            intent.putExtra(AppsProfileActivity.EXTRA_NEW_PROFILE_NAME, newProfName.toString());
                                            intent.putExtra(AppsProfileActivity.EXTRA_NEW_PROFILE, true);
                                            activity.startActivity(intent);
                                        }
                                    })
                                    .show();
                            return true;
                        }
                        case R.id.action_export:
                            activity.profileName = profName;
                            activity.exportProfile.launch(profName + ".am.json");
                            return true;
                        default:
                            return false;
                    }
                });
                popupMenu.show();
                return true;
            });
            return convertView;
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
