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

package io.github.muntashirakon.AppManager.runningapps;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.ApiSupporter;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.Utils;

@WorkerThread
final class ProcessParser {
    // "^(?<label>[^\\t\\s]+)[\\t\\s]+(?<pid>\\d+)[\\t\\s]+(?<ppid>\\d+)[\\t\\s]+(?<rss>\\d+)[\\t\\s]+(?<vsz>\\d+)[\\t\\s]+(?<user>[^\\t\\s]+)[\\t\\s]+(?<uid>\\d+)[\\t\\s]+(?<state>\\w)(?<stateplus>[\\w\\+<])?[\\t\\s]+(?<name>[^\\t\\s]+)$"
    private static final Pattern PROCESS_MATCHER = Pattern.compile("^([^\\t\\s]+)[\\t\\s]+(\\d+)" +
            "[\\t\\s]+(\\d+)[\\t\\s]+(\\d+)[\\t\\s]+(\\d+)[\\t\\s]+([^\\t\\s]+)[\\t\\s]+(\\d+)" +
            "[\\t\\s]+(\\w)([\\w+<])?[\\t\\s]+([^\\t\\s]+)$");

    private PackageManager pm;
    private HashMap<String, PackageInfo> installedPackages;

    ProcessParser() {
        pm = AppManager.getContext().getPackageManager();
        getInstalledPackages();
    }

    @NonNull
    HashMap<Integer, ProcessItem> parse() {
        Runner.Result result = Runner.runCommand(new String[]{Runner.TOYBOX, "ps", "-dwZ", "-o", "PID,PPID,RSS,VSZ,USER,UID,STAT,NAME"});
        HashMap<Integer, ProcessItem> processItems = new HashMap<>();
        if (result.isSuccessful()) {
            List<String> processInfoLines = result.getOutputAsList(1);
            for (String processInfoLine : processInfoLines) {
                if (processInfoLine.contains(":kernel:") || processInfoLine.contains("toybox"))
                    continue;
                try {
                    ProcessItem processItem = parseProcess(processInfoLine);
                    processItems.put(processItem.pid, processItem);
                } catch (Exception ignore) {
                }
            }
        }
        return processItems;
    }

    @NonNull
    private ProcessItem parseProcess(String line) throws Exception {
        Matcher matcher = PROCESS_MATCHER.matcher(line);
        if (matcher.find() && matcher.groupCount() == 10) {
            String processName = matcher.group(10);
            ProcessItem processItem;
            if (installedPackages.containsKey(processName)) {
                processItem = new AppProcessItem();
                @NonNull PackageInfo packageInfo = Objects.requireNonNull(installedPackages.get(processName));
                ((AppProcessItem) processItem).packageInfo = packageInfo;
                processItem.name = pm.getApplicationLabel(packageInfo.applicationInfo).toString();
            } else {
                processItem = new ProcessItem();
                processItem.name = processName;
            }
            //noinspection ConstantConditions
            processItem.pid = Integer.parseInt(matcher.group(2));
            //noinspection ConstantConditions
            processItem.ppid = Integer.parseInt(matcher.group(3));
            //noinspection ConstantConditions
            processItem.rss = Integer.parseInt(matcher.group(4));
            //noinspection ConstantConditions
            processItem.vsz = Integer.parseInt(matcher.group(5));
            processItem.user = matcher.group(6);
            //noinspection ConstantConditions
            processItem.uid = Integer.parseInt(matcher.group(7));
            //noinspection ConstantConditions
            processItem.state = Utils.getProcessStateName(matcher.group(8));
            processItem.state_extra = Utils.getProcessStateExtraName(matcher.group(9));
            return processItem;
        }
        throw new Exception("Failed to parse line");
    }

    private void getInstalledPackages() {
        List<PackageInfo> packageInfoList = new ArrayList<>();
        for (int userHandle : Users.getUsersHandles()) {
            try {
                packageInfoList.addAll(ApiSupporter.getInstance(LocalServer.getInstance())
                        .getInstalledPackages(0, userHandle));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        installedPackages = new HashMap<>(packageInfoList.size());
        for (PackageInfo info : packageInfoList) {
            installedPackages.put(info.packageName, info);
        }
    }
}
