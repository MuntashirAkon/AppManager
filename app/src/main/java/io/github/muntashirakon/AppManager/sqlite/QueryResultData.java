// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sqlite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class QueryResultData {
    public final List<List<TableRecordItem>> resultRows;
    public final List<TableColumn> resultColumns;
    public final long totalRows;
    public final int limit;
    public final long startOffset;
    @Nullable
    public final String query;
    @Nullable
    public final String tableName;

    public QueryResultData(@NonNull List<List<TableRecordItem>> resultRows,
                           @NonNull List<TableColumn> resultColumns,
                           long totalRows, int limit, long startOffset,
                           @Nullable String query, @Nullable String tableName) {
        this.resultRows = resultRows;
        this.resultColumns = resultColumns;
        this.totalRows = totalRows;
        this.limit = limit;
        this.startOffset = startOffset;
        this.query = query;
        this.tableName = tableName;
    }

    @Nullable
    public Long nextOffset() {
        long nextOffset = startOffset + limit;
        if (nextOffset < totalRows) {
            return nextOffset;
        }
        // No next offset
        return null;
    }

    @Nullable
    public Long previousOffset() {
        long previousOffset = startOffset - limit;
        if (previousOffset < totalRows) {
            if (previousOffset >= 0) {
                return previousOffset;
            }
            // Candidate offset is less than 0.
            // If start offset was not 0, start with 0
            if (startOffset != 0) {
                return 0L;
            }
        }
        // Invalid offset
        return null;
    }
}
