package net.dongliu.apk.parser.bean;

import net.dongliu.apk.parser.struct.dex.DexClassStruct;

import androidx.annotation.Nullable;

/**
 * @author dongliu
 */
public class DexClass {
    /**
     * the class name
     */
    private final String classType;
    private final String superClass;
    private final int accessFlags;

    public DexClass(String classType, String superClass, int accessFlags) {
        this.classType = classType;
        this.superClass = superClass;
        this.accessFlags = accessFlags;
    }

    public String getPackageName() {
        String packageName = classType;
        if (packageName.length() > 0) {
            if (packageName.charAt(0) == 'L') {
                packageName = packageName.substring(1);
            }
        }
        if (packageName.length() > 0) {
            int idx = classType.lastIndexOf('/');
            if (idx > 0) {
                packageName = packageName.substring(0, classType.lastIndexOf('/') - 1);
            } else if (packageName.charAt(packageName.length() - 1) == ';') {
                packageName = packageName.substring(0, packageName.length() - 1);
            }
        }
        return packageName.replace('/', '.');
    }

    public String getClassType() {
        return classType;
    }

    @Nullable
    public String getSuperClass() {
        return superClass;
    }

    public boolean isInterface() {
        return (this.accessFlags & DexClassStruct.ACC_INTERFACE) != 0;
    }

    public boolean isEnum() {
        return (this.accessFlags & DexClassStruct.ACC_ENUM) != 0;
    }

    public boolean isAnnotation() {
        return (this.accessFlags & DexClassStruct.ACC_ANNOTATION) != 0;
    }

    public boolean isPublic() {
        return (this.accessFlags & DexClassStruct.ACC_PUBLIC) != 0;
    }

    public boolean isProtected() {
        return (this.accessFlags & DexClassStruct.ACC_PROTECTED) != 0;
    }

    public boolean isStatic() {
        return (this.accessFlags & DexClassStruct.ACC_STATIC) != 0;
    }

    @Override
    public String toString() {
        return classType;
    }
}
