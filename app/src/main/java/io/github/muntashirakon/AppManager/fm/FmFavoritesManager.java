// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.FmFavorite;
import io.github.muntashirakon.io.Path;

public final class FmFavoritesManager {

    private static final MutableLiveData<FmFavorite> sFavoriteAddedLiveData = new MutableLiveData<>();

    public static LiveData<FmFavorite> getFavoriteAddedLiveData() {
        return sFavoriteAddedLiveData;
    }

    @WorkerThread
    public static long addToFavorite(@NonNull Path path, @NonNull FmActivity.Options options) {
        FmFavorite fmFavorite = new FmFavorite();
        fmFavorite.name = path.getName();
        fmFavorite.uri = options.isVfs() ? options.uri.toString() : path.getUri().toString();
        fmFavorite.initUri = options.isVfs() ? path.getUri().toString() : null;
        fmFavorite.options = options.options;
        long id = AppsDb.getInstance().fmFavoriteDao().insert(fmFavorite);
        sFavoriteAddedLiveData.postValue(fmFavorite);
        return id;
    }

    @WorkerThread
    public static void removeFromFavorite(long id) {
        AppsDb.getInstance().fmFavoriteDao().delete(id);
        sFavoriteAddedLiveData.postValue(null);
    }

    public static void renameFavorite(long id, @NonNull String newName) {
        AppsDb.getInstance().fmFavoriteDao().rename(id, newName);
        sFavoriteAddedLiveData.postValue(null);
    }

    @WorkerThread
    public static List<FmFavorite> getAllFavorites() {
        return AppsDb.getInstance().fmFavoriteDao().getAll();
    }
}
