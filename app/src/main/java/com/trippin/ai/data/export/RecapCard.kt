package com.trippin.ai.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.trippin.ai.data.model.Itinerary
import com.trippin.ai.data.model.Trip
import java.io.File
import java.time.format.DateTimeFormatter

/** Draws the plan as a 1080 x 1350 image (feed friendly) and shares it as a PNG. Nothing here is invented: it lists the real stops. */
object RecapCard {
    private const val W = 1080
    private const val H = 1350
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM")

    fun render(trip: Trip, itin: Itinerary): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), 0xFFFF3D6E.toInt(), 0xFF6C4DFF.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
        fun paint(p: Paint, size: Float, bold: Boolean) = p.apply {
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }

        var y = 130f
        c.drawText("TRIPPIN", 80f, y, paint(soft, 34f, true).apply { letterSpacing = 0.2f })
        y += 110f
        val title = trip.destination?.name ?: trip.title
        c.drawText(title.take(22), 80f, y, paint(white, 96f, true))
        y += 70f
        val first = itin.days.firstOrNull()?.date
        val last = itin.days.lastOrNull()?.date
        val range = if (first != null && last != null) "${first.format(dateFmt)} to ${last.format(dateFmt)}" else ""
        c.drawText("$range  |  ${trip.memberIds.size} travelling", 80f, y, paint(soft, 38f, false))
        y += 90f

        val shown = itin.days.take(5)
        shown.forEachIndexed { i, d ->
            c.drawText("Day ${i + 1}", 80f, y, paint(white, 44f, true))
            y += 54f
            d.stops.filter { !it.optional }.take(3).forEach { s ->
                c.drawText("  ${s.name.take(34)}", 80f, y, paint(soft, 36f, false))
                y += 48f
            }
            y += 26f
        }
        if (itin.days.size > shown.size) c.drawText("+ ${itin.days.size - shown.size} more days", 80f, y, paint(soft, 36f, false))
        c.drawText("Planned together on Trippin AI", 80f, H - 90f, paint(soft, 32f, false))
        return bmp
    }

    fun share(context: Context, trip: Trip, itin: Itinerary) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "trippin-recap.png")
        file.outputStream().use { render(trip, itin).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share recap"))
    }
}
