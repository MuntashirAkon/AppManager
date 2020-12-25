/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package android.content;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.RequiresApi;

public interface IIntentSender extends IInterface {

    int send(int code, Intent intent, String resolvedType,
             IIntentReceiver finishedReceiver, String requiredPermission, Bundle options);

    @RequiresApi(26)
    void send(int code, Intent intent, String resolvedType, IBinder whitelistToken,
              IIntentReceiver finishedReceiver, String requiredPermission, Bundle options);

    abstract class Stub extends Binder implements IIntentSender {
        public static IIntentSender asInterface(android.os.IBinder obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public android.os.IBinder asBinder() {
            throw new UnsupportedOperationException();
        }
    }
}