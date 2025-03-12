package android.os;

import java.util.List;

import misc.utils.HiddenUtil;

public interface IIdmap2 extends IInterface {
    String DESCRIPTOR = "android.os.IIDmap2";
    String getIdmapPath(String var1, int var2) throws RemoteException;

    boolean removeIdmap(String var1, int var2) throws RemoteException;

    boolean verifyIdmap(String var1, String var2, String var3, int var4, boolean var5, int var6) throws RemoteException;

    String createIdmap(String var1, String var2, String var3, int var4, boolean var5, int var6) throws RemoteException;

    FabricatedOverlayInfo createFabricatedOverlay(FabricatedOverlayInternal var1) throws RemoteException;

    boolean deleteFabricatedOverlay(String var1) throws RemoteException;

    int acquireFabricatedOverlayIterator() throws RemoteException;

    void releaseFabricatedOverlayIterator(int var1) throws RemoteException;

    List<FabricatedOverlayInfo> nextFabricatedOverlayInfos(int var1) throws RemoteException;

    String dumpIdmap(String var1) throws RemoteException;

    public abstract static class Stub extends Binder {
        public static IIdmap2 asInterface(IBinder var0) {
            return HiddenUtil.throwUOE(var0);
        }
    }
}
