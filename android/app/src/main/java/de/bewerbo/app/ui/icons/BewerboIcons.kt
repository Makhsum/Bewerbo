package de.bewerbo.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------------------------
// The icon set: 24 dp vectors on a 24 unit grid, 1.65 px stroke, round caps and joins.
//
// There is no emoji anywhere in this app. An emoji is the platform's picture, not the product's:
// it changes between Android versions, it cannot take the theme's colour, and it reads as a
// placeholder. Everything here is drawn, takes a tint, and is consistent in stroke weight.
//
// Material Symbols Rounded is the reference for the standard concepts. The four that have no
// standard glyph — DIN grid, anabin lookup, Faltmarke, Anlagen — are drawn for this product.
// ---------------------------------------------------------------------------------------------

private fun icon(name: String, vararg pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        pathData.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                // Black is a placeholder: Icon() tints the whole vector, so the token colour wins.
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.65f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object BewerboIcons {

    // -- navigation --------------------------------------------------------------------------

    val Overview: ImageVector = icon(
        "overview",
        "M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h6v6h-6z",
    )

    val Person: ImageVector = icon(
        "person",
        "M12 4a3.6 3.6 0 1 1 0 7.2 3.6 3.6 0 0 1 0-7.2z",
        "M5 20c0-3.3 3.1-5.6 7-5.6s7 2.3 7 5.6",
    )

    val Posting: ImageVector = icon(
        "posting",
        "M9 4h6v2.4H9z",
        "M15 5.2h2.6c.6 0 1 .5 1 1.1v12.6c0 .6-.4 1.1-1 1.1H6.4c-.6 0-1-.5-1-1.1V6.3c0-.6.4-1.1 1-1.1H9",
        "M9 11h6", "M9 14.6h6", "M9 18h3.6",
    )

    val Document: ImageVector = icon(
        "document",
        "M7 3.4h6.6L18 8v12.6H7z",
        "M13.4 3.4V8H18",
        "M9.6 12.6h5.2", "M9.6 16h5.2",
    )

    /// Anlagen — a paperclip, drawn rather than borrowed, because the standard attachment glyph is
    /// too thin to read next to the others at 24 dp.
    val Anlagen: ImageVector = icon(
        "anlagen",
        "M17.4 11.2 11 17.6a3.6 3.6 0 0 1-5.1-5.1l7-7a2.4 2.4 0 0 1 3.4 3.4l-7 7a1.2 1.2 0 0 1-1.7-1.7l6.3-6.3",
    )

    // -- the profile ------------------------------------------------------------------------

    val Experience: ImageVector = icon(
        "experience",
        "M4.6 8.4h14.8c.6 0 1 .5 1 1.1v8.4c0 .6-.4 1.1-1 1.1H4.6c-.6 0-1-.5-1-1.1V9.5c0-.6.4-1.1 1-1.1z",
        "M9.2 8.4V6.3c0-.6.4-1.1 1-1.1h3.6c.6 0 1 .5 1 1.1v2.1",
        "M3.6 12.8h16.8",
    )

    val Education: ImageVector = icon(
        "education",
        "M12 4.6 21 9l-9 4.4L3 9z",
        "M6.6 10.8v4.6c0 1.6 2.4 2.8 5.4 2.8s5.4-1.2 5.4-2.8v-4.6",
    )

    val Languages: ImageVector = icon(
        "languages",
        "M3.6 6.2h8.2", "M7.7 4.6v1.6",
        "M10.2 6.2c0 3.4-2.6 6.2-6 7.4",
        "M5.4 9.6c1 1.9 2.7 3.3 4.8 3.9",
        "M13 20l3.7-9 3.7 9", "M14.4 16.8h4.6",
    )

    /// The Zeitstrahl glyph: two lanes with a marked span. No standard icon means this.
    val Timeline: ImageVector = icon(
        "timeline",
        "M4 6h10", "M4 12h6", "M4 18h13",
        "M17.4 4.8v2.4", "M12.4 10.8v2.4", "M20.4 16.8v2.4",
    )

    /// The DIN grid glyph: a sheet with a measured margin. Drawn for this product.
    val DinGrid: ImageVector = icon(
        "din_grid",
        "M5.4 3.6h13.2v16.8H5.4z",
        "M9 3.6v16.8",
        "M9 8.4h9.6", "M9 12h6.6",
        "M6.6 5.4v2.2", "M6.6 16.4v2.2",
    )

    /// The Faltmarke: the fold line and its tick. There is no standard glyph for this at all.
    val Faltmarke: ImageVector = icon(
        "faltmarke",
        "M4.2 12h8.4", "M16.2 12h3.6",
        "M6.6 8.4 4.2 12l2.4 3.6",
        "M14.4 9v6",
    )

    // -- states ----------------------------------------------------------------------------

    val Check: ImageVector = icon(
        "check",
        "M12 3.6 19.2 6v6c0 4-3 7.2-7.2 8.4C7.8 19.2 4.8 16 4.8 12V6z",
        "M9.2 12.1l2 2 3.6-4",
    )

    val Attention: ImageVector = icon(
        "attention",
        "M12 4.2 21 19.8H3z",
        "M12 10v4.2", "M12 17.1v.1",
    )

    val Covered: ImageVector = icon(
        "covered",
        "M12 4a8 8 0 1 1 0 16 8 8 0 0 1 0-16z",
        "M8.6 12.2l2.2 2.2 4.6-4.8",
    )

    val NotClaimed: ImageVector = icon(
        "not_claimed",
        "M12 4a8 8 0 1 1 0 16 8 8 0 0 1 0-16z",
        "M8.4 12h7.2",
    )

    val Employer: ImageVector = icon(
        "employer",
        "M4.2 20.4V6.6l7.2-2.4v16.2",
        "M11.4 9.6h8.4v10.8",
        "M7.2 9.6v.1", "M7.2 13.2v.1", "M7.2 16.8v.1",
        "M15 13.2v.1", "M15 16.8v.1",
    )

    /// Referenznummer.
    val Reference: ImageVector = icon(
        "reference",
        "M9.6 4.2 7.8 19.8", "M16.2 4.2l-1.8 15.6",
        "M4.8 9h15", "M4.2 14.4h15",
    )

    val Period: ImageVector = icon(
        "period",
        "M4.8 6h14.4c.6 0 1 .5 1 1.1v11.3c0 .6-.4 1.1-1 1.1H4.8c-.6 0-1-.5-1-1.1V7.1c0-.6.4-1.1 1-1.1z",
        "M3.8 10.2h16.4",
        "M8.4 3.8v3.6", "M15.6 3.8v3.6",
    )

    val Rewrite: ImageVector = icon(
        "rewrite",
        "M16.4 4.6l3 3-9.6 9.6-3.9.9.9-3.9z",
        "M14.4 6.6l3 3",
    )

    val Template: ImageVector = icon(
        "template",
        "M12 3.6 20.4 8 12 12.4 3.6 8z",
        "M3.6 12 12 16.4 20.4 12",
        "M3.6 16 12 20.4 20.4 16",
    )

    val Preview: ImageVector = icon(
        "preview",
        "M2.8 12S6.4 6.4 12 6.4 21.2 12 21.2 12 17.6 17.6 12 17.6 2.8 12 2.8 12z",
        "M12 9.4a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2z",
    )

    val Export: ImageVector = icon(
        "export",
        "M12 3.8v10.4", "M8 10.4l4 3.8 4-3.8",
        "M4.4 16.6v2.6c0 .6.4 1 1 1h13.2c.6 0 1-.4 1-1v-2.6",
    )

    /// EU storage — a lock, used where the app states where the data lives.
    val EuStorage: ImageVector = icon(
        "eu_storage",
        "M6.4 10.4h11.2c.6 0 1 .5 1 1.1v7.3c0 .6-.4 1.1-1 1.1H6.4c-.6 0-1-.5-1-1.1v-7.3c0-.6.4-1.1 1-1.1z",
        "M8.4 10.4V7.8a3.6 3.6 0 0 1 7.2 0v2.6",
    )

    /// The anabin lookup: a magnifier over a certificate corner.
    val Anabin: ImageVector = icon(
        "anabin",
        "M10.8 4.4a6 6 0 1 1 0 12 6 6 0 0 1 0-12z",
        "M15.2 14.9 20 19.7",
        "M8.4 10.4h4.8", "M8.4 12.8h3",
    )

    val Settings: ImageVector = icon(
        "settings",
        "M12 9.4a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2z",
        "M19.1 14.2a1.4 1.4 0 0 0 .3 1.5l.1.1a1.7 1.7 0 1 1-2.4 2.4l-.1-.1a1.4 1.4 0 0 0-2.4 1v.2a1.7 1.7 0 1 1-3.4 0v-.1a1.4 1.4 0 0 0-2.4-1l-.1.1a1.7 1.7 0 1 1-2.4-2.4l.1-.1a1.4 1.4 0 0 0-1-2.4h-.2a1.7 1.7 0 1 1 0-3.4h.1a1.4 1.4 0 0 0 1-2.4l-.1-.1a1.7 1.7 0 1 1 2.4-2.4l.1.1a1.4 1.4 0 0 0 2.4-1v-.2a1.7 1.7 0 1 1 3.4 0v.1a1.4 1.4 0 0 0 2.4 1l.1-.1a1.7 1.7 0 1 1 2.4 2.4l-.1.1a1.4 1.4 0 0 0 1 2.4h.2a1.7 1.7 0 1 1 0 3.4h-.1a1.4 1.4 0 0 0-1.3.9z",
    )

    // -- actions ----------------------------------------------------------------------------

    val Add: ImageVector = icon("add", "M12 5.4v13.2", "M5.4 12h13.2")

    val ChevronRight: ImageVector = icon("chevron_right", "M9.6 5.4 16.2 12l-6.6 6.6")

    val Globe: ImageVector = icon(
        "globe",
        "M12 4a8 8 0 1 1 0 16 8 8 0 0 1 0-16z",
        "M4 12h16",
        "M12 4c2.1 2.2 3.2 5 3.2 8s-1.1 5.8-3.2 8c-2.1-2.2-3.2-5-3.2-8s1.1-5.8 3.2-8z",
    )

    val Refresh: ImageVector = icon(
        "refresh",
        "M19.2 12a7.2 7.2 0 1 1-2.1-5.1",
        "M19.4 4.4v3.8h-3.8",
    )

    val Mail: ImageVector = icon(
        "mail",
        "M4.4 5.8h15.2c.6 0 1 .5 1 1.1v10.2c0 .6-.4 1.1-1 1.1H4.4c-.6 0-1-.5-1-1.1V6.9c0-.6.4-1.1 1-1.1z",
        "M3.8 7 12 12.6 20.2 7",
    )

    val Search: ImageVector = icon(
        "search",
        "M10.8 4.4a6 6 0 1 1 0 12 6 6 0 0 1 0-12z",
        "M15.2 14.9 20 19.7",
    )

    val Filter: ImageVector = icon(
        "filter",
        "M3.8 5.6h16.4l-6.4 7.4v5.6l-3.6 1.8v-7.4z",
    )
}
