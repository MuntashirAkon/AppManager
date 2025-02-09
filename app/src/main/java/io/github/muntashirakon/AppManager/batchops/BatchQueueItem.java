// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.os.ParcelCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager.OpType;
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.util.ParcelUtils;

public class BatchQueueItem implements Parcelable, IJsonSerializer {
    @NonNull
    public static BatchQueueItem getBatchOpQueue(@OpType int op,
                                                 @Nullable ArrayList<String> packages,
                                                 @Nullable ArrayList<Integer> users,
                                                 @Nullable IBatchOpOptions options) {
        return new BatchQueueItem(R.string.batch_ops, op, packages, users, options);
    }

    @NonNull
    public static BatchQueueItem getOneClickQueue(@OpType int op,
                                                  @Nullable ArrayList<String> packages,
                                                  @Nullable ArrayList<Integer> users,
                                                  @Nullable IBatchOpOptions args) {
        return new BatchQueueItem(R.string.one_click_ops, op, packages, users, args);
    }

    @StringRes
    private final int mTitleRes;
    @OpType
    private final int mOp;
    @NonNull
    private ArrayList<String> mPackages;
    @Nullable
    private ArrayList<Integer> mUsers;
    @Nullable
    private final IBatchOpOptions mOptions;

    private BatchQueueItem(@StringRes int titleRes,
                           @OpType int op,
                           @Nullable ArrayList<String> packages,
                           @Nullable ArrayList<Integer> users,
                           @Nullable IBatchOpOptions options) {
        mTitleRes = titleRes;
        mOp = op;
        mPackages = packages != null ? packages : new ArrayList<>(0);
        mUsers = users;
        mOptions = options;
    }

    @StringRes
    public int getTitleRes() {
        return mTitleRes;
    }

    @Nullable
    public String getTitle() {
        try {
            return ContextUtils.getContext().getString(mTitleRes);
        } catch (Resources.NotFoundException e) {
            // This resource may not always be found
            return null;
        }
    }

    public int getOp() {
        return mOp;
    }

    @NonNull
    public ArrayList<String> getPackages() {
        return mPackages;
    }

    public void setPackages(@NonNull ArrayList<String> packages) {
        mPackages = packages;
    }

    @NonNull
    public ArrayList<Integer> getUsers() {
        if (mUsers == null) {
            int size = mPackages.size();
            int userId = UserHandleHidden.myUserId();
            mUsers = new ArrayList<>(size);
            for (int i = 0; i < size; ++i) {
                mUsers.add(userId);
            }
        } else {
            assert mPackages.size() == mUsers.size();
        }
        return mUsers;
    }

    public void setUsers(@Nullable ArrayList<Integer> users) {
        mUsers = users;
    }

    @Nullable
    public IBatchOpOptions getOptions() {
        return mOptions;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mTitleRes);
        dest.writeInt(mOp);
        dest.writeStringList(mPackages);
        ParcelUtils.writeArrayList(mUsers, dest);
        dest.writeParcelable(mOptions, flags);
    }

    protected BatchQueueItem(@NonNull JSONObject jsonObject) throws JSONException {
        mTitleRes = jsonObject.getInt("title_res");
        mOp = jsonObject.getInt("op");
        mPackages = JSONUtils.getArray(jsonObject.getJSONArray("packages"));
        mUsers = JSONUtils.getArray(jsonObject.getJSONArray("users"));
        JSONObject options = jsonObject.optJSONObject("options");
        mOptions = options != null ? IBatchOpOptions.DESERIALIZER.deserialize(options) : null;
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title_res", mTitleRes);
        jsonObject.put("op", mOp);
        jsonObject.put("packages", JSONUtils.getJSONArray(mPackages));
        jsonObject.put("users", JSONUtils.getJSONArray(mUsers));
        jsonObject.put("options", mOptions != null ? mOptions.serializeToJson() : null);
        return jsonObject;
    }

    protected BatchQueueItem(@NonNull Parcel in) {
        mTitleRes = in.readInt();
        mOp = in.readInt();
        mPackages = Objects.requireNonNull(in.createStringArrayList());
        mUsers = ParcelUtils.readArrayList(in, Integer.class.getClassLoader());
        mOptions = ParcelCompat.readParcelable(in, IBatchOpOptions.class.getClassLoader(), IBatchOpOptions.class);
    }

    public static final JsonDeserializer.Creator<BatchQueueItem> DESERIALIZER = BatchQueueItem::new;

    public static final Creator<BatchQueueItem> CREATOR = new Creator<BatchQueueItem>() {
        @NonNull
        @Override
        public BatchQueueItem createFromParcel(@NonNull Parcel in) {
            return new BatchQueueItem(in);
        }

        @NonNull
        @Override
        public BatchQueueItem[] newArray(int size) {
            return new BatchQueueItem[size];
        }
    };
}
