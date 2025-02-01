package android.content.om;

import androidx.annotation.NonNull;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(OverlayManagerTransaction.class)
public class OverlayManagerTransactionHidden {
    public static final class Builder {
        public Builder setEnabled(@NonNull OverlayIdentifier overlay, boolean enable) { return null; }

        @NonNull
        public OverlayManagerTransactionHidden build() {
            return null;
        }
    }
}
