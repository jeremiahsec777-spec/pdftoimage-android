package com.pdftoimage.converter.model

enum class ImageFormat(val extension: String, val displayName: String) {
    PNG("png", "PNG"),
    JPEG("jpg", "JPEG"),
    WEBP("webp", "WebP")
}

enum class QualityPreset(val quality: Int, val label: String) {
    HIGH(95, "High"),
    MEDIUM(80, "Medium"),
    LOW(60, "Low")
}

data class ConversionSettings(
    val dpi: Int = 300,
    val format: ImageFormat = ImageFormat.PNG,
    val qualityPreset: QualityPreset = QualityPreset.HIGH,
    val createZip: Boolean = false
)
