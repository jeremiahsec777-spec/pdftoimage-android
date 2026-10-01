package com.pdftoimage.converter.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.pdftoimage.converter.model.ConversionSettings
import com.pdftoimage.converter.model.ImageFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ConversionProgress(
    val currentFileName: String,
    val currentPage: Int,
    val totalPages: Int,
    val processedPages: Int
)

class PdfConverter(private val context: Context) {

    suspend fun convertPdf(
        uri: Uri,
        pdfName: String,
        settings: ConversionSettings,
        outputDir: File,
        pageOffset: Int,
        totalPages: Int,
        onProgress: (ConversionProgress) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        val outputFiles = mutableListOf<File>()

        val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Cannot open PDF: $pdfName")

        fileDescriptor.use { fd ->
            val renderer = PdfRenderer(fd)
            renderer.use { pdfRenderer ->
                val pageCount = pdfRenderer.pageCount
                val zoom = settings.dpi / 72f

                for (pageIndex in 0 until pageCount) {
                    val page = pdfRenderer.openPage(pageIndex)

                    // Calculate dimensions based on DPI
                    var width = (page.width * zoom).toInt()
                    var height = (page.height * zoom).toInt()

                    // Safety clamp to prevent OOM
                    val maxDimension = 4096
                    if (width > maxDimension || height > maxDimension) {
                        val scale = maxDimension.toFloat() / maxOf(width, height)
                        width = (width * scale).toInt()
                        height = (height * scale).toInt()
                    }

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    // Save the bitmap
                    val fileName = "${pdfName}_page_${(pageIndex + 1).toString().padStart(4, '0')}.${settings.format.extension}"
                    val outputFile = File(outputDir, fileName)
                    saveBitmap(bitmap, outputFile, settings)
                    bitmap.recycle()

                    outputFiles.add(outputFile)

                    onProgress(
                        ConversionProgress(
                            currentFileName = pdfName,
                            currentPage = pageIndex + 1,
                            totalPages = totalPages,
                            processedPages = pageOffset + pageIndex + 1
                        )
                    )
                }
            }
        }

        outputFiles
    }

    private fun saveBitmap(bitmap: Bitmap, file: File, settings: ConversionSettings) {
        FileOutputStream(file).use { fos ->
            when (settings.format) {
                ImageFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                ImageFormat.JPEG -> bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    settings.qualityPreset.quality,
                    fos
                )
                ImageFormat.WEBP -> bitmap.compress(
                    Bitmap.CompressFormat.WEBP_LOSSY,
                    settings.qualityPreset.quality,
                    fos
                )
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
            val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
            fd.use {
                val renderer = PdfRenderer(it)
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
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
        return name
    }

    fun getFileSize(uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    fun renderThumbnail(uri: Uri, pageIndex: Int = 0, maxWidth: Int = 400): Bitmap? {
        return try {
            val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            fd.use {
                val renderer = PdfRenderer(it)
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
        } catch (e: Exception) {
            null
        }
    }
}
