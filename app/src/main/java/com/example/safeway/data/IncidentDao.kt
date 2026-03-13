package com.example.safeway.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface IncidentDao {

    @Insert
    suspend fun insertIncident(incident: Incident)

    @Update
    suspend fun updateIncident(incident: Incident)

    @Delete
    suspend fun deleteIncident(incident: Incident)

    @Query("SELECT * FROM incidents ORDER BY createdAtMillis DESC")
    suspend fun getAllIncidents(): List<Incident>

    @Query("SELECT * FROM incidents ORDER BY createdAtMillis DESC LIMIT :limit OFFSET :offset")
    suspend fun getIncidentsPaged(limit: Int, offset: Int): List<Incident>

    @Query("SELECT COUNT(*) FROM incidents")
    suspend fun getIncidentCount(): Int
}

