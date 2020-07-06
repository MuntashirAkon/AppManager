package io.github.muntashirakon.AppManager;

import android.app.Application;
import android.content.Context;

public class AppManager extends Application {
    private static AppManager instance;

    public static AppManager getInstance() {
        return instance;
    }

    public static Context getContext(){
        return instance;
    }

    @Override
    public void onCreate() {
        instance = this;
        super.onCreate();
    }
}
