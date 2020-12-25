package android.content.pm;

interface IPackageStatsObserver {
    void onGetStatsCompleted(PackageStats pStats, boolean succeeded);
}