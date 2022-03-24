// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.io.Path;

public class FmViewModel extends AndroidViewModel {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final MutableLiveData<List<FmItem>> fmItems = new MutableLiveData<>();
    private Path currentPath;

    public FmViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }

    @AnyThread
    public boolean hasParent() {
        if (currentPath != null) {
            return currentPath.getParentFile() != null;
        }
        return false;
    }

    @AnyThread
    public void loadFiles(Uri uri) {
        Path path = new Path(getApplication(), uri);
        loadFiles(path);
    }

    public void reload() {
        if (currentPath != null) {
            loadFiles(currentPath);
        }
    }

    @AnyThread
    private void loadFiles(Path path) {
        currentPath = path;
        executor.submit(() -> {
            if (!path.isDirectory()) return;
            List<FmItem> fmItems = new ArrayList<>();
            Path[] children = path.listFiles();
            for (Path child : children) {
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
