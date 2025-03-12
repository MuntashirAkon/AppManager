// SPDX-License-Identifier: Apache-2.0

package android.content.om;

import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.RequiresApi;

import java.util.List;
import java.util.Map;

import misc.utils.HiddenUtil;

/**
 * Api for getting information about overlay packages.
 */
@SuppressWarnings("unused")
@RequiresApi(Build.VERSION_CODES.O)
public interface IOverlayManager extends IInterface {
    /**
     * Returns information about all installed overlay packages for the
     * specified user. If there are no installed overlay packages for this user,
     * an empty map is returned (i.e. null is never returned). The returned map is a
     * mapping of target package names to lists of overlays. Each list for a
     * given target package is sorted in priority order, with the overlay with
     * the highest priority at the end of the list.
     *
     * @param userId The user to get the OverlayInfos for.
     * @return A Map<String, List<OverlayInfo>> with target package names
     *         mapped to lists of overlays; if no overlays exist for the
     *         requested user, an empty map is returned.
     */
    Map<String, List<OverlayInfo>> getAllOverlays(int userId);

    /**
     * Returns information about all overlays for the given target package for
     * the specified user. The returned list is ordered according to the
     * overlay priority with the highest priority at the end of the list.
     *
     * @param targetPackageName The name of the target package.
     * @param userId The user to get the OverlayInfos for.
     * @return A list of OverlayInfo objects; if no overlays exist for the
     *         requested package, an empty list is returned.
     */
    List<OverlayInfo> getOverlayInfosForTarget(String targetPackageName, int userId);

    /**
     * Returns information about the overlay with the given package name for the
     * specified user.
     *
     * @param packageName The name of the overlay package.
     * @param userId The user to get the OverlayInfo for.
     * @return The OverlayInfo for the overlay package; or null if no such
     *         overlay package exists.
     */
    OverlayInfo getOverlayInfo(String packageName, int userId);

    /**
     * Returns information about the overlay with the given package name for the
     * specified user.
     *
     * @param packageName The name of the overlay package.
     * @param userId The user to get the OverlayInfo for.
     * @return The OverlayInfo for the overlay package; or null if no such
     *         overlay package exists.
     */
    OverlayInfo getOverlayInfoByIdentifier(OverlayIdentifier packageName, int userId);

    /**
     * Request that an overlay package be enabled or disabled when possible to
     * do so.
     *
     * It is always possible to disable an overlay, but due to technical and
     * security reasons it may not always be possible to enable an overlay. An
     * example of the latter is when the related target package is not
     * installed. If the technical obstacle is later overcome, the overlay is
     * automatically enabled at that point in time.
     *
     * An enabled overlay is a part of target package's resources, i.e. it will
     * be part of any lookups performed via {@link android.content.res.Resources}
     * and {@link android.content.res.AssetManager}. A disabled overlay will no
     * longer affect the resources of the target package. If the target is
     * currently running, its outdated resources will be replaced by new ones.
     * This happens the same way as when an application enters or exits split
     * window mode.
     *
     * @param packageName The name of the overlay package.
     * @param enable true to enable the overlay, false to disable it.
     * @param userId The user for which to change the overlay.
     * @return true if the system successfully registered the request, false otherwise.
     */
    boolean setEnabled(String packageName, boolean enable, int userId);

    /**
     * Request that an overlay package is enabled and any other overlay packages with the same
     * target package are disabled.
     *
     * See {@link #setEnabled} for the details on overlay packages.
     *
     * @param packageName the name of the overlay package to enable.
     * @param enabled must be true, otherwise the operation fails.
     * @param userId The user for which to change the overlay.
     * @return true if the system successfully registered the request, false otherwise.
     */
    boolean setEnabledExclusive(String packageName, boolean enable, int userId);

    /**
     * Request that an overlay package is enabled and any other overlay packages with the same
     * target package and category are disabled.
     *
     * See {@link #setEnabled} for the details on overlay packages.
     *
     * @param packageName the name of the overlay package to enable.
     * @param userId The user for which to change the overlay.
     * @return true if the system successfully registered the request, false otherwise.
     */
    boolean setEnabledExclusiveInCategory(String packageName, int userId);

    /**
     * Change the priority of the given overlay to be just higher than the
     * overlay with package name newParentPackageName. Both overlay packages
     * must have the same target and user.
     *
     * @see getOverlayInfosForTarget
     *
     * @param packageName The name of the overlay package whose priority should
     *        be adjusted.
     * @param newParentPackageName The name of the overlay package the newly
     *        adjusted overlay package should just outrank.
     * @param userId The user for which to change the overlay.
     */
    boolean setPriority(String packageName, String newParentPackageName, int userId);

    /**
     * Change the priority of the given overlay to the highest priority relative to
     * the other overlays with the same target and user.
     *
     * @see getOverlayInfosForTarget
     *
     * @param packageName The name of the overlay package whose priority should
     *        be adjusted.
     * @param userId The user for which to change the overlay.
     */
    boolean setHighestPriority(String packageName, int userId);

    /**
     * Change the priority of the overlay to the lowest priority relative to
     * the other overlays for the same target and user.
     *
     * @see getOverlayInfosForTarget
     *
     * @param packageName The name of the overlay package whose priority should
     *        be adjusted.
     * @param userId The user for which to change the overlay.
     */
    boolean setLowestPriority(String packageName, int userId);

    /**
     * Returns the list of default overlay packages, or an empty array if there are none.
     */
    String[] getDefaultOverlayPackages();

    /**
     * Invalidates and removes the idmap for an overlay,
     * @param packageName The name of the overlay package whose idmap should be deleted.
     */
    void invalidateCachesForOverlay(String packageName, int userId);

    /**
     * Perform a series of requests related to overlay packages. This is an
     * atomic operation: either all requests were performed successfully and
     * the changes were propagated to the rest of the system, or at least one
     * request could not be performed successfully and nothing is changed and
     * nothing is propagated to the rest of the system.
     *
     * @see OverlayManagerTransaction
     *
     * @param transaction the series of overlay related requests to perform
     * @throws SecurityException if the transaction failed
     */
    void commit(OverlayManagerTransaction transaction);

    /**
     * Returns a String of a list of partitions from low priority to high.
     */
    String getPartitionOrder();

    /**
     * Returns a boolean which represent whether the partition list is sorted by default.
     * If not then it should be sorted by /product/overlay/partition_order.xml.
     */
    boolean isDefaultPartitionOrder();

    public static abstract class Stub {
        public static IOverlayManager asInterface(IBinder binder) {
            return HiddenUtil.throwUOE(binder);
        }
    }
}
