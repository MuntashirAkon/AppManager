// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sqlite;

public class RecordItemHeader extends TableRecordItem {

    public RecordItemHeader(String id) {
        super(id, null);
    }

    public RecordItemHeader(String id, String data) {
        super(id, data);
    }
}
