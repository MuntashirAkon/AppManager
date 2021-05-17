// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.content.IContentProvider;
import android.os.Build;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.O)
public class ContentProviderHolder implements Parcelable {
    public IContentProvider provider;
}
