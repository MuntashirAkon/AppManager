/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.classysharkandroid.reflector;

import java.util.Hashtable;

import androidx.annotation.NonNull;

public class ClassTypeAlgorithm {
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
