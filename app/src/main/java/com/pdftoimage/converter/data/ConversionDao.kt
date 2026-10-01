package com.pdftoimage.converter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionDao {
    @Insert
    suspend fun insert(record: ConversionRecord): Long

    @Query("SELECT * FROM conversion_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<ConversionRecord>>

    @Query("DELETE FROM conversion_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversion_records")
    suspend fun deleteAll()
}
