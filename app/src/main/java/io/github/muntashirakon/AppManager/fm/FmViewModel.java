// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.io.Storage;

public class FmViewModel extends AndroidViewModel {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final MutableLiveData<List<FmItem>> fmItems = new MutableLiveData<>();

    public FmViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }

    public void loadFiles(File file) {
        Storage storage = new Storage(getApplication(), file);
        loadFiles(storage);
    }

    public void loadFiles(Storage path) {
        executor.submit(() -> {
            if (!path.isDirectory()) return;
            List<FmItem> fmItems = new ArrayList<>();
            Storage parentDir = path.getParentFile();
            if (parentDir != null) {
                fmItems.add(new FmItem("..", parentDir));
            }
            Storage[] children = path.listFiles();
            for (Storage child : children) {
                fmItems.add(new FmItem(child));
            }
            Collections.sort(fmItems);
            this.fmItems.postValue(fmItems);
        });
    }

    public LiveData<List<FmItem>> observeFiles() {
        return fmItems;
    }
}
