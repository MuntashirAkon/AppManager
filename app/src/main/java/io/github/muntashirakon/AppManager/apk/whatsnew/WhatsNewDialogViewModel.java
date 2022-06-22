// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew;

import android.app.Application;
import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.R;

public class WhatsNewDialogViewModel extends AndroidViewModel {
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<List<ApkWhatsNewFinder.Change>> changesLiveData = new MutableLiveData<>();

    public WhatsNewDialogViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdownNow();
        super.onCleared();
    }

    public LiveData<List<ApkWhatsNewFinder.Change>> getChangesLiveData() {
        return changesLiveData;
    }

    public void loadChanges(PackageInfo newPkgInfo, PackageInfo oldPkgInfo) {
        mExecutor.submit(() -> {
            ApkWhatsNewFinder.Change[][] changes = ApkWhatsNewFinder.getInstance().getWhatsNew(newPkgInfo, oldPkgInfo);
            List<ApkWhatsNewFinder.Change> changeList = new ArrayList<>();
            for (ApkWhatsNewFinder.Change[] changes1 : changes) {
                if (changes1.length > 0) {
                    Collections.addAll(changeList, changes1);
                }
            }
            if (changeList.size() == 0) {
                changeList.add(new ApkWhatsNewFinder.Change(ApkWhatsNewFinder.CHANGE_INFO,
                        getApplication().getString(R.string.no_changes)));
            }
            changesLiveData.postValue(changeList);
        });
    }
}
