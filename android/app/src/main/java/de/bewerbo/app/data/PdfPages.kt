package de.bewerbo.app.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/// How wide a rasterised page is. An A4 page at this width comes out around 1020 px tall — more
/// than the preview ever shows on a phone, and still sharp when it is scaled down to a thumbnail.
private const val PageWidthPx = 720

/**
 * The exported Bewerbungsmappe, page by page, as bitmaps.
 *
 * The preview used to be a Compose copy of page 1 of the Anschreiben, which meant the Lebenslauf
 * and the Anlagenverzeichnis — most of the file the user is about to send — could not be looked at
 * at all. These pages are the REAL file, rendered by the same code that produces it, so what the
 * preview shows and what leaves the app cannot drift apart.
 *
 * [PdfRenderer] is part of the platform since API 21 and the app's minSdk is 26, so this needs no
 * library. An empty list means there was nothing to render — a download that produced no file.
 */
fun renderPdfPages(file: File): List<Bitmap> {
    if (!file.isFile || file.length() == 0L) return emptyList()

    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        PageWidthPx,
                        PageWidthPx * page.height / page.width,
                        Bitmap.Config.ARGB_8888,
                    )
                    // A PDF page is transparent wherever nothing is printed. Without this the paper
                    // would come out black under the dark theme, which is not what the employer
                    // opens.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }
}
