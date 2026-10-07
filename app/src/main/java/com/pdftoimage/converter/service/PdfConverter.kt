package com.pdftoimage.converter.service

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.pdftoimage.converter.model.ConversionSettings
import com.pdftoimage.converter.model.ImageFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ConversionProgress(
    val currentFileName: String,
    val currentPage: Int,
    val totalPages: Int,
    val processedPages: Int
)

data class ConvertedPageResult(
    val pageNumber: Int,
    val file: File,
    val mediaStoreUri: Uri?,
    val width: Int,
    val height: Int,
    val size: Long
)

class PdfConverter(private val context: Context) {

    suspend fun convertPdf(
        uri: Uri,
        sanitizedPdfName: String,
        settings: ConversionSettings,
        jobOutputDir: File,
        pageOffset: Int,
        totalPages: Int,
        onProgress: suspend (ConversionProgress) -> Unit
    ): List<ConvertedPageResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ConvertedPageResult>()

        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            throw IOException("Cannot open PDF '$sanitizedPdfName': ${e.localizedMessage ?: "Unknown file access error"}", e)
        } ?: throw IOException("Could not open file descriptor for $sanitizedPdfName")

        pfd.use { fd ->
            val pdfRenderer = try {
                PdfRenderer(fd)
            } catch (e: SecurityException) {
                throw IOException("PDF '$sanitizedPdfName' is password-protected or encrypted.", e)
            } catch (e: Exception) {
                throw IOException("Unable to parse '$sanitizedPdfName': ${e.localizedMessage ?: "Corrupted PDF format"}", e)
            }

            pdfRenderer.use { renderer ->
                val pageCount = renderer.pageCount
                val zoom = settings.dpi / 72.0f

                for (pageIndex in 0 until pageCount) {
                    currentCoroutineContext().ensureActive()

                    val page = renderer.openPage(pageIndex)
                    var targetWidth = (page.width * zoom).toInt()
                    var targetHeight = (page.height * zoom).toInt()

                    // Safety clamp to prevent memory overflow (e.g. at 1200 DPI)
                    val maxDimension = 6144
                    if (targetWidth > maxDimension || targetHeight > maxDimension) {
                        val scale = maxDimension.toFloat() / maxOf(targetWidth, targetHeight)
                        targetWidth = (targetWidth * scale).toInt()
                        targetHeight = (targetHeight * scale).toInt()
                    }

                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    currentCoroutineContext().ensureActive()

                    // 1. Save to private app storage
                    val fileName = "${sanitizedPdfName}_page_${(pageIndex + 1).toString().padStart(4, '0')}.${settings.format.extension}"
                    val appFile = File(jobOutputDir, fileName)
                    saveBitmapToFile(bitmap, appFile, settings)

                    // 2. Save to MediaStore (Pictures/PDFToImage/sanitizedPdfName) so it appears in Photos/Gallery
                    val mediaStoreUri = saveBitmapToMediaStore(
                        bitmap = bitmap,
                        displayName = fileName,
                        subFolder = "PDFToImage/$sanitizedPdfName",
                        format = settings.format,
                        quality = settings.qualityPreset.quality
                    )

                    val result = ConvertedPageResult(
                        pageNumber = pageIndex + 1,
                        file = appFile,
                        mediaStoreUri = mediaStoreUri,
                        width = targetWidth,
                        height = targetHeight,
                        size = appFile.length()
                    )
                    results.add(result)
                    bitmap.recycle()

                    onProgress(
                        ConversionProgress(
                            currentFileName = sanitizedPdfName,
                            currentPage = pageIndex + 1,
                            totalPages = totalPages,
                            processedPages = pageOffset + pageIndex + 1
                        )
                    )
                }
            }
        }

        results
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File, settings: ConversionSettings) {
        FileOutputStream(file).use { fos ->
            compressBitmap(bitmap, fos, settings.format, settings.qualityPreset.quality)
        }
    }

    private fun saveBitmapToMediaStore(
        bitmap: Bitmap,
        displayName: String,
        subFolder: String,
        format: ImageFormat,
        quality: Int
    ): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$subFolder")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = context.contentResolver.insert(collection, contentValues) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { os ->
                compressBitmap(bitmap, os, format, quality)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            uri
        } catch (e: Exception) {
            null
        }
    }

    private fun compressBitmap(bitmap: Bitmap, out: OutputStream, format: ImageFormat, quality: Int) {
        when (format) {
            ImageFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            ImageFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            ImageFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
                }
            }
        }
    }

    fun createZipArchive(files: List<File>, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (file in files) {
                val entry = ZipEntry(file.name)
                zos.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    fun getPageCount(uri: Uri): Int {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
            pfd.use { fd ->
                val renderer = PdfRenderer(fd)
                val count = renderer.pageCount
                renderer.close()
                count
            }
        } catch (e: Exception) {
            0
        }
    }

    fun getFileName(uri: Uri): String {
        var name = "document.pdf"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    val resolvedName = cursor.getString(nameIndex)
                    if (!resolvedName.isNullOrBlank()) {
                        name = resolvedName
                    }
                }
            }
        } catch (_: Exception) { }
        return name
    }

    fun getFileSize(uri: Uri): Long {
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) { }
        return size
    }

    fun renderThumbnail(uri: Uri, pageIndex: Int = 0, maxWidth: Int = 400): Bitmap? {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            pfd.use { fd ->
                val renderer = PdfRenderer(fd)
                if (pageIndex >= renderer.pageCount) {
                    renderer.close()
                    return null
                }
                val page = renderer.openPage(pageIndex)
                val scale = maxWidth.toFloat() / page.width
                val width = maxWidth
                val height = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }
}
