// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import java.io.File;

public class AppDetailsLibraryItem<T> extends AppDetailsItem<T> {
    public long size;
    public String type;
    public File path;

    public AppDetailsLibraryItem(T item) {
        super(item);
    }
}
