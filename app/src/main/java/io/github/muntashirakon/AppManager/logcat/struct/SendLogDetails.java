// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import androidx.annotation.Nullable;

import io.github.muntashirakon.io.Path;

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class SendLogDetails {
    @Nullable
    private String mSubject;
    @Nullable
    private Path mAttachment;
    @Nullable
    private String mAttachmentType;

    @Nullable
    public String getSubject() {
        return mSubject;
    }

    public void setSubject(@Nullable String subject) {
        mSubject = subject;
    }

    @Nullable
    public Path getAttachment() {
        return mAttachment;
    }

    public void setAttachment(@Nullable Path attachment) {
        mAttachment = attachment;
    }

    @Nullable
    public String getAttachmentType() {
        return mAttachmentType;
    }

    public void setAttachmentType(@Nullable String attachmentType) {
        mAttachmentType = attachmentType;
    }
}
