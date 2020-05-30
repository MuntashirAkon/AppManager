// This is a modified version of IconListAdapter.java taken
// from https://github.com/butzist/ActivityLauncher/commit/dfb7fe271dae9379b5453bbb6e88f30a1adc94a9
// and was authored by Adam M. Szalkowski with ISC License.
// All derivative works are licensed under GPLv3.0.

package io.github.muntashirakon.AppManager;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

public abstract class AsyncProvider<ReturnType> extends AsyncTask<Void, Integer, ReturnType> {
    private CharSequence message;
    private Listener<ReturnType> listener;
    private int max;
    private ProgressDialog progress;
    AsyncProvider(Context context, Listener<ReturnType> listener, boolean showProgressDialog) {
        this.message = context.getText(R.string.loading);
        this.listener = listener;

        if (showProgressDialog) {
            this.progress = new ProgressDialog(context);
        } else {
            progress = null;
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (this.progress != null && values.length > 0) {
            int value = values[0];

            if (value == 0) {
                this.progress.setIndeterminate(false);
                this.progress.setMax(this.max);
            }

            this.progress.setProgress(value);
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        if (this.progress != null) {
            this.progress.setCancelable(false);
            this.progress.setMessage(this.message);
            this.progress.setIndeterminate(true);
            this.progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            this.progress.show();
        }
    }

    @Override
    protected void onPostExecute(ReturnType result) {
        super.onPostExecute(result);
        if (this.listener != null) {
            this.listener.onProviderFinished(this, result);
        }

        if (this.progress != null) {
            try {
                this.progress.dismiss();
            }
            catch (IllegalArgumentException e) { /* ignore */ }
        }
    }

    abstract protected ReturnType run(Updater updater);

    @Override
    protected ReturnType doInBackground(Void... params) {
        return run(new Updater(this));
    }

    public interface Listener<ReturnType> {
        void onProviderFinished(AsyncProvider<ReturnType> task, ReturnType value);
    }

    class Updater {
        private AsyncProvider<ReturnType> provider;

        Updater(AsyncProvider<ReturnType> provider) {
            this.provider = provider;
        }

        void update(int value) {
            this.provider.publishProgress(value);
        }

        void updateMax(int value) {
            this.provider.max = value;
        }
    }
}
