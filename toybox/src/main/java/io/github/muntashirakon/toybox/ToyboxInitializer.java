/*
 * Copyright 2020 Muntashir Al-Islam
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.muntashirakon.toybox;

import android.content.Context;

import com.topjohnwu.superuser.Shell;

import java.io.File;

import androidx.annotation.NonNull;

public class ToyboxInitializer extends Shell.Initializer {
    public static final String TOYBOX = "libtoybox.so";
    private static File TOYBOX_LIB;

    @Override
    public boolean onInit(@NonNull Context context, @NonNull Shell shell) {
        getToyboxLib(context);
        shell.newJob().add("export PATH=" + TOYBOX_LIB.getParent() + ":$PATH").exec();
        return true;
    }

    public static File getToyboxLib(Context context) {
        if (TOYBOX_LIB == null) {
            TOYBOX_LIB = new File(context.getApplicationInfo().nativeLibraryDir, TOYBOX);
        }
        return TOYBOX_LIB;
    }
}
