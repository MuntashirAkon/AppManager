# Specify compression level
-optimizationpasses 5
# Algorithm for confusion
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
# Allow access to and modification of classes and class members with modifiers during optimization
-allowaccessmodification
# Rename file source to "Sourcefile" string
-renamesourcefileattribute SourceFile
# Keep line number
-keepattributes SourceFile,LineNumberTable
# Keep generics
-keepattributes Signature
# Keep all class members that implement the serializable interface
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
# Keep all class members that implement the percelable interface
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
# Don't minify server-related classes
-keep public class io.github.muntashirakon.AppManager.servermanager.** { *; }
-keep public class io.github.muntashirakon.AppManager.server.** { *; }
# Don't minify debug-sepcific resource file
-keep class io.github.muntashirakon.AppManager.debug.R$raw {*;}
