package net.dongliu.apk.parser.struct;

import net.dongliu.apk.parser.struct.resource.*;
import net.dongliu.apk.parser.utils.Locales;

import java.util.List;
import java.util.Locale;

/**
 * Resource entity, contains the resource id, should retrieve the value from resource table, or string pool if it is a string resource.
 *
 * @author dongliu
 */
public abstract class ResourceValue {
    protected final int value;

    protected ResourceValue(int value) {
        this.value = value;
    }

    /**
     * get value as string.
     */
    public abstract String toStringValue(ResourceTable resourceTable, Locale locale);

    public static ResourceValue decimal(int value) {
        return new DecimalResourceValue(value);
    }

    public static ResourceValue hexadecimal(int value) {
        return new HexadecimalResourceValue(value);
    }

    public static ResourceValue bool(int value) {
        return new BooleanResourceValue(value);
    }

    public static ResourceValue string(int value, StringPool stringPool) {
        return new StringResourceValue(value, stringPool);
    }

    public static ResourceValue reference(int value) {
        return new ReferenceResourceValue(value);
    }

    public static ResourceValue nullValue() {
        return NullResourceValue.instance;
    }

    public static ResourceValue rgb(int value, int len) {
        return new RGBResourceValue(value, len);
    }

    public static ResourceValue dimension(int value) {
        return new DimensionValue(value);
    }

    public static ResourceValue fraction(int value) {
        return new FractionValue(value);
    }

    public static ResourceValue raw(int value, short type) {
        return new RawValue(value, type);
    }


    private static class DecimalResourceValue extends ResourceValue {

        private DecimalResourceValue(int value) {
            super(value);
        }

        @Override
        public String toStringValue(ResourceTable resourceTable, Locale locale) {
            return String.valueOf(value);
        }
    }

    private static class HexadecimalResourceValue extends ResourceValue {

        private HexadecimalResourceValue(int value) {
            super(value);
        }

        @Override
        public String toStringValue(ResourceTable resourceTable, Locale locale) {
            return "0x" + Integer.toHexString(value);
        }
    }

    private static class BooleanResourceValue extends ResourceValue {

        private BooleanResourceValue(int value) {
            super(value);
        }

        @Override
        public String toStringValue(ResourceTable resourceTable, Locale locale) {
            return String.valueOf(value != 0);
        }
    }

    private static class StringResourceValue extends ResourceValue {
        private final StringPool stringPool;

        private StringResourceValue(int value, StringPool stringPool) {
            super(value);
            this.stringPool = stringPool;
        }

        @Override
        public String toStringValue(ResourceTable resourceTable, Locale locale) {
            if (value >= 0) {
                return stringPool.get(value);
            } else {
                return null;
            }
        }

        @Override
        public String toString() {
            return value + ":" + stringPool.get(value);
        }
    }

    /**
     * ReferenceResource ref one another resources, and may has different value for different resource config(locale, density, etc)
     */
    public static class ReferenceResourceValue extends ResourceValue {

        private ReferenceResourceValue(int value) {
            super(value);
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
            return value & 0xFFFFFFFFL;
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
            super(-1);
        }

        @Override
        public String toStringValue(ResourceTable resourceTable, Locale locale) {
            return "";
        }
    }

    private static class RGBResourceValue extends ResourceValue {
        private final int len;

        private RGBResourceValue(int value, int len) {
            super(value);
            this.len = len;
        }

        @Override
        public String toStringValue(ResourceTable resourceTable, Locale locale) {
            StringBuilder sb = new StringBuilder();
            for (int i = len / 2 - 1; i >= 0; i--) {
                sb.append(Integer.toHexString((value >> i * 8) & 0xff));
            }
            return sb.toString();
        }
    }

    private static class DimensionValue extends ResourceValue {

        private DimensionValue(int value) {
            super(value);
        }

        @Override
        public String toStringValue(ResourceTable resourceTable, Locale locale) {
            short unit = (short) (value & 0xff);
            String unitStr;
            switch (unit) {
                case ResValue.ResDataCOMPLEX.UNIT_MM:
                    unitStr = "mm";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_PX:
                    unitStr = "px";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_DIP:
                    unitStr = "dp";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_SP:
                    unitStr = "sp";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_PT:
                    unitStr = "pt";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_IN:
                    unitStr = "in";
                    break;
                default:
                    unitStr = "unknown unit:0x" + Integer.toHexString(unit);
            }
            return (value >> 8) + unitStr;
        }
    }

    private static class FractionValue extends ResourceValue {

        private FractionValue(int value) {
            super(value);
        }

        @Override
        public String toStringValue(ResourceTable resourceTable, Locale locale) {
            // The low-order 4 bits of the data value specify the type of the fraction
            short type = (short) (value & 0xf);
            String pstr;
            switch (type) {
                case ResValue.ResDataCOMPLEX.UNIT_FRACTION:
                    pstr = "%";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_FRACTION_PARENT:
                    pstr = "%p";
                    break;
                default:
                    pstr = "unknown type:0x" + Integer.toHexString(type);
            }
            float f = Float.intBitsToFloat(value >> 4);
            return f + pstr;
        }
    }

    private static class RawValue extends ResourceValue {
        private final short dataType;

        private RawValue(int value, short dataType) {
            super(value);
            this.dataType = dataType;
        }

        @Override
        public String toStringValue(ResourceTable resourceTable, Locale locale) {
            return "{" + dataType + ":" + (value & 0xFFFFFFFFL) + "}";
        }
    }
}
