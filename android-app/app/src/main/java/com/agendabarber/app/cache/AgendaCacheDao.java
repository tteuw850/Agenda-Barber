package com.agendabarber.app.cache;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface AgendaCacheDao {
    @Nullable
    @Query("SELECT * FROM agenda_snapshots WHERE cacheKey = :key LIMIT 1")
    AgendaSnapshot find(String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AgendaSnapshot snapshot);

    @Query("DELETE FROM agenda_snapshots")
    void clearAll();
}
