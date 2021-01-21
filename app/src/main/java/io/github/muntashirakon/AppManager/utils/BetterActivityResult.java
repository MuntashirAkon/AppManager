/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.utils;

import android.content.Intent;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BetterActivityResult<Input, Result> {
    @NonNull
    public static <Input, Result> BetterActivityResult<Input, Result> registerForActivityResult(
            @NonNull ActivityResultCaller caller,
            @NonNull ActivityResultContract<Input, Result> contract,
            @Nullable OnActivityResult<Result> onActivityResult) {
        return new BetterActivityResult<>(caller, contract, onActivityResult);
    }

    @NonNull
    public static <Input, Result> BetterActivityResult<Input, Result> registerForActivityResult(
            @NonNull ActivityResultCaller caller,
            @NonNull ActivityResultContract<Input, Result> contract) {
        return new BetterActivityResult<>(caller, contract, null);
    }

    @NonNull
    public static BetterActivityResult<Intent, ActivityResult> registerActivityForResult(
            @NonNull ActivityResultCaller caller) {
        return new BetterActivityResult<>(caller, new ActivityResultContracts.StartActivityForResult(), null);
    }

    public interface OnActivityResult<O> {
        void onActivityResult(O result);
    }

    private final ActivityResultLauncher<Input> launcher;
    @Nullable
    private OnActivityResult<Result> onActivityResult;

    private BetterActivityResult(@NonNull ActivityResultCaller caller,
                                 @NonNull ActivityResultContract<Input, Result> contract,
                                 @Nullable OnActivityResult<Result> onActivityResult) {
        this.onActivityResult = onActivityResult;
        this.launcher = caller.registerForActivityResult(contract, this::callOnActivityResult);
    }

    public void setOnActivityResult(@Nullable OnActivityResult<Result> onActivityResult) {
        this.onActivityResult = onActivityResult;
    }

    public void launch(Input input, @Nullable OnActivityResult<Result> onActivityResult) {
        if (onActivityResult != null) {
            this.onActivityResult = onActivityResult;
        }
        launcher.launch(input);
    }

    public void launch(Input input) {
        launch(input, null);
    }

    private void callOnActivityResult(Result result) {
        if (onActivityResult != null) onActivityResult.onActivityResult(result);
    }
}
