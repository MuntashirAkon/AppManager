// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Menu;

import androidx.activity.EdgeToEdge;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class PerProcessActivity extends AppCompatActivity {
    @CallSuper
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }

    public boolean getTransparentBackground() {
        return false;
    }

    @CallSuper
    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    protected void clearBackStack() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            FragmentManager.BackStackEntry entry = fragmentManager.getBackStackEntryAt(0);
            fragmentManager.popBackStackImmediate(entry.getId(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    protected void removeCurrentFragment(@IdRes int id) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(fragment)
                    .commit();
        }
    }
}
