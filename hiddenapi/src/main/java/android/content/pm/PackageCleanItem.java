package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

public class PackageCleanItem implements Parcelable {
    public final int userId;
    public final String packageName;
    public final boolean andCode;

    public PackageCleanItem(int userId, String packageName, boolean andCode) {
        throw new UnsupportedOperationException();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int parcelableFlags) {
        throw new UnsupportedOperationException();
    }

    public static final Parcelable.Creator<PackageCleanItem> CREATOR
            = new Parcelable.Creator<PackageCleanItem>() {
        public PackageCleanItem createFromParcel(Parcel source) {
            return new PackageCleanItem(source);
        }

        public PackageCleanItem[] newArray(int size) {
            return new PackageCleanItem[size];
        }
    };

    private PackageCleanItem(Parcel source) {
        throw new UnsupportedOperationException();
    }
}
