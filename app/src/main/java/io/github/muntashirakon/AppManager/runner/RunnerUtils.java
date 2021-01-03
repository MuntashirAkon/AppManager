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

package io.github.muntashirakon.AppManager.runner;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.tananaev.adblib.AdbConnection;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.adb.AdbShell;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.servermanager.ApiSupporter;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.utils.AppPref;

@SuppressWarnings("UnusedReturnValue")
public final class RunnerUtils {
    public static final int USER_ALL = -1;

    public static final String CMD_PM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? "cmd package" : "pm";
    public static final String CMD_AM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? "cmd activity" : "am";

    public static final String CMD_CLEAR_CACHE_PREFIX = "rm -rf";
    public static final String CMD_CLEAR_CACHE_DIR_SUFFIX = " %s/cache %s/code_cache";
    public static final String CMD_CLEAR_PACKAGE_DATA = CMD_PM + " clear --user %s %s";
    public static final String CMD_ENABLE_PACKAGE = CMD_PM + " enable --user %s %s";
    public static final String CMD_DISABLE_PACKAGE = CMD_PM + " disable-user --user %s %s";
    public static final String CMD_FORCE_STOP_PACKAGE = CMD_AM + " force-stop --user %s %s";
    public static final String CMD_INSTALL_EXISTING_PACKAGE = CMD_PM + " install-existing --user %s %s";
    public static final String CMD_UNINSTALL_PACKAGE = CMD_PM + " uninstall -k --user %s %s";
    public static final String CMD_UNINSTALL_PACKAGE_WITH_DATA = CMD_PM + " uninstall --user %s %s";

    public static final String CMD_PERMISSION_GRANT = CMD_PM + " grant --user %s %s %s";
    public static final String CMD_PERMISSION_REVOKE = CMD_PM + " revoke --user %s %s %s";

    public static final String CMD_PID_PACKAGE = "pidof %s";
    public static final String CMD_KILL_SIG9 = "kill -9 %s";

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
    public static Runner.Result clearPackageCache(String packageName, int userHandle) {
        try {
            ApplicationInfo applicationInfo = ApiSupporter.getApplicationInfo(packageName, 0, userHandle);
            StringBuilder command = new StringBuilder(CMD_CLEAR_CACHE_PREFIX);
            command.append(String.format(CMD_CLEAR_CACHE_DIR_SUFFIX, applicationInfo.dataDir, applicationInfo.dataDir));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !applicationInfo.dataDir.equals(applicationInfo.deviceProtectedDataDir)) {
                command.append(String.format(CMD_CLEAR_CACHE_DIR_SUFFIX, applicationInfo.deviceProtectedDataDir, applicationInfo.deviceProtectedDataDir));
            }
            File[] cacheDirs = AppManager.getInstance().getExternalCacheDirs();
            int currentUserHandle = Users.getCurrentUserHandle();
            for (File cacheDir : cacheDirs) {
                if (cacheDir != null) {
                    String extCache = cacheDir.getAbsolutePath().replace(BuildConfig.APPLICATION_ID,
                            packageName).replace(String.valueOf(currentUserHandle), String.valueOf(userHandle));
                    command.append(" ").append(extCache);
                }
            }
            return Runner.runCommand(command.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return new Runner.Result();
        }
    }

    @NonNull
    public static Runner.Result clearPackageData(String packageName, int userHandle) {
        return Runner.runCommand(String.format(CMD_CLEAR_PACKAGE_DATA, userHandleToUser(userHandle), packageName));
    }

    @NonNull
    public static Runner.Result enablePackage(String packageName, int userHandle) {
        return Runner.runCommand(String.format(CMD_ENABLE_PACKAGE, userHandleToUser(userHandle), packageName));
    }

    @NonNull
    public static Runner.Result disablePackage(String packageName, int userHandle) {
        return Runner.runCommand(String.format(CMD_DISABLE_PACKAGE, userHandleToUser(userHandle), packageName));
    }

    @NonNull
    public static Runner.Result forceStopPackage(String packageName, int userHandle) {
        return Runner.runCommand(String.format(CMD_FORCE_STOP_PACKAGE, userHandleToUser(userHandle), packageName));
    }

    @NonNull
    public static Runner.Result uninstallPackageUpdate(String packageName, int userHandle, boolean keepData) {
        String cmd = String.format(keepData ? CMD_UNINSTALL_PACKAGE : CMD_UNINSTALL_PACKAGE_WITH_DATA, userHandleToUser(USER_ALL), packageName) + " && "
                + String.format(CMD_INSTALL_EXISTING_PACKAGE, userHandleToUser(userHandle), packageName);
        return Runner.runCommand(cmd);
    }

    @NonNull
    public static Runner.Result uninstallPackageWithoutData(String packageName, int userHandle) {
        return Runner.runCommand(String.format(CMD_UNINSTALL_PACKAGE, userHandleToUser(userHandle), packageName));
    }

    @NonNull
    public static Runner.Result uninstallPackageWithData(String packageName, int userHandle) {
        return Runner.runCommand(String.format(CMD_UNINSTALL_PACKAGE_WITH_DATA, userHandleToUser(userHandle), packageName));
    }

    @NonNull
    public static Runner.Result grantPermission(String packageName, String permissionName, int userHandle) {
        return Runner.runCommand(String.format(CMD_PERMISSION_GRANT, userHandleToUser(userHandle), packageName, permissionName));
    }

    @NonNull
    public static Runner.Result revokePermission(String packageName, String permissionName, int userHandle) {
        return Runner.runCommand(String.format(CMD_PERMISSION_REVOKE, userHandleToUser(userHandle), packageName, permissionName));
    }

    public static boolean fileExists(@NonNull String fileName) {
        return Runner.runCommand(new String[]{Runner.TOYBOX, "test", "-e", fileName}).isSuccessful();
    }

    public static boolean fileExists(@NonNull File fileName) {
        return Runner.runCommand(new String[]{Runner.TOYBOX, "test", "-e", fileName.getAbsolutePath()}).isSuccessful();
    }

    public static boolean isDirectory(@NonNull String fileName) {
        return Runner.runCommand(new String[]{Runner.TOYBOX, "test", "-d", fileName}).isSuccessful();
    }

    public static boolean isFile(@NonNull String fileName) {
        return Runner.runCommand(new String[]{Runner.TOYBOX, "test", "-f", fileName}).isSuccessful();
    }

    public static boolean mkdir(@NonNull String fileName) {
        return Runner.runCommand(new String[]{Runner.TOYBOX, "mkdir", fileName}).isSuccessful();
    }

    public static boolean mkdirs(@NonNull String fileName) {
        return Runner.runCommand(new String[]{Runner.TOYBOX, "mkdir", "-p", fileName}).isSuccessful();
    }

    public static void deleteFile(@NonNull String fileName, boolean isForce) {
        String forceSwitch = isForce ? "-rf" : "-f";
        Runner.runCommand(new String[]{Runner.TOYBOX, "rm", forceSwitch, fileName});
    }

    public static void deleteFile(@NonNull File fileName, boolean isForce) {
        deleteFile(fileName.getAbsolutePath(), isForce);
    }

    public static boolean mv(@NonNull File source, @NonNull File dest) {
        return Runner.runCommand(new String[]{Runner.TOYBOX, "mv", "-f", source.getAbsolutePath(), dest.getAbsolutePath()}).isSuccessful();
    }

    public static boolean cp(@NonNull File source, @NonNull File dest) {
        return Runner.runCommand(new String[]{Runner.TOYBOX, "cp", "-a", source.getAbsolutePath(), dest.getAbsolutePath()}).isSuccessful();
    }

    public static String cat(@NonNull String fileName, String emptyValue) {
        Runner.Result result = Runner.runCommand(String.format(Runner.TOYBOX + " cat \"%s\" 2> /dev/null", fileName));
        return result.isSuccessful() ? result.getOutput() : emptyValue;
    }

    @NonNull
    public static String userHandleToUser(int userHandle) {
        if (userHandle == USER_ALL) return "all";
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

    private static boolean isAdbAvailable(Context context) {
        try (AdbConnection ignored = AdbConnectionManager.connect(context, ServerConfig.getAdbHost(), ServerConfig.getAdbPort())) {
            return true;
        } catch (IOException | NoSuchAlgorithmException | InterruptedException e) {
            return false;
        }
    }

    @WorkerThread
    private static void autoDetectRootOrAdb(Context context) {
        // Update config
        LocalServer.updateConfig();
        // Check root, ADB and load am_local_server
        if (!RunnerUtils.isRootGiven()) {
            AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
            // Check for adb
            new Thread(() -> {
                if (RunnerUtils.isAdbAvailable(context)) {
                    AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, true);
                }
                try {
                    AdbShell.CommandResult result = AdbShell.run("echo AMAdbCheck");
                    if (!result.isSuccessful() || !"AMAdbCheck".equals(result.getStdout())) {
                        throw new IOException("Adb not available");
                    }
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, "Working on ADB mode", Toast.LENGTH_SHORT).show());
                } catch (IOException e) {
                    AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, false);
                }
            }).start();
        } else {
            AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, true);
            new Thread(LocalServer::getInstance).start();
        }
    }

    @WorkerThread
    public static void setModeOfOps(Context context) {
        String mode = (String) AppPref.get(AppPref.PrefKey.PREF_MODE_OF_OPS_STR);
        switch (mode) {
            case Runner.MODE_AUTO:
                if (LocalServer.isAMServiceAlive()) {
                    // Don't bother detecting root/ADB
                    return;
                } else {
                    // AMService isn't running, check for root/ADB
                    RunnerUtils.autoDetectRootOrAdb(context);
                }
                return;
            case Runner.MODE_ROOT:
                AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, true);
                AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, false);
                LocalServer.getInstance();
                break;
            case Runner.MODE_ADB:
                AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
                AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, true);
                LocalServer.getInstance();
                break;
            case Runner.MODE_NO_ROOT:
                AppPref.set(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
                AppPref.set(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, false);
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
