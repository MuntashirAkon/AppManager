package android.view;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(Window.class)
public class WindowHidden {
    public void addSystemFlags(int flags) {
        HiddenUtil.throwUOE(flags);
    }
}
