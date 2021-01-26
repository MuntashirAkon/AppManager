package android.app;

import android.net.Uri;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class GrantedUriPermission implements Parcelable {
    public final Uri uri;
    public final String packageName;

    public GrantedUriPermission(@NonNull Uri uri, @Nullable String packageName) {
        this.uri = uri;
        this.packageName = packageName;
    }
}
