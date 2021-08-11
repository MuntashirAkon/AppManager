// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.security.InvalidParameterException;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.settings.MainPreferences;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;

@SuppressWarnings("UnusedReturnValue")
public final class RunnerUtils {
    public static final String CMD_PM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? "cmd package" : "pm";

    public static final String CMD_INSTALL_EXISTING_PACKAGE = CMD_PM + " install-existing --user %s %s";
    public static final String CMD_UNINSTALL_PACKAGE = CMD_PM + " uninstall -k --user %s %s";
    public static final String CMD_UNINSTALL_PACKAGE_WITH_DATA = CMD_PM + " uninstall --user %s %s";

    private static final String EMPTY = "";

    /**
     * Translator object for escaping Shell command language.
     *
     * @see <a href="http://pubs.opengroup.org/onlinepubs/7908799/xcu/chap2.html">Shell Command Language</a>
     */
    public static final LookupTranslator ESCAPE_XSI;

    static {
        final Map<CharSequence, CharSequence> escapeXsiMap = new HashMap<>();
        escapeXsiMap.put("|", "\\|");
        escapeXsiMap.put("&", "\\&");
        escapeXsiMap.put(";", "\\;");
        escapeXsiMap.put("<", "\\<");
        escapeXsiMap.put(">", "\\>");
        escapeXsiMap.put("(", "\\(");
        escapeXsiMap.put(")", "\\)");
        escapeXsiMap.put("$", "\\$");
        escapeXsiMap.put("`", "\\`");
        escapeXsiMap.put("\\", "\\\\");
        escapeXsiMap.put("\"", "\\\"");
        escapeXsiMap.put("'", "\\'");
        escapeXsiMap.put(" ", "\\ ");
        escapeXsiMap.put("\t", "\\\t");
        escapeXsiMap.put("\r\n", EMPTY);
        escapeXsiMap.put("\n", EMPTY);
        escapeXsiMap.put("*", "\\*");
        escapeXsiMap.put("?", "\\?");
        escapeXsiMap.put("[", "\\[");
        escapeXsiMap.put("#", "\\#");
        escapeXsiMap.put("~", "\\~");
        escapeXsiMap.put("=", "\\=");
        escapeXsiMap.put("%", "\\%");
        ESCAPE_XSI = new LookupTranslator(Collections.unmodifiableMap(escapeXsiMap));
    }

    /**
     * <p>Escapes the characters in a {@code String} using XSI rules.</p>
     *
     * <p><b>Beware!</b> In most cases you don't want to escape shell commands but use multi-argument
     * methods provided by {@link java.lang.ProcessBuilder} or {@link java.lang.Runtime#exec(String[])}
     * instead.</p>
     *
     * <p>Example:</p>
     * <pre>
     * input string: He didn't say, "Stop!"
     * output string: He\ didn\'t\ say,\ \"Stop!\"
     * </pre>
     *
     * @param input String to escape values in, may be null
     * @return String with escaped values, {@code null} if null string input
     * @see <a href="http://pubs.opengroup.org/onlinepubs/7908799/xcu/chap2.html">Shell Command Language</a>
     */
    public static String escape(final String input) {
        return ESCAPE_XSI.translate(input);
    }

    @NonNull
    public static Runner.Result uninstallPackageUpdate(String packageName, int userHandle, boolean keepData) {
        String cmd = String.format(keepData ? CMD_UNINSTALL_PACKAGE : CMD_UNINSTALL_PACKAGE_WITH_DATA, userHandleToUser(Users.USER_ALL), packageName) + " && "
                + String.format(CMD_INSTALL_EXISTING_PACKAGE, userHandleToUser(userHandle), packageName);
        return Runner.runCommand(cmd);
    }

    @NonNull
    public static String userHandleToUser(int userHandle) {
        if (userHandle == Users.USER_ALL) return "all";
        else return String.valueOf(userHandle);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isRootGiven() {
        if (isRootAvailable()) {
            String output = Runner.runCommand(Runner.getRootInstance(), "echo AMRootTest").getOutput();
            return output.contains("AMRootTest");
        }
        return false;
    }

    public static boolean isRootAvailable() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String pathDir : pathEnv.split(":")) {
                try {
                    if (new File(pathDir, "su").exists()) {
                        return true;
                    }
                } catch (NullPointerException ignore) {
                }
            }
        }
        return false;
    }

    @WorkerThread
    private static void autoDetectRootOrAdb() {
        // Update config
        LocalServer.updateConfig();
        // Check root, ADB and load am_local_server
        if (!RunnerUtils.isRootGiven()) {
            AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
            // Check for adb
            if (LocalServer.isAdbAvailable()) {
                Log.e("ADB", "ADB available");
                AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, true);
            }
            try {
                LocalServer.restart();
                if (!LocalServer.isAMServiceAlive()) {
                    throw new IOException("ADB not available");
                }
                UiThreadHandler.run(() -> UIUtils.displayShortToast(R.string.working_on_adb_mode));
            } catch (IOException | RemoteException e) {
                Log.e("ADB", e);
                AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, false);
            }
        } else {  // Root is available
            AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, true);
            try {
                LocalServer.restart();
            } catch (RemoteException | IOException e) {
                Log.e("ROOT", e);
                AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
            }
        }
    }

    @WorkerThread
    public static void setModeOfOps(FragmentActivity activity, boolean force) {
        String mode = AppPref.getString(AppPref.PrefKey.PREF_MODE_OF_OPS_STR);
        try {
            if (!force) {
                if (LocalServer.isAMServiceAlive()) {
                    // Don't bother detecting root/ADB
                    return;
                } else if (!Runner.MODE_NO_ROOT.equals(mode) && LocalServer.isLocalServerAlive()) {
                    // Remote server is running
                    LocalServer.getInstance();
                    return;
                }
            }
            switch (mode) {
                case Runner.MODE_AUTO:
                    RunnerUtils.autoDetectRootOrAdb();
                    return;
                case Runner.MODE_ROOT:
                    if (!isRootAvailable()) {
                        throw new Exception("Root not available.");
                    }
                    AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, true);
                    AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, false);
                    LocalServer.launchAmService();
                    return;
                case Runner.MODE_ADB_WIFI:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
                        AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, true);
                        CountDownLatch waitForConfig = new CountDownLatch(1);
                        UiThreadHandler.run(() -> MainPreferences.configureWirelessDebugging(activity, waitForConfig));
                        waitForConfig.await(2, TimeUnit.MINUTES);
                        LocalServer.restart();
                        return;
                    } // else fallback to ADB over TCP
                case "adb":
                    if (mode.equals("adb")) {
                        // Backward compatibility for v2.6.0
                        AppPref.set(AppPref.PrefKey.PREF_MODE_OF_OPS_STR, Runner.MODE_ADB_OVER_TCP);
                    }
                    // fallback to ADB over TCP
                case Runner.MODE_ADB_OVER_TCP:
                    // Port is always 5555
                    AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
                    AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, true);
                    ServerConfig.setAdbPort(ServerConfig.DEFAULT_ADB_PORT);
                    LocalServer.updateConfig();
                    LocalServer.restart();
                    return;
                case Runner.MODE_NO_ROOT:
                    AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
                    AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, false);
            }
        } catch (Exception e) {
            Log.e("ModeOfOps", e);
            CountDownLatch waitForInteraction = new CountDownLatch(1);
            AtomicReference<AlertDialog> alertDialog = new AtomicReference<>();
            UiThreadHandler.run(() -> alertDialog.set(new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.fallback_to_no_root_mode)
                    .setMessage(R.string.fallback_to_no_root_mode_description)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
                        AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, false);
                        waitForInteraction.countDown();
                    })
                    .setNegativeButton(R.string.no, (dialog, which) -> waitForInteraction.countDown())
                    .setCancelable(false)
                    .show()));
            try {
                waitForInteraction.await(2, TimeUnit.MINUTES);
                if (waitForInteraction.getCount() == 1) {
                    // Closed due to timeout: fallback to no-root by default
                    AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
                    AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, false);
                    UiThreadHandler.run(() -> {
                        if (alertDialog.get() != null) alertDialog.get().dismiss();
                    });
                }
            } catch (InterruptedException ignore) {
            }
        }
    }

    /**
     * An API for translating text.
     * Its core use is to escape and unescape text. Because escaping and unescaping
     * is completely contextual, the API does not present two separate signatures.
     *
     * @since 1.0
     */
    public static class LookupTranslator {
        /**
         * The mapping to be used in translation.
         */
        private final Map<String, String> lookupMap;
        /**
         * The first character of each key in the lookupMap.
         */
        private final BitSet prefixSet;
        /**
         * The length of the shortest key in the lookupMap.
         */
        private final int shortest;
        /**
         * The length of the longest key in the lookupMap.
         */
        private final int longest;

        /**
         * Define the lookup table to be used in translation
         * <p>
         * Note that, as of Lang 3.1 (the origin of this code), the key to the lookup
         * table is converted to a java.lang.String. This is because we need the key
         * to support hashCode and equals(Object), allowing it to be the key for a
         * HashMap. See LANG-882.
         *
         * @param lookupMap Map&lt;CharSequence, CharSequence&gt; table of translator
         *                  mappings
         */
        public LookupTranslator(final Map<CharSequence, CharSequence> lookupMap) {
            if (lookupMap == null) {
                throw new InvalidParameterException("lookupMap cannot be null");
            }
            this.lookupMap = new HashMap<>();
            this.prefixSet = new BitSet();
            int currentShortest = Integer.MAX_VALUE;
            int currentLongest = 0;

            for (final Map.Entry<CharSequence, CharSequence> pair : lookupMap.entrySet()) {
                this.lookupMap.put(pair.getKey().toString(), pair.getValue().toString());
                this.prefixSet.set(pair.getKey().charAt(0));
                final int sz = pair.getKey().length();
                if (sz < currentShortest) {
                    currentShortest = sz;
                }
                if (sz > currentLongest) {
                    currentLongest = sz;
                }
            }
            this.shortest = currentShortest;
            this.longest = currentLongest;
        }

        /**
         * Translate a set of codepoints, represented by an int index into a CharSequence,
         * into another set of codepoints. The number of codepoints consumed must be returned,
         * and the only IOExceptions thrown must be from interacting with the Writer so that
         * the top level API may reliably ignore StringWriter IOExceptions.
         *
         * @param input CharSequence that is being translated
         * @param index int representing the current point of translation
         * @param out   Writer to translate the text to
         * @return int count of codepoints consumed
         * @throws IOException if and only if the Writer produces an IOException
         */
        public int translate(@NonNull final CharSequence input, final int index, final Writer out) throws IOException {
            // check if translation exists for the input at position index
            if (prefixSet.get(input.charAt(index))) {
                int max = longest;
                if (index + longest > input.length()) {
                    max = input.length() - index;
                }
                // implement greedy algorithm by trying maximum match first
                for (int i = max; i >= shortest; i--) {
                    final CharSequence subSeq = input.subSequence(index, index + i);
                    final String result = lookupMap.get(subSeq.toString());

                    if (result != null) {
                        out.write(result);
                        return i;
                    }
                }
            }
            return 0;
        }

        /**
         * Helper for non-Writer usage.
         *
         * @param input CharSequence to be translated
         * @return String output of translation
         */
        public final String translate(final CharSequence input) {
            if (input == null) {
                return null;
            }
            try {
                final StringWriter writer = new StringWriter(input.length() * 2);
                translate(input, writer);
                return writer.toString();
            } catch (final IOException ioe) {
                // this should never ever happen while writing to a StringWriter
                throw new RuntimeException(ioe);
            }
        }

        /**
         * Translate an input onto a Writer. This is intentionally final as its algorithm is
         * tightly coupled with the abstract method of this class.
         *
         * @param input CharSequence that is being translated
         * @param out   Writer to translate the text to
         * @throws IOException if and only if the Writer produces an IOException
         */
        public final void translate(final CharSequence input, final Writer out) throws IOException {
            if (input == null) {
                return;
            }
            int pos = 0;
            final int len = input.length();
            while (pos < len) {
                final int consumed = translate(input, pos, out);
                if (consumed == 0) {
                    // inlined implementation of Character.toChars(Character.codePointAt(input, pos))
                    // avoids allocating temp char arrays and duplicate checks
                    final char c1 = input.charAt(pos);
                    out.write(c1);
                    pos++;
                    if (Character.isHighSurrogate(c1) && pos < len) {
                        final char c2 = input.charAt(pos);
                        if (Character.isLowSurrogate(c2)) {
                            out.write(c2);
                            pos++;
                        }
                    }
                    continue;
                }
                // contract with translators is that they have to understand codepoints
                // and they just took care of a surrogate pair
                for (int pt = 0; pt < consumed; pt++) {
                    pos += Character.charCount(Character.codePointAt(input, pos));
                }
            }
        }
    }
}
