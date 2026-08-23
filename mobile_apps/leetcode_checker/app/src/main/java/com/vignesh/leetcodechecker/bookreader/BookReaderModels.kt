package com.vignesh.leetcodechecker.bookreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class BookFormat(val extensions: List<String>) {
    TXT(listOf("txt")),
    EPUB(listOf("epub")),
    PDF(listOf("pdf")),
    UNKNOWN(emptyList());

    companion object {
        fun fromFileName(fileName: String): BookFormat {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions } ?: UNKNOWN
        }
    }
}

data class Book(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String,
    val storedFileName: String, // filename under filesDir/books/
    val format: BookFormat,
    val addedAtMillis: Long = System.currentTimeMillis(),
    val lastChapterIndex: Int = 0
)

/**
 * Local-only library: metadata in SharedPreferences (matching this app's existing
 * settings/storage convention -- see AppSettingsStore, AINewsStorage), actual book
 * files copied into internal storage (filesDir/books/) rather than kept as SAF URIs,
 * so they survive independent of whether the original picked file/folder still exists
 * and are covered by the app's existing filesDir backup.
 */
object BookLibraryStorage {
    private const val PREFS = "book_library_prefs"
    private const val KEY_BOOKS = "books_json"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun booksDir(context: Context): java.io.File =
        java.io.File(context.filesDir, "books").apply { mkdirs() }

    fun loadBooks(context: Context): List<Book> {
        val raw = prefs(context).getString(KEY_BOOKS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Book(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    category = obj.optString("category", "Uncategorized"),
                    storedFileName = obj.getString("storedFileName"),
                    format = runCatching { BookFormat.valueOf(obj.getString("format")) }.getOrDefault(BookFormat.UNKNOWN),
                    addedAtMillis = obj.optLong("addedAtMillis", 0L),
                    lastChapterIndex = obj.optInt("lastChapterIndex", 0)
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun saveBooks(context: Context, books: List<Book>) {
        val arr = JSONArray()
        books.forEach { book ->
            arr.put(
                JSONObject()
                    .put("id", book.id)
                    .put("title", book.title)
                    .put("category", book.category)
                    .put("storedFileName", book.storedFileName)
                    .put("format", book.format.name)
                    .put("addedAtMillis", book.addedAtMillis)
                    .put("lastChapterIndex", book.lastChapterIndex)
            )
        }
        prefs(context).edit().putString(KEY_BOOKS, arr.toString()).apply()
    }

    fun addBook(context: Context, book: Book) {
        saveBooks(context, loadBooks(context) + book)
    }

    fun updateBook(context: Context, book: Book) {
        val updated = loadBooks(context).map { if (it.id == book.id) book else it }
        saveBooks(context, updated)
    }

    fun deleteBook(context: Context, bookId: String) {
        val book = loadBooks(context).firstOrNull { it.id == bookId } ?: return
        java.io.File(booksDir(context), book.storedFileName).delete()
        saveBooks(context, loadBooks(context).filter { it.id != bookId })
    }

    fun categories(context: Context): List<String> =
        loadBooks(context).map { it.category }.distinct().sorted()
}
