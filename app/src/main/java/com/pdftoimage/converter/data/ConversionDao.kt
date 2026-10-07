package com.pdftoimage.converter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class JobWithPages(
    val id: Long,
    val pdfName: String,
    val pageCount: Int,
    val dpi: Int,
    val format: String,
    val quality: Int,
    val zipPath: String?,
    val timestamp: Long
)

@Dao
interface ConversionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: ConversionJobEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<ConvertedPageEntity>)

    @Query("SELECT * FROM conversion_jobs ORDER BY timestamp DESC")
    fun getAllJobs(): Flow<List<ConversionJobEntity>>

    @Query("SELECT * FROM converted_pages ORDER BY id DESC")
    fun getAllPages(): Flow<List<ConvertedPageEntity>>

    @Query("SELECT * FROM converted_pages WHERE jobId = :jobId ORDER BY pageNumber ASC")
    fun getPagesForJob(jobId: Long): Flow<List<ConvertedPageEntity>>

    @Query("DELETE FROM conversion_jobs WHERE id = :jobId")
    suspend fun deleteJobById(jobId: Long)

    @Query("DELETE FROM conversion_jobs")
    suspend fun deleteAllJobs()
}
