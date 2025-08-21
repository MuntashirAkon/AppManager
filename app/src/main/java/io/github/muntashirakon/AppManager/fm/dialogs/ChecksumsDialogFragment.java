// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs;

import static io.github.muntashirakon.AppManager.utils.UIUtils.displayLongToast;

import android.app.Application;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ClipboardUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.adapters.SelectedArrayAdapter;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.view.TextInputLayoutCompat;
import io.github.muntashirakon.widget.MaterialSpinner;
import io.github.muntashirakon.widget.TextInputTextView;

public class ChecksumsDialogFragment extends DialogFragment {
    public static final String TAG = ChecksumsDialogFragment.class.getSimpleName();

    private static final String ARG_PATH = "path";

    @NonNull
    public static ChecksumsDialogFragment getInstance(@NonNull Path path) {
        ChecksumsDialogFragment fragment = new ChecksumsDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_PATH, path.getUri());
        fragment.setArguments(args);
        return fragment;
    }

    private Path mPath;
    private View mDialogView;
    private RecyclerView mRecyclerView;
    private MaterialSpinner mSpinner;
    private MaterialButton mButton;
    private TextInputTextView mTextView;
    private ChecksumsViewModel mViewModel;
    private ChecksumsRecyclerViewAdapter mAdapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(this).get(ChecksumsViewModel.class);
        mPath = Paths.get(Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_PATH, Uri.class)));
        mAdapter = new ChecksumsRecyclerViewAdapter();
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_checksums, null);
        mRecyclerView = mDialogView.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setAdapter(mAdapter);
        mSpinner = mDialogView.findViewById(R.id.spinner);
        SelectedArrayAdapter<String> algorithmsAdapter = new SelectedArrayAdapter<>(requireContext(),
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item_small, getAlgorithms());
        mSpinner.setAdapter(algorithmsAdapter);
        mButton = mDialogView.findViewById(R.id.action);
        mButton.setOnClickListener(v -> {
            String algorithm = mSpinner.getEditText().getText().toString().trim();
            mViewModel.loadChecksum(algorithm, mPath);
        });
        mTextView = mDialogView.findViewById(R.id.text);
        mTextView.setVisibility(View.GONE);
        DialogTitleBuilder titleBuilder = new DialogTitleBuilder(requireActivity())
                .setTitle(R.string.checksums)
                .setSubtitle(mPath.getName())
                .setEndIcon(R.drawable.ic_content_paste, v -> {
                    String data = ClipboardUtils.readHashValueFromClipboard(v.getContext());
                    if (data != null) {
                        for (Map.Entry<String, String> digest : mAdapter.mNameChecksumMap) {
                            if (digest.getValue().equals(data)) {
                                if (digest.getValue().equals(DigestUtils.MD5) || digest.getValue().equals(DigestUtils.SHA_1)) {
                                    displayLongToast(R.string.verified_using_unreliable_hash);
                                } else {
                                    displayLongToast(R.string.verified);
                                }
                                return;
                            }
                        }
                        displayLongToast(R.string.not_verified);
                    }
                })
                .setEndIconContentDescription(R.string.paste);
        return new MaterialAlertDialogBuilder(requireActivity())
                .setCustomTitle(titleBuilder.build())
                .setView(mDialogView)
                .setNegativeButton(R.string.close, null)
                .create();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return mDialogView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (mViewModel != null) {
            mViewModel.getChecksumsLiveData().observe(getViewLifecycleOwner(), map -> mAdapter.setDefaultList(map));
            mViewModel.getChecksumLiveData().observe(getViewLifecycleOwner(), algoHashPair -> {
                mTextView.setVisibility(View.VISIBLE);
                mTextView.setText(algoHashPair.second);
                TextInputLayoutCompat.fromTextInputEditText(mTextView).setHint(algoHashPair.first);
            });
            mViewModel.loadChecksums(mPath);
        }
    }

    private List<String> getAlgorithms() {
        Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        List<String> algorithms = new ArrayList<>();
        for (Provider.Service service : provider.getServices()) {
            if ("MessageDigest".equals(service.getType())) {
                algorithms.add(service.getAlgorithm());
            }
        }
        return algorithms;
    }

    private static class ChecksumsRecyclerViewAdapter extends RecyclerView.Adapter<ChecksumsRecyclerViewAdapter.ViewHolder> {
        @NonNull
        private final List<Map.Entry<String, String>> mNameChecksumMap = new ArrayList<>();

        public void setDefaultList(@NonNull Map<String, String> map) {
            synchronized (mNameChecksumMap) {
                mNameChecksumMap.clear();
                mNameChecksumMap.addAll(map.entrySet());
                notifyDataSetChanged();
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_text_input_layout_monospace, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map.Entry<String, String> entry;
            synchronized (mNameChecksumMap) {
                entry = mNameChecksumMap.get(position);
            }
            holder.input.setHint(entry.getKey());
            holder.textView.setText(entry.getValue());
        }

        @Override
        public int getItemCount() {
            synchronized (mNameChecksumMap) {
                return mNameChecksumMap.size();
            }
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextInputLayout input;
            TextInputTextView textView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                input = (TextInputLayout) itemView;
                textView = (TextInputTextView) Objects.requireNonNull(input.getEditText());
            }
        }
    }

    public static class ChecksumsViewModel extends AndroidViewModel {
        private final MutableLiveData<Map<String, String>> mChecksumsLiveData = new MutableLiveData<>();
        private final MutableLiveData<Pair<String, String>> mChecksumLiveData = new MutableLiveData<>();

        public ChecksumsViewModel(@NonNull Application application) {
            super(application);
        }

        public LiveData<Map<String, String>> getChecksumsLiveData() {
            return mChecksumsLiveData;
        }

        public LiveData<Pair<String, String>> getChecksumLiveData() {
            return mChecksumLiveData;
        }

        public void loadChecksum(@NonNull String algo, @NonNull Path path) {
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    MessageDigest digest = MessageDigest.getInstance(algo, BouncyCastleProvider.PROVIDER_NAME);
                    try (InputStream is = path.openInputStream()) {
                        byte[] buffer = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
                        int length;
                        while ((length = is.read(buffer)) != -1) {
                            digest.update(buffer, 0, length);
                        }
                    }
                    mChecksumLiveData.postValue(new Pair<>(algo, HexEncoding.encodeToString(digest.digest(), false)));
                } catch (Exception e) {
                    e.printStackTrace();
                    mChecksumLiveData.postValue(new Pair<>(null, null));
                }
            });
        }

        public void loadChecksums(@NonNull Path path) {
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    mChecksumsLiveData.postValue(generateChecksums(path));
                } catch (Exception e) {
                    e.printStackTrace();
                    mChecksumsLiveData.postValue(Collections.emptyMap());
                }
            });
        }

        private Map<String, String> generateChecksums(@NonNull Path path) throws Exception {
            String[] nativeAlgo = new String[]{"MD5", "SHA-1", "SHA-256", "SHA-512"};
            String[] bcAlgo = new String[]{"SHA3-256", "SHA3-512"};
            MessageDigest[] messageDigests = new MessageDigest[nativeAlgo.length + bcAlgo.length];
            String[] algoNames = new String[]{"MD5 (unreliable)", "SHA-1 (unreliable)", "SHA-256", "SHA-512", "SHA3-256", "SHA3-512"};
            for (int i = 0; i < nativeAlgo.length; ++i) {
                messageDigests[i] = MessageDigest.getInstance(nativeAlgo[i]);
            }
            for (int i = 0; i < bcAlgo.length; ++i) {
                messageDigests[nativeAlgo.length + i] = MessageDigest.getInstance(bcAlgo[i], BouncyCastleProvider.PROVIDER_NAME);
            }
            CRC32 crc32 = new CRC32();
            try (InputStream is = path.openInputStream()) {
                byte[] buffer = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
                int length;
                while ((length = is.read(buffer)) != -1) {
                    for (MessageDigest messageDigest : messageDigests) {
                        messageDigest.update(buffer, 0, length);
                    }
                    crc32.update(buffer, 0, length);
                }
            }
            Map<String, String> checksums = new LinkedHashMap<>(messageDigests.length + 1);
            checksums.put("CRC32 (insecure)", HexEncoding.encodeToString(DigestUtils.longToBytes(crc32.getValue()), false));
            for (int i = 0; i < messageDigests.length; ++i) {
                checksums.put(algoNames[i], HexEncoding.encodeToString(messageDigests[i].digest(), false));
            }
            return checksums;
        }
    }
}
