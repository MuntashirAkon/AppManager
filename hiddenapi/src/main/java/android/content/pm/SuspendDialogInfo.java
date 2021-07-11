// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.Parcelable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

public final class SuspendDialogInfo implements Parcelable {
    /**
     * @return the resource id of the icon to be used with the dialog
     */
    @DrawableRes
    public int getIconResId() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the resource id of the title to be used with the dialog
     */
    @StringRes
    public int getTitleResId() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the resource id of the text to be shown in the dialog's body
     */
    @StringRes
    public int getDialogMessageResId() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the text to be shown in the dialog's body. Returns {@code null} if
     * {@link #getDialogMessageResId()} returns a valid resource id.
     */
    @Nullable
    public String getDialogMessage() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the text to be shown
     */
    @StringRes
    public int getNeutralButtonTextResId() {
        throw new UnsupportedOperationException();
    }

    SuspendDialogInfo(Builder b) {
        throw new UnsupportedOperationException();
    }

    /**
     * Builder to build a {@link SuspendDialogInfo} object.
     */
    public static final class Builder {
        /**
         * Set the resource id of the icon to be used. If not provided, no icon will be shown.
         *
         * @param resId The resource id of the icon.
         * @return this builder object.
         */
        @NonNull
        public Builder setIcon(@DrawableRes int resId) {
            throw new UnsupportedOperationException();
        }

        /**
         * Set the resource id of the title text to be displayed. If this is not provided, the
         * system will use a default title.
         *
         * @param resId The resource id of the title.
         * @return this builder object.
         */
        @NonNull
        public Builder setTitle(@StringRes int resId) {
            throw new UnsupportedOperationException();
        }

        /**
         * Set the text to show in the body of the dialog. Ignored if a resource id is set via
         * {@link #setMessage(int)}.
         * <p>
         * The system will use {@link String#format(Locale, String, Object...) String.format} to
         * insert the suspended app name into the message, so an example format string could be
         * {@code "The app %1$s is currently suspended"}. This is optional - if the string passed in
         * {@code message} does not accept an argument, it will be used as is.
         *
         * @param message The dialog message.
         * @return this builder object.
         * @see #setMessage(int)
         */
        @NonNull
        public Builder setMessage(@NonNull String message) {
            throw new UnsupportedOperationException();
        }

        /**
         * Set the resource id of the dialog message to be shown. If no dialog message is provided
         * via either this method or {@link #setMessage(String)}, the system will use a
         * default message.
         * <p>
         * The system will use {@link android.content.res.Resources#getString(int, Object...)
         * getString} to insert the suspended app name into the message, so an example format string
         * could be {@code "The app %1$s is currently suspended"}. This is optional - if the string
         * referred to by {@code resId} does not accept an argument, it will be used as is.
         *
         * @param resId The resource id of the dialog message.
         * @return this builder object.
         * @see #setMessage(String)
         */
        @NonNull
        public Builder setMessage(@StringRes int resId) {
            throw new UnsupportedOperationException();
        }

        /**
         * Set the resource id of text to be shown on the neutral button. Tapping this button starts
         * the {@link android.content.Intent#ACTION_SHOW_SUSPENDED_APP_DETAILS} activity. If this is
         * not provided, the system will use a default text.
         *
         * @param resId The resource id of the button text
         * @return this builder object.
         */
        @NonNull
        public Builder setNeutralButtonText(@StringRes int resId) {
            throw new UnsupportedOperationException();
        }

        /**
         * Build the final object based on given inputs.
         *
         * @return The {@link SuspendDialogInfo} object built using this builder.
         */
        @NonNull
        public SuspendDialogInfo build() {
            throw new UnsupportedOperationException();
        }
    }
}