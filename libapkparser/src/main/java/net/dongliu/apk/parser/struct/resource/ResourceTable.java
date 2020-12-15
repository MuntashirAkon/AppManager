package net.dongliu.apk.parser.struct.resource;

import android.util.SparseArray;

import net.dongliu.apk.parser.struct.ResourceValue;
import net.dongliu.apk.parser.struct.StringPool;
import net.dongliu.apk.parser.utils.ResourceLoader;

import java.util.*;

import androidx.annotation.NonNull;

/**
 * The apk resource table
 *
 * @author dongliu
 */
public class ResourceTable {
    private Map<Short, ResourcePackage> packageMap = new HashMap<>();
    private StringPool stringPool;

    public static SparseArray<String> sysStyle = ResourceLoader.loadSystemStyles();

    public void addPackage(ResourcePackage resourcePackage) {
        this.packageMap.put(resourcePackage.getId(), resourcePackage);
    }

    public ResourcePackage getPackage(short id) {
        return this.packageMap.get(id);
    }

    public StringPool getStringPool() {
        return stringPool;
    }

    public void setStringPool(StringPool stringPool) {
        this.stringPool = stringPool;
    }


    /**
     * Get resources match the given resource id.
     */
    @NonNull
    public List<Resource> getResourcesById(long resourceId) {
        // An Android Resource id is a 32-bit integer. It comprises
        // an 8-bit Package id [bits 24-31]
        // an 8-bit Type id [bits 16-23]
        // a 16-bit Entry index [bits 0-15]


        short packageId = (short) (resourceId >> 24 & 0xff);
        short typeId = (short) ((resourceId >> 16) & 0xff);
        int entryIndex = (int) (resourceId & 0xffff);
        ResourcePackage resourcePackage = this.getPackage(packageId);
        if (resourcePackage == null) {
            return Collections.emptyList();
        }
        TypeSpec typeSpec = resourcePackage.getTypeSpec(typeId);
        List<Type> types = resourcePackage.getTypes(typeId);
        if (typeSpec == null || types == null) {
            return Collections.emptyList();
        }
        if (!typeSpec.exists(entryIndex)) {
            return Collections.emptyList();
        }

        // read from type resource
        List<Resource> result = new ArrayList<>();
        for (Type type : types) {
            ResourceEntry resourceEntry = type.getResourceEntry(entryIndex);
            if (resourceEntry == null) {
                continue;
            }
            ResourceValue currentResourceValue = resourceEntry.getValue();
            if (currentResourceValue == null) {
                continue;
            }

            // cyclic reference detect
            if (currentResourceValue instanceof ResourceValue.ReferenceResourceValue) {
                if (resourceId == ((ResourceValue.ReferenceResourceValue) currentResourceValue)
                        .getReferenceResourceId()) {
                    continue;
                }
            }

            result.add(new Resource(typeSpec, type, resourceEntry));
        }
        return result;
    }

    /**
     * contains all info for one resource
     */
    public static class Resource {
        private TypeSpec typeSpec;
        private Type type;
        private ResourceEntry resourceEntry;

        public Resource(TypeSpec typeSpec, Type type, ResourceEntry resourceEntry) {
            this.typeSpec = typeSpec;
            this.type = type;
            this.resourceEntry = resourceEntry;
        }

        public TypeSpec getTypeSpec() {
            return typeSpec;
        }

        public Type getType() {
            return type;
        }

        public ResourceEntry getResourceEntry() {
            return resourceEntry;
        }
    }
}
