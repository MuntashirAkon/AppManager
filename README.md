# apps_Packages Info (<-- ApplicationsInfo)

#### (Pull request are welcomed, don't hesitate to improve the app !)

Simple android application that shows all information available about all installed apps.
It can be a good source of inspiration for all beginners. It deals with activities, multi pane, fragments, async tasks ... 
![alt tag](https://bitbucket.org/oF2pks/fdroid-applications-info/raw/9a73274f8c99e6261fbb2ead15d2262e76858bdd/pixelC.png)

[![F-Droid](https://fdroid.gitlab.io/artwork/badge/get-it-on.png "Get it on F-Droid")](https://f-droid.org/en/packages/com.oF2pks.applicationsinfo/)
## Changelog: apps_Packages Info

 * 1.7.15:
     * Dynamic androidManifest via LongClick
     * Refresh button now active for detailView
 * 1.7.14:
     * MainScreen: apps starred for debug builds
     * Marshmallow: _permissions for both Used/Declared with AppOp association 
     * F-Droid option replaces: unleashed ClassyShark3xodus (now included via ToggleList)
     * manifestAndroid bugs: JellyBean, view leak fixed & errorPatch
     * (Uses-permission INFOs via 2xTap)
 * 1.7.13:
     * Signature cert: sort option (sha256 cert), complete cert
     * Fix reqGles Feature & possible loose Providers
     * Oreo _usesCleartextTraffic warning
     * Marshmallow minSDK
     * (Uses-permission INFOs via 2xTap)
 * 1.7.12:
     * 2nd additions: sorted sub-categories by name, installer origin detection (after update info), all services with corresponding singlePermission, permissions', activities' and services' flags updated (api 23/24/26/27), Uses-permission info 2-clicks
     * manifest: new Oreo permission: REQUEST_DELETE_PACKAGES (will still pop-up), limit GET_PACKAGE_SIZE" to android:maxSdkVersion="25", fix wasty reloads
     * translation: German (thx to F.Oflak), (Russian github PR, since v11)
 * 1.7.11:
     * Uses permission sorted with granted flag.
     * 3dots option to ClassyShark for all classes.
     * target sdk28
 * 1.7.10:
     * uid sort option 
 * 1.7.9:
     * 3xodus.apk intent 
     * Oreo/Pie fileSize spam replaced by sdk int
 * 1.7.8:
     * F-Droid initial release
     * Add gray-ed for disabled apps      
...
     
 * 1.7.7:
     * REFACTOR majeur->oF2pks 
 * 1.7:
     * Kind of colors & flags      
     * Add uid & shared_libs to Details 
     * Basics fixes for main crashes
...


[![Google Play](https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png "Get it on Google Play")](https://play.google.com/store/apps/details?id=com.majeur.applicationsinfo)
#### https://github.com/MajeurAndroid/Android-Applications-Info
## Changelog: ApplicationsInfo

 * 1.6:
     * Now using Loader framework, added SearchView and also various fixes.
 * 1.5:
     * Added net stats, fixes/improve
...     
 * 1.4:
     * Added size and sort by size in main list
     * Changed installation and last update dates format
     * Fixed label in detail for tablets
     * Fixed minute value that appears instead of month
 * 1.3:
     * Better display of manifest code
     * Design and bug fixies
 * 1.2:
     * View manifest file
 * 1.1:
     * Multi pane
     * Detailed size
 * 1.0:
     * Initial release

This app is using parts of [XmlApkParser](http://code.google.com/p/xml-apk-parser/) library, which is under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)


