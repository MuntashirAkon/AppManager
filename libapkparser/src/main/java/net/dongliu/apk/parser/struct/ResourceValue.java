// SPDX-License-Identifier: BSD-2-Clause AND GPL-3.0-or-later

package net.dongliu.apk.parser.struct;

import android.util.TypedValue;

import androidx.annotation.Nullable;

import net.dongliu.apk.parser.struct.resource.Densities;
import net.dongliu.apk.parser.struct.resource.ResourceEntry;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
import net.dongliu.apk.parser.struct.resource.Type;
import net.dongliu.apk.parser.struct.resource.TypeSpec;
import net.dongliu.apk.parser.utils.Locales;

import java.util.List;
import java.util.Locale;

/**
 * Resource entity, contains the resource id, should retrieve the value from resource table, or string pool if it is a string resource.
 */
// Copyright 2016 Liu Dong
public class ResourceValue extends TypedValue {
    public String stringValue;

    public ResourceValue(int type, String stringValue) {
        this.type = type;
        this.stringValue = stringValue;
    }

    public ResourceValue(int type, int data) {
        this.type = type;
        this.data = data;
    }

    /**
     * get value as string.
     */
    @Nullable
    public String toStringValue(@Nullable ResourceTable resourceTable, @Nullable Locale locale) {
        switch (type) {
            case TYPE_STRING:
                return stringValue;
            case TYPE_REFERENCE:
                return "@" + Long.toHexString(data & 0xFFFFFFFFL).toUpperCase();
            case TYPE_ATTRIBUTE:
                return "?" + Long.toHexString(data & 0xFFFFFFFFL).toUpperCase();
        }
        return coerceToString(type, data);
    }

    public static ResourceValue nullValue() {
        return NullResourceValue.instance;
    }

    /**
     * ReferenceResource ref one another resources, and may has different value for different resource config(locale, density, etc)
     */
    public static class ReferenceResourceValue extends ResourceValue {

        private ReferenceResourceValue(int value) {
            super(TypedValue.TYPE_STRING, value);
        }

        @Override
        public String toStringValue(ResourceTable resourceTable, Locale locale) {
            long resourceId = getReferenceResourceId();
            // android system styles.
            if (resourceId > AndroidConstants.SYS_STYLE_ID_START && resourceId < AndroidConstants.SYS_STYLE_ID_END) {
                return "@android:style/" + ResourceTable.sysStyle.get((int) resourceId);
            }

            String raw = "resourceId:0x" + Long.toHexString(resourceId);
            if (resourceTable == null) {
                return raw;
            }

            List<ResourceTable.Resource> resources = resourceTable.getResourcesById(resourceId);
            // read from type resource
            ResourceEntry selected = null;
            TypeSpec typeSpec = null;
            int currentLocalMatchLevel = -1;
            int currentDensityLevel = -1;
            for (ResourceTable.Resource resource : resources) {
                Type type = resource.getType();
                typeSpec = resource.getTypeSpec();
                ResourceEntry resourceEntry = resource.getResourceEntry();
                int localMatchLevel = Locales.match(locale, type.getLocale());
                int densityLevel = densityLevel(type.getDensity());
                if (localMatchLevel > currentLocalMatchLevel) {
                    selected = resourceEntry;
                    currentLocalMatchLevel = localMatchLevel;
                    currentDensityLevel = densityLevel;
                } else if (densityLevel > currentDensityLevel) {
                    selected = resourceEntry;
                    currentDensityLevel = densityLevel;
                }
            }
            String result;
            if (selected == null) {
                result = raw;
            } else if (locale == null) {
                result = "@" + typeSpec.getName() + "/" + selected.getKey();
            } else {
                result = selected.toStringValue(resourceTable, locale);
            }
            return result;
        }

        public long getReferenceResourceId() {
            return data & 0xFFFFFFFFL;
        }

        private static int densityLevel(int density) {
            if (density == Densities.ANY || density == Densities.NONE) {
                return -1;
            }
            if (density == Densities.DEFAULT) {
                return Densities.DEFAULT;
            }
            return density;
        }
    }

    private static class NullResourceValue extends ResourceValue {
        private static final NullResourceValue instance = new NullResourceValue();

        private NullResourceValue() {
            super(TYPE_NULL, 0);
        }

        @Nullable
        @Override
        public String toStringValue(ResourceTable resourceTable, Locale locale) {
            return null;
        }
    }
}
