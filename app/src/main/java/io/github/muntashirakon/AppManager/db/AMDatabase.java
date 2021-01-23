/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import io.github.muntashirakon.AppManager.db.dao.AppDao;
import io.github.muntashirakon.AppManager.db.entity.App;

@Database(entities = {App.class}, version = 1)
public abstract class AMDatabase extends RoomDatabase {
    public abstract AppDao appDao();
}
