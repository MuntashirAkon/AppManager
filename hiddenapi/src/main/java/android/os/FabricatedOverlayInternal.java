//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package android.os;

import androidx.annotation.RequiresApi;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class FabricatedOverlayInternal implements Parcelable {
    public String packageName;
    public String overlayName;
    public String targetPackageName;
    public String targetOverlayable;
    public List<FabricatedOverlayInternalEntry> entries;
    public static final Parcelable.Creator<FabricatedOverlayInternal> CREATOR = new Parcelable.Creator<FabricatedOverlayInternal>() {
        public FabricatedOverlayInternal createFromParcel(Parcel var1) {
            FabricatedOverlayInternal var2 = new FabricatedOverlayInternal();
            var2.readFromParcel(var1);
            return var2;
        }

        public FabricatedOverlayInternal[] newArray(int var1) {
            return new FabricatedOverlayInternal[var1];
        }
    };

    public FabricatedOverlayInternal() {
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public final void writeToParcel(Parcel var1, int var2) {
        int var3 = var1.dataPosition();
        var1.writeInt(0);
        var1.writeString(this.packageName);
        var1.writeString(this.overlayName);
        var1.writeString(this.targetPackageName);
        var1.writeString(this.targetOverlayable);
        var1.writeTypedList(this.entries, var2);
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
                if (var1.dataPosition() - var2 >= var3) {
                    return;
                }

                this.entries = var1.createTypedArrayList(FabricatedOverlayInternalEntry.CREATOR);
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
        var1 |= this.describeContents(this.entries);
        return var1;
    }

    private int describeContents(Object var1) {
        if (var1 == null) {
            return 0;
        } else if (!(var1 instanceof Collection)) {
            return var1 instanceof Parcelable ? ((Parcelable)var1).describeContents() : 0;
        } else {
            int var2 = 0;

            Object var4;
            for(Iterator var3 = ((Collection)var1).iterator(); var3.hasNext(); var2 |= this.describeContents(var4)) {
                var4 = var3.next();
            }

            return var2;
        }
    }
}
