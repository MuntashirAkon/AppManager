package io.github.muntashirakon.AppManager.appops;

import android.annotation.SuppressLint;

import com.jaredrummler.android.shell.CommandResult;
import com.jaredrummler.android.shell.Shell;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressLint("DefaultLocale")
public
class AppOpsService implements IAppOpsService {
    private static final Pattern OP_MATCHER = Pattern.compile("(?:Uid mode: )?(\\w+): (\\w+)" +
            "(?:; time=(?:\\s*0|([+\\-])(\\d+d)?(\\d{1,2}h)?(\\d{1,2}m)?(\\d{1,2}s)?(\\d{1,3}m))s ago)?" +
            "(?:; rejectTime=(?:\\s*0|([+\\-])(\\d+d)?(\\d{1,2}h)?(\\d{1,2}m)?(\\d{1,2}s)?(\\d{1,3}m))s ago)?" +
            "( \\(running\\))?(?:; duration=(?:\\s*0|([+\\-])(\\d+d)?(\\d{1,2}h)?(\\d{1,2}m)?(\\d{1,2}s)?(\\d{1,3}m))s)?");

    private static final long[] TIME  = new long[]{
            86400000,  // DAY
            3600000,  // HOUR
            60000,  // MINUTE
            1000,  // SECOND
            1  // MILLISECOND
    };

    private static final int DEFAULT_MODE_SKIP = 14;

    private boolean isSuccessful = false;
    private List<String> output = null;

    /**
     * Get the mode of operation of the given package or uid.
     *
     * NOTE: This is different from the original implementation where both uid and package name have
     * to be given. Here you can specify only one of them.
     * @param op One of the OP_*
     * @param uid User ID for the package(s)
     * @param packageName Name of the package
     * @return One of the MODE_*
     * @throws Exception Exception is thrown if neither uid nor package name is supplied or it is
     * an invalid operation name or mode name or there's an error parsing the output
     */
    @Override
    @AppOpsManager.Mode
    public int checkOperation(int op, int uid, @Nullable String packageName)
            throws Exception {
        String opStr = AppOpsManager.opToName(op);
        if (uid >= 0)
            runCommand(String.format("appops get %d %s", uid, opStr));
        else if (packageName != null)
            runCommand(String.format("appops get %s %s", packageName, opStr));
        else throw new Exception("No uid or package name provided");
        if (isSuccessful) {
            try {
                String opModeOut;
                if (output.size() == 1) {
                    AppOpsManager.OpEntry entry = parseOpName(output.get(0));
                    return entry.getMode();
                } else if (output.size() == 2) {
                    opModeOut = output.get(1).substring(DEFAULT_MODE_SKIP);
                    return strModeToMode(opModeOut);
                }
            } catch (IndexOutOfBoundsException e) {
                throw new Exception("Invalid output from appops");
            }
        }
        throw new Exception("Failed to check operation " + opStr);
    }

    @Override
    public List<AppOpsManager.PackageOps> getOpsForPackage(int uid, String packageName, int[] ops)
            throws Exception {
        List<AppOpsManager.PackageOps> packageOpsList = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        if (packageName == null) throw new Exception("No uid and package name provided");
        if (ops == null) {
            runCommand(String.format("appops get %s", packageName));
            lines = output;
            if (!isSuccessful) throw new Exception("Failed to get operations for package " + packageName);
        } else {
            for(int op: ops) {
                runCommand(String.format("appops get %s %s", packageName, AppOpsManager.opToName(op)));
                if (output.size() == 1) {  // Trivial parser
                    lines.addAll(output);
                } else if (output.size() == 2) {  // Custom parser
                    String name = String.format("%s: %s", AppOpsManager.opToName(op), output.get(1).substring(DEFAULT_MODE_SKIP));
                    lines.add(name);
                }
                if (!isSuccessful) throw new Exception("Failed to get operations for package " + packageName);
            }
        }
        List<AppOpsManager.OpEntry> opEntries = new ArrayList<>();
        for(String line: lines) {
            opEntries.add(parseOpName(line));
        }
        AppOpsManager.PackageOps packageOps = new AppOpsManager.PackageOps(packageName, uid, opEntries);
        packageOpsList.add(packageOps);
        return packageOpsList;
    }

    @Override
    public void setMode(int op, int uid, String packageName, int mode) throws Exception {
        String opStr = AppOpsManager.opToName(op);
        String modeStr = AppOpsManager.modeToName(mode);
        if (uid >= 0)
            runCommand(String.format("appops set --uid %d %s %s", uid, opStr, modeStr));
        else if (packageName != null)
            runCommand(String.format("appops set %s %s %s", packageName, opStr, modeStr));
        else throw new Exception("No uid or package name provided");
        if (isSuccessful) { return; }
        throw new Exception("Failed to check operation " + opStr);
    }

    @Override
    public void resetAllModes(int reqUserId, @NonNull String reqPackageName) throws Exception {
        if (reqUserId < 0)
            runCommand(String.format("appops reset %s", reqPackageName));
        else
            runCommand(String.format("appops reset --user %d %s", reqUserId, reqPackageName));
        if (!isSuccessful) throw new Exception("Error resetting all modes for package " + reqPackageName);
    }

    /**
     * Run the given command and save results to {@link #isSuccessful} and {@link #output}
     * @param command The command to run
     */
    private void runCommand(String command) {
        CommandResult commandResult = Shell.SU.run(command);
        isSuccessful = commandResult.isSuccessful();
        output = commandResult.stdout;
    }

    /**
     * Mode names to mode values
     * @param modeStr Mode name, eg. allow
     * @return Integer value of the mode
     */
    @SuppressLint("WrongConstant")
    @AppOpsManager.Mode
    private static int strModeToMode(String modeStr) throws Exception {
        for (int i = AppOpsManager.MODE_NAMES.length - 1; i >= 0; i--) {
            if (AppOpsManager.MODE_NAMES[i].equals(modeStr)) {
                return i;
            }
        }
        try {
            return Integer.parseInt(modeStr);
        } catch (NumberFormatException ignored) {}
        throw new Exception("Invalid mode " + modeStr);
    }

    /**
     * String value of op to integer value of op
     *
     * @param op String value of op, eg. android:coarse_location
     * @return Integer value of op
     */
    static private int strOpToOp(String op) throws Exception {
        try {
            return AppOpsManager.strOpToOp(op);
        } catch (IllegalArgumentException ignored) {}
        try {
            return Integer.parseInt(op);
        } catch (NumberFormatException ignored) {}
        try {
            return AppOpsManager.strDebugOpToOp(op);
        } catch (IllegalArgumentException e) {
            throw new Exception("Invalid op " + op);
        }
    }

    private static AppOpsManager.OpEntry parseOpName(String line) throws Exception {
        Matcher matcher = OP_MATCHER.matcher(line);
        if (matcher.find()) {
            if (matcher.group(1) == null && matcher.group(2) == null)
                throw new Exception("Op name or mode cannot be empty");
            int op = strOpToOp(matcher.group(1));
            @AppOpsManager.Mode int mode = strModeToMode(matcher.group(2));
            boolean running = matcher.group(15) != null;
            long accessTime = getTime(matcher, 3);
            long rejectTime = getTime(matcher, 9);
            long duration = getTime(matcher, 16);
            return new AppOpsManager.OpEntry(op, running, mode, accessTime, rejectTime, duration, null, null);
        }
        throw new Exception("Failed to parse line");
    }

    private static long getTime(@NonNull Matcher matcher, int start) {
        long time = 1;
        String sign = matcher.group(start);
        if (sign == null) return 0;
        String tmp;
        for(int i = 0; i<5; ++i) {
            tmp = removeLastChar(matcher.group(start+i));
            if (tmp != null && !tmp.equals("")) time += Integer.parseInt(tmp) * TIME[i];
        }
        return sign.equals("-") ? -time : time;
    }

    private static String removeLastChar(@Nullable String str) {
        return str != null ? str.substring(0, str.length() - 1) : null;
    }
}
