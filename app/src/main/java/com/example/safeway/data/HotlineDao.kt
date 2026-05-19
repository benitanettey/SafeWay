package com.example.safeway.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HotlineDao {

    @Insert
    suspend fun insertHotline(hotline: Hotline)

    @Delete
    suspend fun deleteHotline(hotline: Hotline)

    @Query("SELECT * FROM hotlines ORDER BY id ASC")
    suspend fun getAllHotlines(): List<Hotline>
}
