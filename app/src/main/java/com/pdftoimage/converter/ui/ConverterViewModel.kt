package com.pdftoimage.converter.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdftoimage.converter.data.AppDatabase
import com.pdftoimage.converter.data.ConversionRecord
import com.pdftoimage.converter.model.ConversionSettings
import com.pdftoimage.converter.model.ConversionState
import com.pdftoimage.converter.model.ImageFormat
import com.pdftoimage.converter.model.PdfFile
import com.pdftoimage.converter.model.QualityPreset
import com.pdftoimage.converter.service.PdfConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val converter = PdfConverter(application)
    private val dao = AppDatabase.getDatabase(application).conversionDao()

    // PDF queue
    private val _pdfFiles = MutableStateFlow<List<PdfFile>>(emptyList())
    val pdfFiles: StateFlow<List<PdfFile>> = _pdfFiles.asStateFlow()

    // Settings
    private val _settings = MutableStateFlow(ConversionSettings())
    val settings: StateFlow<ConversionSettings> = _settings.asStateFlow()

    // Conversion state
    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    // History
    val historyRecords = dao.getAllRecords()

    // Output files from last conversion
    private val _outputFiles = MutableStateFlow<List<File>>(emptyList())
    val outputFiles: StateFlow<List<File>> = _outputFiles.asStateFlow()

    // Selected PDF for preview
    private val _selectedPdfIndex = MutableStateFlow(-1)
    val selectedPdfIndex: StateFlow<Int> = _selectedPdfIndex.asStateFlow()

    private var conversionJob: Job? = null

    fun addPdfUris(uris: List<Uri>) {
        viewModelScope.launch {
            val newFiles = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    try {
                        // Take persistent permission
                        try {
                            getApplication<Application>().contentResolver.takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Exception) { }

                        val name = converter.getFileName(uri)
                        val size = converter.getFileSize(uri)
                        val pageCount = converter.getPageCount(uri)
                        if (pageCount > 0) {
                            PdfFile(uri = uri, name = name, size = size, pageCount = pageCount)
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            val current = _pdfFiles.value.toMutableList()
            val existingUris = current.map { it.uri }.toSet()
            val unique = newFiles.filter { it.uri !in existingUris }
            current.addAll(unique)
            _pdfFiles.value = current
            if (_selectedPdfIndex.value < 0 && current.isNotEmpty()) {
                _selectedPdfIndex.value = 0
            }
        }
    }

    fun removePdf(index: Int) {
        val current = _pdfFiles.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _pdfFiles.value = current
            if (_selectedPdfIndex.value >= current.size) {
                _selectedPdfIndex.value = current.size - 1
            }
        }
    }

    fun togglePdfQueued(index: Int) {
        val current = _pdfFiles.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(isQueued = !current[index].isQueued)
            _pdfFiles.value = current
        }
    }

    fun clearAllPdfs() {
        _pdfFiles.value = emptyList()
        _selectedPdfIndex.value = -1
    }

    fun selectPdf(index: Int) {
        _selectedPdfIndex.value = index
    }

    fun setDpi(dpi: Int) {
        _settings.value = _settings.value.copy(dpi = dpi.coerceIn(72, 1200))
    }

    fun setFormat(format: ImageFormat) {
        _settings.value = _settings.value.copy(format = format)
    }

    fun setQualityPreset(preset: QualityPreset) {
        _settings.value = _settings.value.copy(qualityPreset = preset)
    }

    fun setCreateZip(create: Boolean) {
        _settings.value = _settings.value.copy(createZip = create)
    }

    fun startConversion() {
        val queuedFiles = _pdfFiles.value.filter { it.isQueued }
        if (queuedFiles.isEmpty()) return

        conversionJob = viewModelScope.launch {
            val settings = _settings.value
            val totalPages = queuedFiles.sumOf { it.pageCount }

            _conversionState.value = ConversionState.Converting(
                currentFile = queuedFiles.first().name,
                currentPage = 0,
                totalPages = totalPages,
                processedPages = 0
            )

            val outputDir = File(getApplication<Application>().cacheDir, "converted_images")
            if (outputDir.exists()) outputDir.deleteRecursively()
            outputDir.mkdirs()

            val allOutputFiles = mutableListOf<File>()
            var pageOffset = 0

            try {
                for (pdfFile in queuedFiles) {
                    val files = converter.convertPdf(
                        uri = pdfFile.uri,
                        pdfName = pdfFile.name.removeSuffix(".pdf").removeSuffix(".PDF"),
                        settings = settings,
                        outputDir = outputDir,
                        pageOffset = pageOffset,
                        totalPages = totalPages,
                        onProgress = { progress ->
                            _conversionState.value = ConversionState.Converting(
                                currentFile = progress.currentFileName,
                                currentPage = progress.currentPage,
                                totalPages = progress.totalPages,
                                processedPages = progress.processedPages
                            )
                        }
                    )
                    allOutputFiles.addAll(files)
                    pageOffset += pdfFile.pageCount

                    // Record in history
                    withContext(Dispatchers.IO) {
                        dao.insert(
                            ConversionRecord(
                                pdfName = pdfFile.name,
                                pageCount = pdfFile.pageCount,
                                dpi = settings.dpi,
                                format = settings.format.displayName,
                                outputDir = outputDir.absolutePath
                            )
                        )
                    }
                }

                var zipFile: File? = null
                if (settings.createZip && allOutputFiles.isNotEmpty()) {
                    zipFile = File(outputDir, "converted_pages.zip")
                    withContext(Dispatchers.IO) {
                        converter.createZipArchive(allOutputFiles, zipFile)
                    }
                }

                _outputFiles.value = allOutputFiles
                _conversionState.value = ConversionState.Completed(
                    totalPages = allOutputFiles.size,
                    outputFiles = allOutputFiles,
                    zipFile = zipFile
                )
            } catch (e: Exception) {
                _conversionState.value = ConversionState.Error(
                    e.message ?: "Unknown error during conversion"
                )
            }
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        _conversionState.value = ConversionState.Idle
    }

    fun resetState() {
        _conversionState.value = ConversionState.Idle
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll()
        }
    }

    fun getTotalQueuedPages(): Int {
        return _pdfFiles.value.filter { it.isQueued }.sumOf { it.pageCount }
    }
}
