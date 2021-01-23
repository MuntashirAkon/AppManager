package io.github.muntashirakon.AppManager.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import io.github.muntashirakon.AppManager.db.dao.AppDao;
import io.github.muntashirakon.AppManager.db.entity.App;

@Database(entities = {App.class}, version = 1)
public abstract class AMDatabase extends RoomDatabase {
    public abstract AppDao appDao();
}
