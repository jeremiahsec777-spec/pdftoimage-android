package com.pdftoimage.converter.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_jobs")
data class ConversionJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfName: String,
    val pageCount: Int,
    val dpi: Int,
    val format: String,
    val quality: Int,
    val zipPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "converted_pages",
    foreignKeys = [
        ForeignKey(
            entity = ConversionJobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["jobId"])]
)
data class ConvertedPageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jobId: Long,
    val pageNumber: Int,
    val filePath: String,
    val mediaStoreUri: String? = null,
    val fileSize: Long = 0L,
    val width: Int = 0,
    val height: Int = 0
)
