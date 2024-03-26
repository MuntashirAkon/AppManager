// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.app.Application;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.FlowLayout;
import io.github.muntashirakon.widget.MaterialAlertView;
import io.github.muntashirakon.widget.RecyclerView;


public class BloatwareDetailsDialog extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = BloatwareDetailsDialog.class.getSimpleName();

    public static final String ARG_PACKAGE_NAME = "pkg";

    @NonNull
    public static BloatwareDetailsDialog getInstance(@NonNull String packageName) {
        BloatwareDetailsDialog fragment = new BloatwareDetailsDialog();
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE_NAME, packageName);
        fragment.setArguments(args);
        return fragment;
    }

    private ImageView mAppIconView;
    private MaterialButton mOpenAppInfoButton;
    private TextView mAppLabelView;
    private TextView mPackageNameView;
    private FlowLayout mFlowLayout;
    private MaterialAlertView mWarningView;
    private MaterialTextView mDescriptionView;
    private LinearLayoutCompat mSuggestionContainer;
    private RecyclerView mSuggestionView;
    private SuggestionsAdapter mAdapter;

    @Override
    public boolean displayLoaderByDefault() {
        return true;
    }

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_bloatware_details, container, false);
        mAppIconView = view.findViewById(R.id.icon);
        mOpenAppInfoButton = view.findViewById(R.id.info);
        mAppLabelView = view.findViewById(R.id.name);
        mPackageNameView = view.findViewById(R.id.package_name);
        mFlowLayout = view.findViewById(R.id.tag_cloud);
        mWarningView = view.findViewById(R.id.alert_text);
        mDescriptionView = view.findViewById(R.id.apk_description);
        mSuggestionContainer = view.findViewById(R.id.container);
        mSuggestionView = view.findViewById(R.id.recycler_view);
        mSuggestionView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new SuggestionsAdapter();
        mSuggestionView.setAdapter(mAdapter);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String packageName = requireArguments().getString(ARG_PACKAGE_NAME);
        if (packageName == null) {
            dismiss();
            return;
        }
        BloatwareDetailsViewModel viewModel = new ViewModelProvider(requireActivity()).get(BloatwareDetailsViewModel.class);
        viewModel.debloatObjectLiveData.observe(getViewLifecycleOwner(), debloatObject -> {
            if (debloatObject == null) {
                dismiss();
                return;
            }
            finishLoading();
            updateDialog(debloatObject);
            updateDialog(debloatObject.getSuggestions());
        });
        viewModel.findDebloatObject(packageName);
    }

    private void updateDialog(@NonNull DebloatObject debloatObject) {
        Drawable icon = debloatObject.getIcon();
        mAppIconView.setImageDrawable(icon != null ? icon : requireActivity().getPackageManager().getDefaultActivityIcon());
        int[] users = debloatObject.getUsers();
        if (users != null && users.length > 0) {
            mOpenAppInfoButton.setVisibility(View.VISIBLE);
            mOpenAppInfoButton.setOnClickListener(v -> {
                Intent appDetailsIntent = AppDetailsActivity.getIntent(requireContext(), debloatObject.packageName,
                        users[0]);
                startActivity(appDetailsIntent);
                dismiss();
            });
        } else {
            mOpenAppInfoButton.setVisibility(View.GONE);
        }
        CharSequence label = debloatObject.getLabel();
        mAppLabelView.setText(label != null ? label : debloatObject.packageName);
        mPackageNameView.setText(debloatObject.packageName);
        String warning = debloatObject.getWarning();
        if (warning != null) {
            mWarningView.setVisibility(View.VISIBLE);
            mWarningView.setText(warning);
            if (debloatObject.getRemoval() != DebloatObject.REMOVAL_CAUTION) {
                mWarningView.setAlertType(MaterialAlertView.ALERT_TYPE_INFO);
            } else mWarningView.setAlertType(MaterialAlertView.ALERT_TYPE_WARN);
        } else mWarningView.setVisibility(View.GONE);
        mDescriptionView.setText(getDescription(debloatObject));
        // Add tags
        int removalColor;
        @StringRes
        int removalRes;
        switch (debloatObject.getRemoval()) {
            case DebloatObject.REMOVAL_SAFE:
                removalColor = ColorCodes.getRemovalSafeIndicatorColor(requireContext());
                removalRes = R.string.debloat_removal_safe_short_description;
                break;
            default:
            case DebloatObject.REMOVAL_CAUTION:
                removalColor = ColorCodes.getRemovalCautionIndicatorColor(requireContext());
                removalRes = R.string.debloat_removal_caution_short_description;
                break;
            case DebloatObject.REMOVAL_REPLACE:
                removalColor = ColorCodes.getRemovalReplaceIndicatorColor(requireContext());
                removalRes = R.string.debloat_removal_replace_short_description;
                break;
        }
        mFlowLayout.removeAllViews();
        addTag(mFlowLayout, debloatObject.type);
        addTag(mFlowLayout, removalRes, removalColor);
    }

    private void updateDialog(@Nullable List<SuggestionObject> suggestionObjects) {
        if (suggestionObjects == null || suggestionObjects.isEmpty()) {
            mSuggestionContainer.setVisibility(View.GONE);
            return;
        }
        mSuggestionContainer.setVisibility(View.VISIBLE);
        mAdapter.setList(suggestionObjects);
    }

    @NonNull
    private CharSequence getDescription(@NonNull DebloatObject debloatObject) {
        String description = debloatObject.getDescription();
        String[] refSites = debloatObject.getWebRefs();
        String[] dependencies = debloatObject.getDependencies();
        String[] requiredBy = debloatObject.getRequiredBy();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(description.trim());
        if (dependencies.length > 0) {
            // Add dependencies
            if (dependencies.length == 1) {
                sb.append(UIUtils.getBoldString("\n\nDependency: ")).append(dependencies[0]);
            } else {
                sb.append(UIUtils.getBoldString("\n\nDependencies\n"))
                        .append(UiUtils.getOrderedList(Arrays.asList(dependencies)));
            }
        }
        if (requiredBy.length > 0) {
            // Add dependencies
            if (requiredBy.length == 1) {
                sb.append(UIUtils.getBoldString("\n\nRequired by: ")).append(requiredBy[0]);
            } else {
                sb.append(UIUtils.getBoldString("\n\nRequired by\n"))
                        .append(UiUtils.getOrderedList(Arrays.asList(requiredBy)));
            }
        }
        if (refSites.length > 0) {
            // Add references
            sb.append(UIUtils.getBoldString("\n\nReferences\n"))
                    .append(UiUtils.getOrderedList(Arrays.asList(refSites)));
        }
        return sb;
    }

    private void addTag(@NonNull ViewGroup parent, @StringRes int titleRes, @ColorInt int textColor) {
        Chip chip = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.item_chip, parent, false);
        chip.setText(titleRes);
        if (textColor >= 0) {
            chip.setTextColor(textColor);
        }
        parent.addView(chip);
    }

    private void addTag(@NonNull ViewGroup parent, @NonNull CharSequence title) {
        Chip chip = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.item_chip, parent, false);
        chip.setText(title);
        parent.addView(chip);
    }

    public static class BloatwareDetailsViewModel extends AndroidViewModel {
        public final MutableLiveData<DebloatObject> debloatObjectLiveData = new MutableLiveData<>();

        public BloatwareDetailsViewModel(@NonNull Application application) {
            super(application);
        }

        public void findDebloatObject(@NonNull String packageName) {
            ThreadUtils.postOnBackgroundThread(() -> {
                List<DebloatObject> debloatObjects = StaticDataset.getDebloatObjects();
                for (DebloatObject debloatObject : debloatObjects) {
                    if (packageName.equals(debloatObject.packageName)) {
                        debloatObject.fillInstallInfo(getApplication(), new AppDb());
                        debloatObjectLiveData.postValue(debloatObject);
                        return;
                    }
                }
                debloatObjectLiveData.postValue(null);
            });
        }
    }

    private class SuggestionsAdapter extends RecyclerView.Adapter<SuggestionsAdapter.SuggestionViewHolder> {
        private final List<SuggestionObject> mSuggestions = Collections.synchronizedList(new ArrayList<>());

        public SuggestionsAdapter() {
        }

        public void setList(@NonNull List<SuggestionObject> suggestions) {
            AdapterUtils.notifyDataSetChanged(this, mSuggestions, suggestions);
        }

        @NonNull
        @Override
        public SuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bloatware_details, parent, false);
            return new SuggestionViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SuggestionViewHolder holder, int position) {
            SuggestionObject suggestion = mSuggestions.get(position);
            holder.labelView.setText(suggestion.getLabel());
            holder.packageNameView.setText(suggestion.packageName);
            int[] users = suggestion.getUsers();
            if (users != null && users.length > 0) {
                MaterialButton appInfoButton = holder.marketOrAppInfoButton;
                appInfoButton.setIconResource(io.github.muntashirakon.ui.R.drawable.ic_information);
                appInfoButton.setOnClickListener(v -> {
                    Intent appDetailsIntent = AppDetailsActivity.getIntent(requireContext(), suggestion.packageName,
                            users[0]);
                    startActivity(appDetailsIntent);
                });
            } else {
                MaterialButton marketButton = holder.marketOrAppInfoButton;
                marketButton.setIconResource(suggestion.isInFDroidMarket() ? R.drawable.ic_frost_fdroid : R.drawable.ic_frost_aurorastore);
                marketButton.setOnClickListener(v -> {
                    Intent appDetailsIntent = suggestion.getMarketLink();
                    appDetailsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(appDetailsIntent);
                    } catch (Throwable th) {
                        UIUtils.displayLongToast("Error: " + th.getMessage());
                    }
                });
            }
            String reason = suggestion.getReason();
            StringBuilder sb = new StringBuilder();
            if (reason != null) sb.append(reason).append("\n");
            sb.append(suggestion.getRepo());
            holder.repoView.setText(sb);
        }

        @Override
        public int getItemCount() {
            return mSuggestions.size();
        }

        @Override
        public long getItemId(int position) {
            return mSuggestions.get(position).hashCode();
        }

        private class SuggestionViewHolder extends RecyclerView.ViewHolder {
            final TextView labelView;
            final TextView packageNameView;
            final TextView repoView;
            final MaterialButton marketOrAppInfoButton;

            public SuggestionViewHolder(@NonNull View itemView) {
                super(itemView);
                labelView = itemView.findViewById(R.id.name);
                packageNameView = itemView.findViewById(R.id.package_name);
                repoView = itemView.findViewById(R.id.message);
                marketOrAppInfoButton = itemView.findViewById(R.id.info);
            }
        }
    }
}
