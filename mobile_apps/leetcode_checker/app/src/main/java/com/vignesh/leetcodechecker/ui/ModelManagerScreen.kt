package com.vignesh.leetcodechecker.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vignesh.leetcodechecker.llm.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    downloadedModels: List<DownloadedModel>,
    selectedModelPath: String?,
    downloadProgress: DownloadProgress?,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf<String?>(null) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Model Manager",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Storage info
            val totalStorage = downloadedModels.sumOf { it.sizeBytes }
            val storageText = when {
                totalStorage >= 1_000_000_000 -> "%.1f GB".format(totalStorage / 1_000_000_000.0)
                totalStorage >= 1_000_000 -> "%.0f MB".format(totalStorage / 1_000_000.0)
                else -> "0 MB"
            }
            Text(
                text = "${downloadedModels.size} models downloaded • $storageText used",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Download progress
            downloadProgress?.let { progress ->
                DownloadProgressCard(progress)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Downloaded models section
                if (downloadedModels.isNotEmpty()) {
                    item {
                        Text(
                            text = "Downloaded Models",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    items(downloadedModels) { model ->
                        DownloadedModelCard(
                            model = model,
                            isSelected = selectedModelPath == model.file.absolutePath,
                            onSelect = { onSelectModel(model.file.absolutePath) },
                            onDelete = { showConfirmDelete = model.file.name }
                        )
                    }
                    
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
                
                // Available models section
                item {
                    Text(
                        text = "Available for Download",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                item {
                    DeviceRecommendation()
                }
                
                items(ModelDownloadManager.AVAILABLE_MODELS) { modelInfo ->
                    val isDownloaded = downloadedModels.any { it.file.name == modelInfo.fileName }
                    val isDownloading = downloadProgress?.modelId == modelInfo.id && 
                        downloadProgress.status == DownloadStatus.DOWNLOADING
                    
                    AvailableModelCard(
                        modelInfo = modelInfo,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        onDownload = { onDownloadModel(modelInfo.id) }
                    )
                }
            }
        }
    }
    
    // Delete confirmation dialog
    showConfirmDelete?.let { fileName ->
        AlertDialog(
            onDismissRequest = { showConfirmDelete = null },
            title = { Text("Delete Model?") },
            text = { Text("This will free up storage space. You can re-download later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteModel(fileName)
                        showConfirmDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DeviceRecommendation() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Choose based on your device:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "• Low-end (2-4GB RAM): 135M-360M models\n" +
                           "• Mid-range (4-6GB RAM): 0.5B-1.5B models\n" +
                           "• High-end (6GB+ RAM): 1.5B-3B models",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DownloadProgressCard(progress: DownloadProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (progress.status) {
                        DownloadStatus.DOWNLOADING -> "Downloading ${progress.modelName}"
                        DownloadStatus.COMPLETED -> "Download complete!"
                        DownloadStatus.FAILED -> "Download failed"
                        DownloadStatus.CANCELLED -> "Download cancelled"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (progress.status == DownloadStatus.DOWNLOADING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (progress.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { progress.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${progress.progressFormatted} (${progress.progressPercent}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            progress.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DownloadedModelCard(
    model: DownloadedModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) 
            CardDefaults.outlinedCardBorder().copy(
                width = 2.dp,
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
            ) 
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = model.sizeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun AvailableModelCard(
    modelInfo: GgufModelInfo,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    val backgroundColor = when {
        isDownloaded -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        modelInfo.id.contains("coder") -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = modelInfo.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (modelInfo.id.contains("coder")) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "CODE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = modelInfo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "💾",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = modelInfo.sizeFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🧠",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RAM: ${modelInfo.ramRequired}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                when {
                    isDownloaded -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Downloaded",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    isDownloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    }
                    else -> {
                        FilledTonalButton(
                            onClick = onDownload,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("⬇️")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}
