// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref;

import android.app.Application;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.io.Path;

public class SharedPrefsViewModel extends AndroidViewModel {
    private final MultithreadedExecutor mExecutor = MultithreadedExecutor.getNewInstance();
    private final MutableLiveData<Map<String, Object>> mSharedPrefsMapLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mSharedPrefsSavedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mSharedPrefsDeletedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mSharedPrefsModifiedLiveData = new MutableLiveData<>();

    // TODO: 8/2/22 Use AtomicExtendedFile to better handle errors
    private Path mSharedPrefsFile;
    private Map<String, Object> mSharedPrefsMap;
    private boolean mModified;

    public SharedPrefsViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdownNow();
        super.onCleared();
    }

    public void setSharedPrefsFile(@NonNull Path sharedPrefFile) {
        mSharedPrefsFile = sharedPrefFile;
    }

    public boolean isModified() {
        return mModified;
    }

    @Nullable
    public String getSharedPrefFilename() {
        if (mSharedPrefsFile != null) {
            return mSharedPrefsFile.getName();
        }
        return null;
    }

    @Nullable
    public Object getValue(@NonNull String key) {
        return mSharedPrefsMap.get(key);
    }

    public void remove(@NonNull String key) {
        mSharedPrefsModifiedLiveData.postValue(mModified = true);
        mSharedPrefsMap.remove(key);
        mSharedPrefsMapLiveData.postValue(mSharedPrefsMap);
    }

    public void add(@NonNull String key, @NonNull Object value) {
        mSharedPrefsModifiedLiveData.postValue(mModified = true);
        mSharedPrefsMap.put(key, value);
        mSharedPrefsMapLiveData.postValue(mSharedPrefsMap);
    }

    public LiveData<Map<String, Object>> getSharedPrefsMapLiveData() {
        return mSharedPrefsMapLiveData;
    }

    public LiveData<Boolean> getSharedPrefsSavedLiveData() {
        return mSharedPrefsSavedLiveData;
    }

    public LiveData<Boolean> getSharedPrefsDeletedLiveData() {
        return mSharedPrefsDeletedLiveData;
    }

    public LiveData<Boolean> getSharedPrefsModifiedLiveData() {
        return mSharedPrefsModifiedLiveData;
    }

    @AnyThread
    public void deleteSharedPrefFile() {
        mExecutor.submit(() -> mSharedPrefsDeletedLiveData.postValue(mSharedPrefsFile.delete()));
    }

    @AnyThread
    public void writeSharedPrefs() {
        mExecutor.submit(() -> {
            try (OutputStream xmlFile = mSharedPrefsFile.openOutputStream()) {
                SharedPrefsUtil.writeSharedPref(xmlFile, mSharedPrefsMap);
                // TODO: 9/7/21 Investigate the state of permission (should be unchanged)
                mSharedPrefsSavedLiveData.postValue(true);
                mSharedPrefsModifiedLiveData.postValue(mModified = false);
            } catch (IOException e) {
                e.printStackTrace();
                mSharedPrefsSavedLiveData.postValue(false);
            }
        });
    }

    @AnyThread
    public void loadSharedPrefs() {
        mExecutor.submit(() -> {
            try (InputStream rulesStream = mSharedPrefsFile.openInputStream()) {
                mSharedPrefsModifiedLiveData.postValue(mModified = false);
                mSharedPrefsMap = SharedPrefsUtil.readSharedPref(rulesStream);
                mSharedPrefsMapLiveData.postValue(mSharedPrefsMap);
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
                mSharedPrefsMap = new HashMap<>();
                mSharedPrefsMapLiveData.postValue(mSharedPrefsMap);
            }
        });
    }
}
