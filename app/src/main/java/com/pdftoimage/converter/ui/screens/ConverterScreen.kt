package com.pdftoimage.converter.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun ConverterScreen(viewModel: ConverterViewModel, modifier: Modifier = Modifier) {
    val pdfFiles by viewModel.pdfFiles.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
    val outputFiles by viewModel.outputFiles.collectAsStateWithLifecycle()
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
            // Logic to handle folder URIs could be added here
        }
    )

    Scaffold(
        modifier = modifier,
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
                FloatingActionButton(
                    onClick = { viewModel.startConversion() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start Conversion")
                }
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
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add PDFs")
                            }
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Folder")
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Selected Files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
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
                        Spacer(modifier = Modifier.height(72.dp)) // FAB spacing
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
                    onShareZip = { zipFile -> ShareHelper.shareZip(context, zipFile) }
                )
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
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No PDFs Added",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add some PDF files to start converting them to images.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAddFiles,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add PDF Files")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onAddFolder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Folder")
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
            thumbnail = converter.renderThumbnail(pdfFile.uri, 0, 300)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = pdfFile.isQueued,
                onCheckedChange = { onToggleQueue() }
            )
            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${pdfFile.pageCount} pages • ${ShareHelper.formatFileSize(pdfFile.size)}",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Conversion Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Resolution (DPI)", style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    val presets = listOf(72, 150, 300, 600)
                    presets.forEach { dpi ->
                        FilterChip(
                            selected = settings.dpi == dpi,
                            onClick = { onDpiChange(dpi) },
                            label = { Text("$dpi") }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Format", style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    ImageFormat.entries.forEach { format ->
                        FilterChip(
                            selected = settings.format == format,
                            onClick = { onFormatChange(format) },
                            label = { Text(format.name) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quality", style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    QualityPreset.entries.forEach { quality ->
                        FilterChip(
                            selected = settings.qualityPreset == quality,
                            onClick = { onQualityChange(quality) },
                            label = { Text(quality.name) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Create ZIP archive", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = settings.createZip,
                    onCheckedChange = { onZipToggle(it) }
                )
            }
        }
    }
}

@Composable
fun ConversionOverlay(
    conversionState: ConversionState,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onShareImages: (List<java.io.File>) -> Unit,
    onShareZip: (java.io.File) -> Unit
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
                                "Converting...",
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
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )

                            Text(
                                "${conversionState.processedPages} of ${conversionState.totalPages} pages",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Button(
                                onClick = onCancel,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Cancel")
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
                                "Conversion Complete",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Successfully converted ${conversionState.totalPages} pages.",
                                style = MaterialTheme.typography.bodyMedium
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
                            } else if (conversionState.outputFiles.isNotEmpty()) {
                                Button(
                                    onClick = { onShareImages(conversionState.outputFiles) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Share Images")
                                }
                            }

                            OutlinedButton(
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
                                "Conversion Failed",
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
                                Text("Close")
                            }
                        }
                        else -> {
                        }
                    }
                }
            }
        }
    }
}
