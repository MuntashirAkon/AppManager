// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IThemeSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;

public final class Languages {
    @NonNull
    public static Language getLanguage(@NonNull Context context, @NonNull String language, @Nullable IThemeSource themeSource) {
        try {
            IGrammarSource grammarSource = IGrammarSource.fromInputStream(context.getAssets().open("languages/" + language + "/tmLanguage.json"), "tmLanguage.json", StandardCharsets.UTF_8);
            Reader languageConfiguration = new InputStreamReader(context.getAssets().open("languages/" + language + "/language-configuration.json"));
            if (themeSource == null) {
                throw new FileNotFoundException("Invalid theme source");
            }
            return TextMateLanguage.create(grammarSource, languageConfiguration, themeSource);
        } catch (IOException e) {
            Log.w("CodeEditor", "Could not load resources for language %s", e, language);
            return new EmptyLanguage();
        }
    }
}
