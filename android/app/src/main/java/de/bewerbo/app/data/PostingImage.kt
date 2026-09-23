package de.bewerbo.app.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * The words in a photographed or screenshotted advert.
 *
 * On a phone the advert is usually on the screen of another app, and typing it across by hand is
 * where people give up. A screenshot is the shortest way from seeing it to having it.
 *
 * The reading happens ON THE DEVICE. That is not only a nicety about what leaves the phone — the
 * bundled recogniser carries its model inside the APK, so this works the first time it is used and
 * without Google Play, which a downloaded model would not.
 *
 * What comes back is the recogniser's best reading and nothing more: no field is extracted here and
 * nothing is sent anywhere. It goes into the same field a pasted advert goes into, where the user
 * reads it and corrects it — which matters more for a photo than for anything else, because an
 * advert shot at an angle comes back with its lines in a plausible but not always right order.
 *
 * An empty string means the picture had no text the recogniser could make out.
 */
suspend fun readTextFromImage(context: Context, uri: Uri): String {
    val image = InputImage.fromFilePath(context, uri)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // ML Kit answers with a Task, and the call must not block the caller's thread. The recogniser
    // is closed either way: it holds a native detector, and one leaked per picture chosen would
    // outlive the screen that asked for it.
    return try {
        suspendCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it.text) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    } finally {
        recognizer.close()
    }
}
