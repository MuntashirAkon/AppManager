package io.github.muntashirakon.AppManager.appops.reflector;

import android.os.Build;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.OpEntry;

import static io.github.muntashirakon.AppManager.appops.reflector.ReflectUtils.getFieldValue;

class PackageOpsConverter {
        Object object;

        /* package */ PackageOpsConverter(Object object) {
            this.object = object;
        }

        @NonNull
        public String getPackageName() {
            Object packageName = getFieldValue(object, "mPackageName");
            if (packageName instanceof String) return (String) packageName;
            else return "";
        }

        public int getUid() {
            Object uid = getFieldValue(object, "mUid");
            if (uid instanceof Integer) return (int) uid;
            return AppOpsManager.OP_NONE;
        }

        public List<OpEntry> getOpEntries() {
            List<OpEntry> opEntries = new ArrayList<>();
            Object entries = getFieldValue(object, "mEntries");
            if (entries instanceof List) {
                for (Object o : (List<?>) entries) {
                    OpEntryConverter converter = new OpEntryConverter(o);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        opEntries.add(new OpEntry(converter.getOp(), converter.getMode(),
                                converter.getTime(), converter.getRejectTime(), converter.getDuration(),
                                converter.getProxyUid(), converter.getProxyPackageName()));
                    } else {
                        opEntries.add(new OpEntry(converter.getOp(), converter.getMode(),
                                converter.getTime(), converter.getRejectTime(), converter.getDuration(),
                                0, null));
                    }
                }
            }
            return opEntries;
        }
    }