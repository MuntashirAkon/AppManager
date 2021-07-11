// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.io.Path;

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

    public void loadFiles(Uri uri) throws FileNotFoundException {
        Path path = new Path(getApplication(), uri);
        loadFiles(path);
    }

    public void loadFiles(Path path) {
        executor.submit(() -> {
            if (!path.isDirectory()) return;
            List<FmItem> fmItems = new ArrayList<>();
            Path parentDir = path.getParentFile();
            if (parentDir != null) {
                fmItems.add(new FmItem("..", parentDir));
            }
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
