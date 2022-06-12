// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import androidx.annotation.Nullable;

import io.github.muntashirakon.io.Path;

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class SendLogDetails {
    @Nullable
    private String subject;
    @Nullable
    private Path attachment;
    @Nullable
    private String attachmentType;

    @Nullable
    public String getSubject() {
        return subject;
    }

    public void setSubject(@Nullable String subject) {
        this.subject = subject;
    }

    @Nullable
    public Path getAttachment() {
        return attachment;
    }

    public void setAttachment(@Nullable Path attachment) {
        this.attachment = attachment;
    }

    @Nullable
    public String getAttachmentType() {
        return attachmentType;
    }

    public void setAttachmentType(@Nullable String attachmentType) {
        this.attachmentType = attachmentType;
    }
}
