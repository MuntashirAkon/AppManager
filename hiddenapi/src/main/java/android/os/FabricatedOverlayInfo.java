//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package android.os;

public class FabricatedOverlayInfo implements Parcelable {
    public String path;
    public String packageName;
    public String overlayName;
    public String targetPackageName;
    public String targetOverlayable;
    public static final Parcelable.Creator<FabricatedOverlayInfo> CREATOR = new Parcelable.Creator<FabricatedOverlayInfo>() {
        public FabricatedOverlayInfo createFromParcel(Parcel var1) {
            FabricatedOverlayInfo var2 = new FabricatedOverlayInfo();
            var2.readFromParcel(var1);
            return var2;
        }

        public FabricatedOverlayInfo[] newArray(int var1) {
            return new FabricatedOverlayInfo[var1];
        }
    };

    public FabricatedOverlayInfo() {
    }

    public final void writeToParcel(Parcel var1, int var2) {
        int var3 = var1.dataPosition();
        var1.writeInt(0);
        var1.writeString(this.path);
        var1.writeString(this.packageName);
        var1.writeString(this.overlayName);
        var1.writeString(this.targetPackageName);
        var1.writeString(this.targetOverlayable);
        int var4 = var1.dataPosition();
        var1.setDataPosition(var3);
        var1.writeInt(var4 - var3);
        var1.setDataPosition(var4);
    }

    public final void readFromParcel(Parcel var1) {
        int var2 = var1.dataPosition();
        int var3 = var1.readInt();

        try {
            if (var3 < 4) {
                throw new BadParcelableException("Parcelable too small");
            }

            if (var1.dataPosition() - var2 >= var3) {
                return;
            }

            this.path = var1.readString();
            if (var1.dataPosition() - var2 >= var3) {
                return;
            }

            this.packageName = var1.readString();
            if (var1.dataPosition() - var2 >= var3) {
                return;
            }

            this.overlayName = var1.readString();
            if (var1.dataPosition() - var2 < var3) {
                this.targetPackageName = var1.readString();
                if (var1.dataPosition() - var2 >= var3) {
                    return;
                }

                this.targetOverlayable = var1.readString();
                return;
            }
        } finally {
            if (var2 > Integer.MAX_VALUE - var3) {
                throw new BadParcelableException("Overflow in the size of parcelable");
            }

            var1.setDataPosition(var2 + var3);
        }

    }

    public int describeContents() {
        byte var1 = 0;
        return var1;
    }
}
