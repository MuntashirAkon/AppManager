package net.dongliu.apk.parser.struct.resource;

import net.dongliu.apk.parser.struct.StringPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;

/**
 * Resource packge.
 *
 * @author dongliu
 */
public class ResourcePackage {
    // the packageName
    private String name;
    private short id;
    // contains the names of the types of the Resources defined in the ResourcePackage
    private StringPool typeStringPool;
    //  contains the names (keys) of the Resources defined in the ResourcePackage.
    private StringPool keyStringPool;

    public ResourcePackage(PackageHeader header) {
        this.name = header.getName();
        this.id = (short) header.getId();
    }

    private Map<Short, TypeSpec> typeSpecMap = new HashMap<>();

    private Map<Short, List<Type>> typesMap = new HashMap<>();

    public void addTypeSpec(TypeSpec typeSpec) {
        this.typeSpecMap.put(typeSpec.getId(), typeSpec);
    }

    @Nullable
    public TypeSpec getTypeSpec(short id) {
        return this.typeSpecMap.get(id);
    }

    public void addType(Type type) {
        List<Type> types = this.typesMap.get(type.getId());
        if (types == null) {
            types = new ArrayList<>();
            this.typesMap.put(type.getId(), types);
        }
        types.add(type);
    }

    @Nullable
    public List<Type> getTypes(short id) {
        return this.typesMap.get(id);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public short getId() {
        return id;
    }

    public void setId(short id) {
        this.id = id;
    }

    public StringPool getTypeStringPool() {
        return typeStringPool;
    }

    public void setTypeStringPool(StringPool typeStringPool) {
        this.typeStringPool = typeStringPool;
    }

    public StringPool getKeyStringPool() {
        return keyStringPool;
    }

    public void setKeyStringPool(StringPool keyStringPool) {
        this.keyStringPool = keyStringPool;
    }

    public Map<Short, TypeSpec> getTypeSpecMap() {
        return typeSpecMap;
    }

    public void setTypeSpecMap(Map<Short, TypeSpec> typeSpecMap) {
        this.typeSpecMap = typeSpecMap;
    }

    public Map<Short, List<Type>> getTypesMap() {
        return typesMap;
    }

    public void setTypesMap(Map<Short, List<Type>> typesMap) {
        this.typesMap = typesMap;
    }
}
