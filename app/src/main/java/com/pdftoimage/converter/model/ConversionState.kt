package com.pdftoimage.converter.model

import java.io.File

sealed class ConversionState {
    data object Idle : ConversionState()
    
    data class Converting(
        val currentFile: String,
        val currentPage: Int,
        val totalPages: Int,
        val processedPages: Int
    ) : ConversionState()
    
    data class Completed(
        val totalPages: Int,
        val outputFiles: List<File>,
        val zipFile: File? = null,
        val savedToGallery: Boolean = true
    ) : ConversionState()
    
    data class Error(val message: String) : ConversionState()
}
