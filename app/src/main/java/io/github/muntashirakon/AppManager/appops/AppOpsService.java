package io.github.muntashirakon.AppManager.appops;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.runner.Runner;

@SuppressLint("DefaultLocale")
public
class AppOpsService implements IAppOpsService {
    private static final Pattern OP_MATCHER = Pattern.compile("(?:Uid mode: )?([\\w()]+): ([\\w=]+)" +
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
    private static final int UNKNOWN_MODE_SKIP = 5;
    private static final int UNKNOWN_OP_SKIP = 8;
    private static final int OP_PREFIX_OP_SKIP = 3;

    private boolean isSuccessful = false;
    private List<String> output = null;
    private Context context;
    public AppOpsService(Context context) {
        this.context = context;
    }

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
    public String checkOperation(int op, int uid, @Nullable String packageName)
            throws Exception {
        String opStr = AppOpsManager.opToName(op);
        if (packageName != null)
            runCommand(String.format("appops get %s %d", packageName, op));
        else if (uid >= 0)
            runCommand(String.format("appops get %d %d", uid, op));
        else throw new Exception("No uid or package name provided");
        if (isSuccessful) {
            try {
                String opModeOut;
                if (output.size() == 1) {
                    return parseOpName(output.get(0)).getMode();
                } else if (output.size() == 2) {
                    String line2 = output.get(1);
                    if (line2.startsWith("Default mode:")) {
                        opModeOut = line2.substring(DEFAULT_MODE_SKIP);
                        return opModeOut;
                    } else return parseOpName(line2).getMode();
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
                runCommand(String.format("appops get %s %d", packageName, op));
                if (output.size() == 1) {  // Trivial parser
                    lines.addAll(output);
                } else if (output.size() == 2) {  // Custom parser
                    String line2 = output.get(1);
                    if (line2.startsWith("Default mode:")) {
                        String name = String.format("%s: %s", AppOpsManager.opToName(op), line2.substring(DEFAULT_MODE_SKIP));
                        lines.add(name);
                    } else lines.add(line2);  // To prevent weird bug in some cases
                }
//                if (!isSuccessful) throw new Exception("Failed to get operations for package " + packageName);
            }
        }
        List<AppOpsManager.OpEntry> opEntries = new ArrayList<>();
        for(String line: lines) {
            try {
                opEntries.add(parseOpName(line));
            } catch (Exception ignored) {}
        }
        AppOpsManager.PackageOps packageOps = new AppOpsManager.PackageOps(packageName, uid, opEntries);
        packageOpsList.add(packageOps);
        return packageOpsList;
    }

    @Override
    public void setMode(int op, int uid, String packageName, int mode) throws Exception {
        String modeStr = AppOpsManager.modeToName(mode);
        if (uid >= 0)
            runCommand(String.format("appops set --uid %d %d %s", uid, op, modeStr));
        else if (packageName != null)
            runCommand(String.format("appops set %s %d %s", packageName, op, modeStr));
        else throw new Exception("No uid or package name provided");
        if (isSuccessful) { return; }
        throw new Exception("Failed to check operation " + op);
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
        Runner.Result result = Runner.run(context, command);
        isSuccessful = result.isSuccessful();
        output = result.getOutputAsList();
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

    @NonNull
    private static AppOpsManager.OpEntry parseOpName(@NonNull String line) throws Exception {
        Matcher matcher = OP_MATCHER.matcher(line);
        if (matcher.find()) {
            String opStr = matcher.group(1);
            String modeStr = matcher.group(2);
            if (opStr == null || modeStr == null)
                throw new Exception("Op name or mode cannot be empty");
            // Handle Unknown(op)
            if (opStr.startsWith("Unknown("))
                opStr = opStr.substring(UNKNOWN_OP_SKIP, opStr.length()-1);
            final String finalOpStr = opStr; // Save the op str before modifying it
            if (opStr.startsWith("OP_"))
                opStr = opStr.substring(OP_PREFIX_OP_SKIP);
            // FIXME: Check old opStr as well
            // Handle mode=5
            if (modeStr.startsWith("mode="))
                modeStr = modeStr.substring(UNKNOWN_MODE_SKIP);
            int op = AppOpsManager.OP_NONE;
            try {
                op = strOpToOp(opStr);
            } catch (Exception ignore) {}
            boolean running = matcher.group(15) != null;
            long accessTime = getTime(matcher, 3);
            long rejectTime = getTime(matcher, 9);
            long duration = getTime(matcher, 16);
            return new AppOpsManager.OpEntry(op, finalOpStr, running, modeStr, accessTime, rejectTime, duration, null, null);
        }
        throw new Exception("Failed to parse line");
    }

    private static long getTime(@NonNull Matcher matcher, int start) {
        long time = 0;
        String sign = matcher.group(start);
        if (sign == null) return 0;
        String tmp;
        for(int i = 0; i<5; ++i) {
            tmp = removeLastChar(matcher.group(start+i+1));
            if (!TextUtils.isEmpty(tmp)) {
                //noinspection ConstantConditions
                time += Long.parseLong(tmp) * TIME[i];
            }
        }
        return sign.equals("-") ? -time : time;
    }

    private static String removeLastChar(@Nullable String str) {
        return str != null ? str.substring(0, str.length() - 1) : null;
    }
}
