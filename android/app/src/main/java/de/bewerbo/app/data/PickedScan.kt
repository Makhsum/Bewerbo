package de.bewerbo.app.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * A file the user chose as the scan of a document, read off the device and not yet sent anywhere.
 *
 * It is held while the disclosure is being read — nothing leaves the phone until the user has
 * pressed the button under it — and it is what the add card shows: the name, the size and the page
 * count that pre-fills the Pages field.
 */
data class PickedScan(
    val fileName: String,
    /// application/pdf | image/jpeg | image/png
    val contentType: String,
    val bytes: ByteArray,
    val pageCount: Int,
) {
    val sizeKb: Int get() = (bytes.size + 1023) / 1024

    // A ByteArray in a data class compares by reference. That is the behaviour wanted here — the
    // state holds at most one pick at a time and a new pick is a new array — but it is written out
    // rather than inherited, so nobody has to work out whether it was meant.
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/**
 * Reads the file behind a picked [uri], or null when it is not one Bewerbo can store.
 *
 * The type is read from the BYTES rather than from [ContentResolver.getType], which mirrors what
 * the server does with the same file. Two readings of the same rule is a deliberate duplication
 * and not an accident: the server's is the one that decides, and this one is what lets the user be
 * told that a .docx renamed to .pdf will not do BEFORE the upload rather than after it. A picker
 * that reports no type at all, which several file apps do, is also not a reason to refuse a file.
 *
 * Nothing here is sent, written or stored — the bytes are handed back and the screen decides.
 */
fun readScan(context: Context, uri: Uri): PickedScan? {
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return null

    val contentType = scanContentTypeOf(bytes) ?: return null
    return PickedScan(
        fileName = displayName(context.contentResolver, uri) ?: "Scan.${scanExtension(contentType)}",
        contentType = contentType,
        bytes = bytes,
        pageCount = pageCountOf(context, bytes, contentType),
    )
}

/// What the bytes are, by their signature — the three types the Mappe stores, and null for
/// everything else. The same three the server accepts, by the same signatures.
fun scanContentTypeOf(bytes: ByteArray): String? = when {
    bytes.startsWith(0x25, 0x50, 0x44, 0x46) -> "application/pdf"
    bytes.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
    bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
    else -> null
}

/**
 * How many pages the chosen file has — the PDF's own count, one for a picture.
 *
 * This is what pre-fills the Pages field while the user has stated no count of their own, so that
 * the number the Anlagenverzeichnis prints comes off the document rather than off their memory of
 * it. A count they did type is left standing: a scan of three sheets that belongs to a two-page
 * document is the exception, and it is theirs to state.
 *
 * [PdfRenderer] needs a seekable descriptor and a content:// URI is not reliably one, so the bytes
 * go through a file in the cache. Zero means the file could not be opened as a PDF; the screen
 * shows one page and the server has the last word either way.
 */
private fun pageCountOf(context: Context, bytes: ByteArray, contentType: String): Int {
    if (contentType != "application/pdf") return 1

    val scratch = File(context.cacheDir, "scan-pick.pdf")
    return try {
        scratch.writeBytes(bytes)
        ParcelFileDescriptor.open(scratch, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { it.pageCount }
        }
    } catch (_: Exception) {
        1
    } finally {
        scratch.delete()
    }
}

/// The name the file has where the user picked it, so they recognise it in the list. Null when the
/// picker does not report one, which is what the caller's fallback is for.
private fun displayName(resolver: ContentResolver, uri: Uri): String? =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
    }

private fun ByteArray.startsWith(vararg signature: Int): Boolean {
    if (size < signature.size) return false
    return signature.withIndex().all { (index, byte) -> this[index] == byte.toByte() }
}
