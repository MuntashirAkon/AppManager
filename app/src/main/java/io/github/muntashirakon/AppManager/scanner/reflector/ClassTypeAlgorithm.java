// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.reflector;

import androidx.annotation.NonNull;

import java.util.Hashtable;

// Copyright 2020 Muntashir Al-Islam
// Copyright 2015 Google, Inc.
class ClassTypeAlgorithm {
    private ClassTypeAlgorithm() {}

    @NonNull
    public static String TypeName(@NonNull String nm, Hashtable<String, String> ht) {
        String yy;
        String arr;

        if (nm.charAt(0) != '[') {
            int i = nm.lastIndexOf(".");
            if (i == -1)
                return nm; // It's a primitive type, ignore it.
            else {
                yy = nm.substring(i + 1);
                if (ht != null)
                    ht.put(nm, yy); // note class types in the hashtable.
                return yy;
            }
        }
        arr = "[]";
        if (nm.charAt(1) == '[')
            yy = TypeName(nm.substring(1), ht);
        else {
            switch (nm.charAt(1)) {
                case 'L':
                    yy = TypeName(nm.substring(nm.indexOf("L") + 1, nm.indexOf(";")), ht);
                    break;
                case 'I': yy = "int"; break;
                case 'V': yy = "void"; break;
                case 'C': yy = "char"; break;
                case 'D': yy = "double"; break;
                case 'F': yy = "float"; break;
                case 'J': yy = "long"; break;
                case 'S': yy = "short"; break;
                case 'Z': yy = "boolean"; break;
                case 'B': yy = "byte"; break;
                default: yy = "BOGUS:" + nm;
            }
        }
        return yy + arr;
    }
}