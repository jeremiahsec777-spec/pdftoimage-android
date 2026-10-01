package com.pdftoimage.converter.model

import android.net.Uri

data class PdfFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val pageCount: Int,
    val isQueued: Boolean = true
)
