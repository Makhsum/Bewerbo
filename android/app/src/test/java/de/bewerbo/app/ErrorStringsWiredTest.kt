package de.bewerbo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every error_* string exists because some failure has to be readable in the user's language — so
 * one that nothing refers to is not a spare string, it is a failure that still reaches the reader
 * as the German the server sent.
 *
 * That is how it goes wrong every time: a new kind is raised, its sentence is written into all four
 * locales, and the case in errorMessage() is the step that is forgotten. The fall-through in
 * errorMessage() is deliberate and must stay — the German detail beats an empty snackbar for a kind
 * this build does not know — which is exactly why it hides the mistake instead of failing. A rule
 * that is only written in a comment is a rule the next kind quietly breaks, so it gets a gate, the
 * same way the emoji rule does in [EmojiFreeStringsTest] and the bar does in [NavigationShapeTest].
 */
class ErrorStringsWiredTest {

    // The test runs with the module directory as the working directory.
    private val strings = File("src/main/res/values/strings.xml")
    private val sources = File("src/main/java")

    private val declaration = Regex("""name="(error_[a-z_]+)"""")

    private fun declaredErrorStrings(): List<String> {
        require(strings.isFile) { "strings.xml not found at ${strings.absolutePath}" }
        return declaration.findAll(strings.readText()).map { it.groupValues[1] }.toList()
    }

    private fun sourceText(): String {
        require(sources.isDirectory) { "src/main/java not found at ${sources.absolutePath}" }
        return sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
    }

    @Test
    fun `every error string is referred to from the code`() {
        val code = sourceText()
        val unused = declaredErrorStrings().filterNot { code.contains("R.string.$it") }

        assertTrue(
            "These error strings are declared but nothing shows them, so the failure they were " +
                "written for still reaches the reader as the server's German. Add the kind to " +
                "errorMessage() in ui/components/DomainComponents.kt:\n" +
                unused.joinToString("\n"),
            unused.isEmpty(),
        )
    }

    @Test
    fun `the test can actually see what it scans`() {
        // Without this, a scan that found nothing because it looked in the wrong directory would
        // pass silently — which is the usual way a rule like this stops being enforced.
        assertTrue("No error strings found to check", declaredErrorStrings().size >= 10)
        assertTrue("No sources found to scan", sourceText().contains("fun errorMessage("))
    }
}
