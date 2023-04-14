// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sqlite;

public class ColumnHeader extends TableRecordItem {

    public ColumnHeader(String id) {
        super(id, null);
    }

    public ColumnHeader(String id, String data) {
        super(id, data);
    }
}
