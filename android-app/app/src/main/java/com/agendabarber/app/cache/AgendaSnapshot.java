package com.agendabarber.app.cache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Local, private-to-app snapshot for READ-ONLY agenda access during network outages. */
@Entity(tableName = "agenda_snapshots")
public class AgendaSnapshot {
    @PrimaryKey
    @NonNull
    public String cacheKey;

    @NonNull
    public String responseJson;

    public long synchronizedAt;

    public AgendaSnapshot(
            @NonNull String cacheKey,
            @NonNull String responseJson,
            long synchronizedAt
    ) {
        this.cacheKey = cacheKey;
        this.responseJson = responseJson;
        this.synchronizedAt = synchronizedAt;
    }
}
