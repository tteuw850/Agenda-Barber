package com.agendabarber.app.cache;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/** Schema upgrades must add migrations instead of destructive fallback. */
@Database(entities = {AgendaSnapshot.class}, version = 1, exportSchema = false)
public abstract class AgendaCacheDatabase extends RoomDatabase {
    public abstract AgendaCacheDao agendaCacheDao();
}
