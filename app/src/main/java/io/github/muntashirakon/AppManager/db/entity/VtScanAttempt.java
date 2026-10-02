// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "vt_scan_attempt", foreignKeys = @ForeignKey(
        entity = VtFile.class,
        parentColumns = "id",
        childColumns = "file_id",
        onDelete = ForeignKey.CASCADE
), indices = {
        @Index(name = "index_vt_scan_attempt_file_id", value = {"file_id"}),
        @Index(name = "index_vt_scan_attempt_status", value = {"status"}),
        @Index(name = "index_vt_scan_attempt_updated_at", value = {"updated_at"})
})
public class VtScanAttempt {
    @StringDef({
            TYPE_LOOKUP,
            TYPE_UPLOAD,
            TYPE_UPDATE,
            TYPE_RESCAN
    })
    public @interface VtScanType {
    }

    public static final String TYPE_LOOKUP = "lookup";
    public static final String TYPE_UPLOAD = "upload";
    public static final String TYPE_UPDATE = "update";
    public static final String TYPE_RESCAN = "rescan";

    @StringDef({
            STATUS_PENDING_CONSENT,
            STATUS_QUEUED,
            STATUS_LOOKING_UP,
            STATUS_UPLOADING,
            STATUS_ANALYSING,
            STATUS_COMPLETED,
            STATUS_FAILED,
            STATUS_CANCELLED
    })
    public @interface VtScanStatus {
    }
    public static final String STATUS_PENDING_CONSENT = "pending_consent";
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_LOOKING_UP = "looking_up";
    public static final String STATUS_UPLOADING = "uploading";
    public static final String STATUS_ANALYSING = "analysing";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELLED = "cancelled";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "file_id")
    public long fileId;

    @NonNull
    @VtScanType
    @ColumnInfo(name = "type")
    public String type = TYPE_LOOKUP;

    @NonNull
    @VtScanStatus
    @ColumnInfo(name = "status")
    public String status = STATUS_QUEUED;

    @Nullable
    @ColumnInfo(name = "analysis_id")
    public String analysisId;

    @Nullable
    @ColumnInfo(name = "permalink")
    public String permalink;

    @Nullable
    @ColumnInfo(name = "report_json")
    public String reportJson;

    @Nullable
    @ColumnInfo(name = "detected")
    public Integer detected;

    @Nullable
    @ColumnInfo(name = "total")
    public Integer total;

    @Nullable
    @ColumnInfo(name = "error_code")
    public String errorCode;

    @Nullable
    @ColumnInfo(name = "error_message")
    public String errorMessage;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "started_at")
    public long startedAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    @ColumnInfo(name = "completed_at")
    public long completedAt;

    @ColumnInfo(name = "read_at")
    public long readAt;
}
