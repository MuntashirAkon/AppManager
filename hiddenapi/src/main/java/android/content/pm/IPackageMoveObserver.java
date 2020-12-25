package android.content.pm;

interface IPackageMoveObserver {
    void packageMoved(String packageName, int returnCode);
}
