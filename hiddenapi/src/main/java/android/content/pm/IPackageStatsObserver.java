package android.content.pm;

public interface IPackageStatsObserver {
    void onGetStatsCompleted(PackageStats pStats, boolean succeeded);
}
