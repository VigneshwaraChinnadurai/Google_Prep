package com.vignesh.leetcodechecker.bookreader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.mail.MailInboxScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val BookColors = object {
    val background = Color(0xFF0D1117)
    val surface = Color(0xFF161B22)
    val surfaceVariant = Color(0xFF21262D)
    val primary = Color(0xFF34D8C4)
    val onSurface = Color(0xFFE6EDF3)
    val onSurfaceVariant = Color(0xFF8B949E)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookLibraryScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var books by remember { mutableStateOf(BookLibraryStorage.loadBooks(context)) }
    var selectedBook by remember { mutableStateOf<Book?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var pendingFileUri by remember { mutableStateOf<Uri?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var showMail by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingFileUri = uri
    }

    if (showMail) {
        MailInboxScreen(onBackClick = { showMail = false })
        return
    }

    val currentSelection = selectedBook
    if (currentSelection != null) {
        BookReaderScreen(
            book = currentSelection,
            onBackClick = {
                selectedBook = null
                books = BookLibraryStorage.loadBooks(context)
            },
            onProgress = { updated -> BookLibraryStorage.updateBook(context, updated) }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📖 Book Reader", color = BookColors.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BookColors.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { showMail = true }) {
                        Icon(Icons.Filled.Email, contentDescription = "Mail", tint = BookColors.onSurface)
                    }
                    IconButton(
                        onClick = {
                            filePicker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "*/*"))
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add book", tint = BookColors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BookColors.surface)
            )
        },
        containerColor = BookColors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            importError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            val categories = remember(books) { books.map { it.category }.distinct().sorted() }
            if (categories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All (${books.size})") }
                    )
                    categories.forEach { category ->
                        val count = books.count { it.category == category }
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text("$category ($count)") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            val visibleBooks = books.filter { selectedCategory == null || it.category == selectedCategory }

            if (visibleBooks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📚", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No books yet",
                        color = BookColors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap + to add an EPUB, PDF, or text file",
                        color = BookColors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleBooks, key = { it.id }) { book ->
                        BookRow(
                            book = book,
                            onClick = { selectedBook = book },
                            onDelete = {
                                BookLibraryStorage.deleteBook(context, book.id)
                                books = BookLibraryStorage.loadBooks(context)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(60.dp)) }
                }
            }
        }
    }

    val uriToImport = pendingFileUri
    if (uriToImport != null) {
        AddBookDialog(
            context = context,
            uri = uriToImport,
            existingCategories = BookLibraryStorage.categories(context),
            isImporting = isImporting,
            onDismiss = { pendingFileUri = null },
            onSave = { title, category ->
                isImporting = true
                importError = null
                scope.launch {
                    val result = importBook(context, uriToImport, title, category)
                    result.onSuccess {
                        books = BookLibraryStorage.loadBooks(context)
                        pendingFileUri = null
                    }.onFailure { e ->
                        importError = "Couldn't add book: ${e.message}"
                        pendingFileUri = null
                    }
                    isImporting = false
                }
            }
        )
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = BookColors.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BookColors.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (book.format) {
                        BookFormat.EPUB -> "📘"
                        BookFormat.PDF -> "📕"
                        BookFormat.TXT -> "📄"
                        BookFormat.UNKNOWN -> "📗"
                    },
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, color = BookColors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                Text(book.category, color = BookColors.onSurfaceVariant, fontSize = 11.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = BookColors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddBookDialog(
    context: Context,
    uri: Uri,
    existingCategories: List<String>,
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, category: String) -> Unit
) {
    var title by remember { mutableStateOf(displayNameFor(context, uri).substringBeforeLast('.')) }
    var category by remember { mutableStateOf(existingCategories.firstOrNull() ?: "Uncategorized") }

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = { Text("Add Book") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existingCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        existingCategories.forEach { existing ->
                            AssistChip(onClick = { category = existing }, label = { Text(existing, fontSize = 11.sp) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && category.isNotBlank() && !isImporting,
                onClick = { onSave(title.trim(), category.trim()) }
            ) {
                Text(if (isImporting) "Adding..." else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) { Text("Cancel") }
        }
    )
}

private fun displayNameFor(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "book"
}

private suspend fun importBook(context: Context, uri: Uri, title: String, category: String): Result<Book> =
    withContext(Dispatchers.IO) {
        runCatching {
            val fileName = displayNameFor(context, uri)
            val format = BookFormat.fromFileName(fileName)
            val extension = fileName.substringAfterLast('.', "bin")
            val book = Book(title = title, category = category, storedFileName = "", format = format)
            val storedFileName = "${book.id}.$extension"

            val target = File(BookLibraryStorage.booksDir(context), storedFileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Couldn't open the selected file.")

            val saved = book.copy(storedFileName = storedFileName)
            BookLibraryStorage.addBook(context, saved)
            saved
        }
    }
