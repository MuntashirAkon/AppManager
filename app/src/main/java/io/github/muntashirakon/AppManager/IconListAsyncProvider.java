// This is a modified version of IconListAsyncProvider.java taken
// from https://github.com/butzist/ActivityLauncher/commit/dfb7fe271dae9379b5453bbb6e88f30a1adc94a9
// and was authored by Adam M. Szalkowski with ISC License.
// All derivative works are licensed under GPLv3.0.

package io.github.muntashirakon.AppManager;

import android.content.Context;

public class IconListAsyncProvider extends AsyncProvider<IconListAdapter> {
    private IconListAdapter adapter;

    public IconListAsyncProvider(Context context, Listener<IconListAdapter> listener) {
        super(context, listener, false);
        this.adapter = new IconListAdapter(context);
    }

    @Override
    protected IconListAdapter run(Updater updater) {
        adapter.resolve(updater);
        return this.adapter;
    }
}
