package com.pdftoimage.converter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_records")
data class ConversionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pdfName: String,
    val pageCount: Int,
    val dpi: Int,
    val format: String,
    val outputDir: String,
    val timestamp: Long = System.currentTimeMillis()
)
