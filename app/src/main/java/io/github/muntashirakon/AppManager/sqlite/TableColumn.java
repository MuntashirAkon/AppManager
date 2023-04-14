// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sqlite;

public class TableColumn {
    public final String name;
    public final String type;
    public final int nonNull;
    public final int pk;
    public final String def;

    public TableColumn(String name, String type, int nonNull, int pk, String def) {
        this.name = name;
        this.type = type;
        this.nonNull = nonNull;
        this.pk = pk;
        this.def = def;
    }

    public String getHeaderName() {
        return name + " (" + type + ")";
    }
}
