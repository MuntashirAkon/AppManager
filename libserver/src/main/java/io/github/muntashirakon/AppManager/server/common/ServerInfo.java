package com.braintreepayments.api;

import android.os.Parcel;
import android.os.Parcelable;

import com.cardinalcommerce.shared.userinterfaces.Customization;

/**
 * Base customization options for 3D Secure 2 flows.
 */
public class ThreeDSecureV2BaseCustomization implements Parcelable {

    private String textFontName;
    private String textColor;
    private int textFontSize;
    protected Customization cardinalValue;

    ThreeDSecureV2BaseCustomization() {}

    /**
     * @param textFontName Font type for the UI element.
     */
    public void setTextFontName(String textFontName) {
        this.textFontName = textFontName;
        cardinalValue.setTextFontName(textFontName);
    }

    /**
     * @param textColor Color code in Hex format. For example, the color code can be “#999999”.
     */
    public void setTextColor(String textColor) {
        this.textColor = textColor;
        cardinalValue.setTextColor(textColor);
    }

    /**
     * @param textFontSize Font size for the UI element.
     */
    public void setTextFontSize(int textFontSize) {
        this.textFontSize = textFontSize;
        cardinalValue.setTextFontSize(textFontSize);
    }

    /**
     * @return Font type for the UI element.
     */
    public String getTextFontName() {
        return textFontName;
    }

    /**
     * @return Color code in Hex format.
     */
    public String getTextColor() {
        return textColor;
    }

    /**
     * @return Font size for the UI element.
     */
    public int getTextFontSize() {
        return textFontSize;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(textFontName);
        parcel.writeString(textColor);
        parcel.writeInt(textFontSize);
    }

    protected ThreeDSecureV2BaseCustomization(Parcel in) {
        textFontName = in.readString();
        textColor = in.readString();
        textFontSize = in.readInt();
    }

    public static final Creator<ThreeDSecureV2BaseCustomization> CREATOR = new Creator<ThreeDSecureV2BaseCustomization>() {
        @Override
        public ThreeDSecureV2BaseCustomization createFromParcel(Parcel in) {
            return new ThreeDSecureV2BaseCustomization(in);
        }

        @Override
        public ThreeDSecureV2BaseCustomization[] newArray(int size) {
            return new ThreeDSecureV2BaseCustomization[size];
        }
    };
}
// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

// Copyright 2017 Zheng Li
public class ServerInfo implements Parcelable {
    public String protocolVersion = DataTransmission.PROTOCOL_VERSION;

    public String startArgs;
    public long startTime;
    public long startRealTime;
    public long rxBytes;  // Received
    public long txBytes;  // Sent
    public long successCount;
    public long errorCount;


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(this.protocolVersion);
        dest.writeString(this.startArgs);
        dest.writeLong(this.startTime);
        dest.writeLong(this.startRealTime);
        dest.writeLong(this.rxBytes);
        dest.writeLong(this.txBytes);
        dest.writeLong(this.successCount);
        dest.writeLong(this.errorCount);
    }

    public ServerInfo() {
    }

    protected ServerInfo(@NonNull Parcel in) {
        this.protocolVersion = in.readString();
        this.startArgs = in.readString();
        this.startTime = in.readLong();
        this.startRealTime = in.readLong();
        this.rxBytes = in.readLong();
        this.txBytes = in.readLong();
        this.successCount = in.readLong();
        this.errorCount = in.readLong();
    }

    public static final Creator<ServerInfo> CREATOR = new Creator<ServerInfo>() {
        @NonNull
        @Override
        public ServerInfo createFromParcel(Parcel source) {
            return new ServerInfo(source);
        }

        @NonNull
        @Override
        public ServerInfo[] newArray(int size) {
            return new ServerInfo[size];
        }
    };

    @NonNull
    @Override
    public String toString() {
        return "ServerInfo{" +
                "protocolVersion='" + protocolVersion + '\'' +
                ", startArgs='" + startArgs + '\'' +
                ", startTime=" + startTime +
                ", startRealTime=" + startRealTime +
                ", rxBytes=" + rxBytes +
                ", txBytes=" + txBytes +
                ", successCount=" + successCount +
                ", errorCount=" + errorCount +
                '}';
    }
}
