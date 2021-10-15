// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.struct.resource;

import androidx.annotation.NonNull;

// Copyright 2014 Liu Dong
public class TypeSpec {
    private long[] entryFlags;
    private String name;
    private short id;

    public TypeSpec(TypeSpecHeader header) {
        this.id = header.getId();
    }

    public boolean exists(int id) {
        return id < entryFlags.length;
    }

    public long[] getEntryFlags() {
        return entryFlags;
    }

    public void setEntryFlags(long[] entryFlags) {
        this.entryFlags = entryFlags;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public short getId() {
        return id;
    }

    public void setId(short id) {
        this.id = id;
    }

    @NonNull
    @Override
    public String toString() {
        return "TypeSpec{" +
                "name='" + name + '\'' +
                ", id=" + id +
                '}';
    }
}
