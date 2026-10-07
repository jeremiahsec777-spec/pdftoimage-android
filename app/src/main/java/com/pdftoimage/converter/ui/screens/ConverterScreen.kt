package com.pdftoimage.converter.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdftoimage.converter.model.*
import com.pdftoimage.converter.service.PdfConverter
import com.pdftoimage.converter.service.ShareHelper
import com.pdftoimage.converter.ui.ConverterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    viewModel: ConverterViewModel,
    modifier: Modifier = Modifier,
    onNavigateToGallery: () -> Unit = {}
) {
    val pdfFiles by viewModel.pdfFiles.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
    val selectedPdfIndex by viewModel.selectedPdfIndex.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.addPdfUris(uris)
            }
        }
    )

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.addFolderUri(uri)
            }
        }
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("PDF to Image Converter") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (pdfFiles.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAllPdfs() }) {
                            Icon(Icons.Filled.ClearAll, contentDescription = "Clear All")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (pdfFiles.any { it.isQueued } && conversionState is ConversionState.Idle) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startConversion() },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    text = { Text("Convert (${viewModel.getTotalQueuedPages()} pages)") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (pdfFiles.isEmpty()) {
                EmptyStateView(
                    onAddFiles = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    onAddFolder = { folderPickerLauncher.launch(null) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add PDFs")
                            }
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Folder")
                            }
                        }
                    }

                    // Selected Document Large Preview Card
                    if (selectedPdfIndex in pdfFiles.indices) {
                        item {
                            SelectedPdfPreviewCard(
                                pdfFile = pdfFiles[selectedPdfIndex]
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Queue (${pdfFiles.count { it.isQueued }}/${pdfFiles.size} selected)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    itemsIndexed(pdfFiles) { index, pdfFile ->
                        PdfFileItem(
                            pdfFile = pdfFile,
                            isSelected = index == selectedPdfIndex,
                            onSelect = { viewModel.selectPdf(index) },
                            onToggleQueue = { viewModel.togglePdfQueued(index) },
                            onRemove = { viewModel.removePdf(index) }
                        )
                    }

                    item {
                        SettingsCard(
                            settings = settings,
                            onDpiChange = { viewModel.setDpi(it) },
                            onFormatChange = { viewModel.setFormat(it) },
                            onQualityChange = { viewModel.setQualityPreset(it) },
                            onZipToggle = { viewModel.setCreateZip(it) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // FAB clearance
                    }
                }
            }

            // Conversion Overlay
            if (conversionState !is ConversionState.Idle) {
                ConversionOverlay(
                    conversionState = conversionState,
                    onCancel = { viewModel.cancelConversion() },
                    onReset = { viewModel.resetState() },
                    onShareImages = { files -> ShareHelper.shareImages(context, files) },
                    onShareZip = { zipFile -> ShareHelper.shareZip(context, zipFile) },
                    onViewGallery = {
                        viewModel.resetState()
                        onNavigateToGallery()
                    }
                )
            }
        }
    }
}

@Composable
fun SelectedPdfPreviewCard(pdfFile: PdfFile) {
    val context = LocalContext.current
    var previewBitmap by remember(pdfFile.uri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(pdfFile.uri) { mutableStateOf(true) }

    LaunchedEffect(pdfFile.uri) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val converter = PdfConverter(context)
            previewBitmap = converter.renderThumbnail(pdfFile.uri, pageIndex = 0, maxWidth = 600)
            isLoading = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preview: ${pdfFile.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Badge {
                    Text("Page 1 of ${pdfFile.pageCount}")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                } else if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        "Unable to preview page",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(onAddFiles: () -> Unit, onAddFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(90.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No PDFs Added",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select one or multiple PDF documents, or pick an entire folder to batch convert.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onAddFiles,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select PDF Files")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onAddFolder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Folder")
        }
    }
}

@Composable
fun PdfFileItem(
    pdfFile: PdfFile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleQueue: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(pdfFile.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pdfFile.uri) {
        withContext(Dispatchers.IO) {
            val converter = PdfConverter(context)
            thumbnail = converter.renderThumbnail(pdfFile.uri, 0, 200)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = pdfFile.isQueued,
                onCheckedChange = { onToggleQueue() }
            )

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = "PDF Thumbnail",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pdfFile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${pdfFile.pageCount} page(s) • ${ShareHelper.formatFileSize(pdfFile.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCard(
    settings: ConversionSettings,
    onDpiChange: (Int) -> Unit,
    onFormatChange: (ImageFormat) -> Unit,
    onQualityChange: (QualityPreset) -> Unit,
    onZipToggle: (Boolean) -> Unit
) {
    var showCustomDpiDialog by remember { mutableStateOf(false) }
    var customDpiText by remember { mutableStateOf(settings.dpi.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Conversion Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Resolution DPI
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Resolution (DPI): ${settings.dpi}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        customDpiText = settings.dpi.toString()
                        showCustomDpiDialog = true
                    }) {
                        Text("Custom DPI")
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    val presets = listOf(72, 150, 200, 300, 400, 600)
                    presets.forEach { dpi ->
                        FilterChip(
                            selected = settings.dpi == dpi,
                            onClick = { onDpiChange(dpi) },
                            label = { Text("$dpi") }
                        )
                    }
                }
            }

            // Image Format
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Image Format", style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    ImageFormat.entries.forEach { format ->
                        FilterChip(
                            selected = settings.format == format,
                            onClick = { onFormatChange(format) },
                            label = { Text(format.displayName) }
                        )
                    }
                }
            }

            // Quality (Hide or grey out if PNG is selected, as PNG is lossless)
            if (settings.format != ImageFormat.PNG) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Compression Quality", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        QualityPreset.entries.forEach { quality ->
                            FilterChip(
                                selected = settings.qualityPreset == quality,
                                onClick = { onQualityChange(quality) },
                                label = { Text(quality.label) }
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "PNG is lossless: output preserves 100% vector fidelity without compression artifacts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // ZIP Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Package as ZIP archive", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Bundle all converted pages into a single .zip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.createZip,
                    onCheckedChange = { onZipToggle(it) }
                )
            }
        }
    }

    if (showCustomDpiDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDpiDialog = false },
            title = { Text("Set Custom DPI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter DPI resolution between 72 and 1200:")
                    OutlinedTextField(
                        value = customDpiText,
                        onValueChange = { customDpiText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = customDpiText.toIntOrNull()
                    if (parsed != null && parsed in 72..1200) {
                        onDpiChange(parsed)
                        showCustomDpiDialog = false
                    }
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDpiDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ConversionOverlay(
    conversionState: ConversionState,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onShareImages: (List<java.io.File>) -> Unit,
    onShareZip: (java.io.File) -> Unit,
    onViewGallery: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (conversionState) {
                        is ConversionState.Converting -> {
                            Text(
                                "Converting Documents",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "File: ${conversionState.currentFile}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val progress = if (conversionState.totalPages > 0) {
                                conversionState.processedPages.toFloat() / conversionState.totalPages
                            } else {
                                0f
                            }

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                            )

                            Text(
                                "${conversionState.processedPages} of ${conversionState.totalPages} pages completed (${(progress * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall
                            )

                            OutlinedButton(
                                onClick = onCancel,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel Conversion")
                            }
                        }

                        is ConversionState.Completed -> {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                "Conversion Complete!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Converted ${conversionState.totalPages} page(s) successfully.\nSaved to Pictures/PDFToImage & Gallery.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            if (conversionState.zipFile != null) {
                                Button(
                                    onClick = { onShareZip(conversionState.zipFile) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Share ZIP Archive")
                                }
                            }

                            if (conversionState.outputFiles.isNotEmpty()) {
                                Button(
                                    onClick = { onShareImages(conversionState.outputFiles) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Share Images (${conversionState.outputFiles.size})")
                                }
                            }

                            FilledTonalButton(
                                onClick = onViewGallery,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("View in Gallery")
                            }

                            TextButton(
                                onClick = onReset,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Done")
                            }
                        }

                        is ConversionState.Error -> {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                "Conversion Error",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                conversionState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = onReset,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Dismiss")
                            }
                        }

                        else -> { }
                    }
                }
            }
        }
    }
}
