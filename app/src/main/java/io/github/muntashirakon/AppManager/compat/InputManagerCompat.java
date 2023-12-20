// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.Context;
import android.hardware.input.IInputManager;
import android.hardware.input.InputManagerHidden;
import android.os.Build;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

// Based on https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/input/InputShellCommand.java;l=350;drc=0b80090e02814093f2187c2ce7e64f87cb917edc
public class InputManagerCompat {
    @RequiresPermission(ManifestCompat.permission.INJECT_EVENTS)
    public static boolean sendKeyEvent(int keyCode, boolean longpress) {
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_UNKNOWN);
        boolean success = true;

        success &= injectKeyEvent(event);
        if (longpress) {
            sleep(ViewConfiguration.getLongPressTimeout());
            // Some long press behavior would check the event time, we set a new event time here.
            long nextEventTime = now + ViewConfiguration.getLongPressTimeout();
            success &=injectKeyEvent(KeyEvent.changeTimeRepeat(event, nextEventTime, 1,
                    KeyEvent.FLAG_LONG_PRESS));
        }
        success &= injectKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP));
        return success;
    }

    @RequiresPermission(ManifestCompat.permission.INJECT_EVENTS)
    public static boolean injectKeyEvent(KeyEvent event) {
        return injectInputEvent(event, InputManagerHidden.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH, -1);
    }

    @RequiresPermission(ManifestCompat.permission.INJECT_EVENTS)
    public static boolean injectInputEvent(@NonNull InputEvent event, int mode, int targetUid) {
        IInputManager inputManager = getInputManager();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return inputManager.injectInputEventToTarget(event, mode, targetUid);
            } else return inputManager.injectInputEvent(event, mode);
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * Puts the thread to sleep for the provided time.
     *
     * @param milliseconds The time to sleep in milliseconds.
     */
    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static IInputManager getInputManager() {
        return IInputManager.Stub.asInterface(ProxyBinder.getService(Context.INPUT_SERVICE));
    }
}
