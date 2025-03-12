//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package android.os;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class FabricatedOverlayInternalEntry implements Parcelable {
    public String resourceName;
    public int dataType = 0;
    public int data = 0;
    public String stringData;
    public ParcelFileDescriptor binaryData;
    public String configuration;
    public long binaryDataOffset = 0L;
    public long binaryDataSize = 0L;
    public boolean isNinePatch = false;
    public static final Parcelable.Creator<FabricatedOverlayInternalEntry> CREATOR = new Parcelable.Creator<FabricatedOverlayInternalEntry>() {
        public FabricatedOverlayInternalEntry createFromParcel(Parcel var1) {
            FabricatedOverlayInternalEntry var2 = new FabricatedOverlayInternalEntry();
            var2.readFromParcel(var1);
            return var2;
        }

        public FabricatedOverlayInternalEntry[] newArray(int var1) {
            return new FabricatedOverlayInternalEntry[var1];
        }
    };

    public FabricatedOverlayInternalEntry() {
    }

    public final void writeToParcel(Parcel var1, int var2) {
        int var3 = var1.dataPosition();
        var1.writeInt(0);
        var1.writeString(this.resourceName);
        var1.writeInt(this.dataType);
        var1.writeInt(this.data);
        var1.writeString(this.stringData);
        var1.writeTypedObject(this.binaryData, var2);
        var1.writeString(this.configuration);
        var1.writeLong(this.binaryDataOffset);
        var1.writeLong(this.binaryDataSize);
        var1.writeBoolean(this.isNinePatch);
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

            this.resourceName = var1.readString();
            if (var1.dataPosition() - var2 >= var3) {
                return;
            }

            this.dataType = var1.readInt();
            if (var1.dataPosition() - var2 >= var3) {
                return;
            }

            this.data = var1.readInt();
            if (var1.dataPosition() - var2 >= var3) {
                return;
            }

            this.stringData = var1.readString();
            if (var1.dataPosition() - var2 < var3) {
                this.binaryData = (ParcelFileDescriptor)var1.readTypedObject(ParcelFileDescriptor.CREATOR);
                if (var1.dataPosition() - var2 >= var3) {
                    return;
                }

                this.configuration = var1.readString();
                if (var1.dataPosition() - var2 >= var3) {
                    return;
                }

                this.binaryDataOffset = var1.readLong();
                if (var1.dataPosition() - var2 >= var3) {
                    return;
                }

                this.binaryDataSize = var1.readLong();
                if (var1.dataPosition() - var2 >= var3) {
                    return;
                }

                this.isNinePatch = var1.readBoolean();
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
        int var1 = 0;
        var1 |= this.describeContents(this.binaryData);
        return var1;
    }

    private int describeContents(Object var1) {
        if (var1 == null) {
            return 0;
        } else {
            return var1 instanceof Parcelable ? ((Parcelable)var1).describeContents() : 0;
        }
    }
}
