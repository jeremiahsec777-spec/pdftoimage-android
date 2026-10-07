package com.pdftoimage.converter.model

enum class ImageFormat(val extension: String, val displayName: String, val mimeType: String) {
    PNG("png", "PNG", "image/png"),
    JPEG("jpg", "JPEG", "image/jpeg"),
    WEBP("webp", "WebP", "image/webp")
}

enum class QualityPreset(val quality: Int, val label: String) {
    HIGH(95, "High (95%)"),
    MEDIUM(80, "Medium (80%)"),
    LOW(60, "Low (60%)")
}

data class ConversionSettings(
    val dpi: Int = 300,
    val format: ImageFormat = ImageFormat.PNG,
    val qualityPreset: QualityPreset = QualityPreset.HIGH,
    val createZip: Boolean = false
)
