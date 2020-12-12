/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

@SuppressWarnings("unused")
public class OpsCommands {
    public static final String ACTION_GET = "get";
    public static final String ACTION_SET = "set";
    public static final String ACTION_CHECK = "check";
    public static final String ACTION_RESET = "reset";
    public static final String ACTION_GET_FOR_OPS = "get_f_ops";
    public static final String ACTION_GET_APPS = "get_apps";
    public static final String ACTION_OTHER = "other";

    public static class Builder implements Parcelable {
        private String action = ACTION_GET;
        private String packageName;
        private int userHandleId;
        private int opInt;
        private int modeInt;
        private int[] ops;
        private boolean reqNet;
        private int uidInt;

        public String getAction() {
            return action;
        }

        public Builder setAction(String action) {
            this.action = action;
            return this;
        }

        public String getPackageName() {
            return packageName;
        }

        public Builder setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public int getUidInt() {
            return uidInt;
        }

        public void setUidInt(int uidInt) {
            this.uidInt = uidInt;
        }

        public int getOpInt() {
            return opInt;
        }

        public Builder setOpInt(int opInt) {
            this.opInt = opInt;
            return this;
        }

        public int getModeInt() {
            return modeInt;
        }

        public Builder setModeInt(int modeInt) {
            this.modeInt = modeInt;
            return this;
        }

        public int getUserHandleId() {
            return userHandleId;
        }

        public Builder setUserHandleId(int uid) {
            this.userHandleId = uid;
            return this;
        }

        public int[] getOps() {
            return ops;
        }

        public Builder setOps(int[] ops) {
            this.ops = ops;
            return this;
        }

        public boolean isReqNet() {
            return reqNet;
        }

        public Builder setReqNet(boolean reqNet) {
            this.reqNet = reqNet;
            return this;
        }

        @NonNull
        @Override
        public String toString() {
            return "Builder{" +
                    "action='" + action + '\'' +
                    ", packageName='" + packageName + '\'' +
                    ", userHandleId=" + userHandleId +
                    ", opInt=" + opInt +
                    ", modeInt=" + modeInt +
                    '}';
        }

        public Builder() {
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(this.action);
            dest.writeString(this.packageName);
            dest.writeInt(this.uidInt);
            dest.writeInt(this.userHandleId);
            dest.writeInt(this.opInt);
            dest.writeInt(this.modeInt);
            dest.writeIntArray(this.ops);
            dest.writeByte(this.reqNet ? (byte) 1 : (byte) 0);
        }

        protected Builder(@NonNull Parcel in) {
            this.action = in.readString();
            this.packageName = in.readString();
            this.uidInt = in.readInt();
            this.userHandleId = in.readInt();
            this.opInt = in.readInt();
            this.modeInt = in.readInt();
            this.ops = in.createIntArray();
            this.reqNet = in.readByte() != 0;
        }

        public static final Creator<Builder> CREATOR = new Creator<Builder>() {
            @NonNull
            @Override
            public Builder createFromParcel(Parcel source) {
                return new Builder(source);
            }

            @NonNull
            @Override
            public Builder[] newArray(int size) {
                return new Builder[size];
            }
        };
    }
}
