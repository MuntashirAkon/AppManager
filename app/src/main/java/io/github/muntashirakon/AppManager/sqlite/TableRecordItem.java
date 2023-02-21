// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sqlite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evrencoskun.tableview.sort.ISortableModel;

public class TableRecordItem implements ISortableModel {
    @NonNull
    public final String id;
    public final Object data;

    public TableRecordItem(@NonNull String id, Object data) {
        this.id = id;
        this.data = data;
    }

    @NonNull
    @Override
    public String getId() {
        return id;
    }

    @Nullable
    @Override
    public Object getContent() {
        return data;
    }
}
