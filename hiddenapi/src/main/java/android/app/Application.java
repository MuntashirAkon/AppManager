package android.app;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;

public class Application extends ContextWrapper implements ComponentCallbacks2 {
    public Application(Context base) {
        super(base);
    }
}
