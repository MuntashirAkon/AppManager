// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.view;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.widget.AutoCompleteTextView;
import android.widget.ListPopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.lang.reflect.Field;

public final class AutoCompleteTextViewCompat {
    @SuppressWarnings("JavaReflectionMemberAccess")
    @SuppressLint("DiscouragedPrivateApi")
    public static void setListSelector(@NonNull AutoCompleteTextView view, @Nullable Drawable listSelector) {
        try {
            ListPopupWindow popupWindow;
            Field mPopup = AutoCompleteTextView.class.getDeclaredField("mPopup");
            mPopup.setAccessible(true);
            popupWindow = (ListPopupWindow) mPopup.get(view);
            if (popupWindow != null) {
                popupWindow.setListSelector(listSelector);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setListSelectorMaterial(@NonNull MaterialAutoCompleteTextView view, @Nullable Drawable listSelector) {
        setListSelector(view, listSelector);
        try {
            androidx.appcompat.widget.ListPopupWindow popupWindow;
            Field mPopup = MaterialAutoCompleteTextView.class.getDeclaredField("modalListPopup");
            mPopup.setAccessible(true);
            popupWindow = (androidx.appcompat.widget.ListPopupWindow) mPopup.get(view);
            if (popupWindow != null) {
                popupWindow.setListSelector(listSelector);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
