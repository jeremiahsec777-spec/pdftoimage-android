package com.pdftoimage.converter.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdftoimage.converter.data.AppDatabase
import com.pdftoimage.converter.data.ConversionJobEntity
import com.pdftoimage.converter.data.ConvertedPageEntity
import com.pdftoimage.converter.model.ConversionSettings
import com.pdftoimage.converter.model.ConversionState
import com.pdftoimage.converter.model.ImageFormat
import com.pdftoimage.converter.model.PdfFile
import com.pdftoimage.converter.model.QualityPreset
import com.pdftoimage.converter.service.PdfConverter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // History & Gallery from Room database
    val historyJobs = dao.getAllJobs()
    val allConvertedPages = dao.getAllPages()

    // Transient output files from last active conversion session
    private val _outputFiles = MutableStateFlow<List<File>>(emptyList())
    val outputFiles: StateFlow<List<File>> = _outputFiles.asStateFlow()

    // Selected PDF for preview
    private val _selectedPdfIndex = MutableStateFlow(-1)
    val selectedPdfIndex: StateFlow<Int> = _selectedPdfIndex.asStateFlow()

    // Snackbar / User notification messages
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private var conversionJob: Job? = null

    fun addPdfUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newFiles = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    try {
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

            if (newFiles.isEmpty()) {
                _snackbarMessage.emit("No readable PDF files could be opened.")
                return@launch
            }

            val current = _pdfFiles.value.toMutableList()
            val existingUris = current.map { it.uri }.toSet()
            val unique = newFiles.filter { it.uri !in existingUris }
            current.addAll(unique)
            _pdfFiles.value = current
            if (_selectedPdfIndex.value < 0 && current.isNotEmpty()) {
                _selectedPdfIndex.value = 0
            }

            val addedCount = unique.size
            if (addedCount > 0) {
                _snackbarMessage.emit("Added $addedCount PDF document(s) to queue.")
            } else {
                _snackbarMessage.emit("Selected PDF(s) are already in the queue.")
            }
        }
    }

    fun addFolderUri(treeUri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }

                val discoveredUris = withContext(Dispatchers.IO) {
                    val uris = mutableListOf<Uri>()
                    val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                    if (rootDoc != null && rootDoc.isDirectory) {
                        scanFolderRecursively(rootDoc, uris)
                    }
                    uris
                }

                if (discoveredUris.isEmpty()) {
                    _snackbarMessage.emit("No PDF files found in selected folder.")
                } else {
                    addPdfUris(discoveredUris)
                }
            } catch (e: Exception) {
                _snackbarMessage.emit("Error reading folder: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private fun scanFolderRecursively(dir: DocumentFile, outList: MutableList<Uri>) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanFolderRecursively(file, outList)
            } else if (file.isFile) {
                val name = file.name?.lowercase() ?: ""
                val type = file.type?.lowercase() ?: ""
                if (name.endsWith(".pdf") || type == "application/pdf") {
                    outList.add(file.uri)
                }
            }
        }
    }

    fun removePdf(index: Int) {
        val current = _pdfFiles.value.toMutableList()
        if (index in current.indices) {
            val removed = current.removeAt(index)
            _pdfFiles.value = current
            if (_selectedPdfIndex.value >= current.size) {
                _selectedPdfIndex.value = current.size - 1
            }
            viewModelScope.launch {
                _snackbarMessage.emit("Removed ${removed.name}")
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
        viewModelScope.launch {
            _snackbarMessage.emit("Cleared queue")
        }
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

            // Dedicated job directory with unique timestamp - NEVER deletes previous runs
            val jobId = System.currentTimeMillis()
            val jobOutputDir = File(getApplication<Application>().filesDir, "conversions/job_$jobId")
            jobOutputDir.mkdirs()

            val allOutputFiles = mutableListOf<File>()
            val allPageEntities = mutableListOf<ConvertedPageEntity>()
            var pageOffset = 0
            val usedNames = mutableMapOf<String, Int>()

            try {
                for (pdfFile in queuedFiles) {
                    // Sanitize filename to prevent filesystem path traversal
                    var safeBaseName = pdfFile.name
                        .removeSuffix(".pdf")
                        .removeSuffix(".PDF")
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    if (safeBaseName.isBlank()) safeBaseName = "document"

                    // Handle collision of identically named PDFs in the same batch
                    val count = usedNames.getOrDefault(safeBaseName, 0)
                    usedNames[safeBaseName] = count + 1
                    val uniquePdfName = if (count > 0) "${safeBaseName}_$count" else safeBaseName

                    val results = converter.convertPdf(
                        uri = pdfFile.uri,
                        sanitizedPdfName = uniquePdfName,
                        settings = settings,
                        jobOutputDir = jobOutputDir,
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

                    allOutputFiles.addAll(results.map { it.file })
                    pageOffset += pdfFile.pageCount

                    // Record job in Room database
                    val jobEntity = ConversionJobEntity(
                        pdfName = pdfFile.name,
                        pageCount = pdfFile.pageCount,
                        dpi = settings.dpi,
                        format = settings.format.displayName,
                        quality = settings.qualityPreset.quality,
                        timestamp = System.currentTimeMillis()
                    )
                    val insertedJobId = withContext(Dispatchers.IO) {
                        dao.insertJob(jobEntity)
                    }

                    // Record pages in Room
                    val pageEntities = results.map { res ->
                        ConvertedPageEntity(
                            jobId = insertedJobId,
                            pageNumber = res.pageNumber,
                            filePath = res.file.absolutePath,
                            mediaStoreUri = res.mediaStoreUri?.toString(),
                            fileSize = res.size,
                            width = res.width,
                            height = res.height
                        )
                    }
                    withContext(Dispatchers.IO) {
                        dao.insertPages(pageEntities)
                    }
                    allPageEntities.addAll(pageEntities)
                }

                var zipFile: File? = null
                if (settings.createZip && allOutputFiles.isNotEmpty()) {
                    zipFile = File(jobOutputDir, "converted_pages_$jobId.zip")
                    withContext(Dispatchers.IO) {
                        converter.createZipArchive(allOutputFiles, zipFile)
                    }
                }

                _outputFiles.value = allOutputFiles
                _conversionState.value = ConversionState.Completed(
                    totalPages = allOutputFiles.size,
                    outputFiles = allOutputFiles,
                    zipFile = zipFile,
                    savedToGallery = true
                )
                _snackbarMessage.emit("Successfully converted ${allOutputFiles.size} page(s)! Saved to Pictures & Gallery.")
            } catch (ce: CancellationException) {
                // Cooperative coroutine cancellation: clean reset, do NOT treat as failure error
                _conversionState.value = ConversionState.Idle
                _snackbarMessage.emit("Conversion cancelled.")
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

    fun deleteHistoryJob(jobId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteJobById(jobId)
            _snackbarMessage.emit("Deleted conversion record.")
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAllJobs()
            _snackbarMessage.emit("Cleared all conversion history.")
        }
    }

    fun getTotalQueuedPages(): Int {
        return _pdfFiles.value.filter { it.isQueued }.sumOf { it.pageCount }
    }
}
