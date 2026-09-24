package com.trippin.ai.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.trippin.ai.data.local.TripWithStops
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * File handling: writes the plan as an iCalendar (.ics) file in the app's cache, then shares it
 * through a FileProvider content:// URI so Google Calendar (or any app) can import it.
 */
object CalendarExport {
    private val day = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun ics(t: TripWithStops): String = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//Trippin AI//EN")
        val start = LocalDate.parse(t.trip.startDate)
        t.stops.sortedWith(compareBy({ it.dayIndex }, { it.orderInDay })).forEach { s ->
            val date = start.plusDays(s.dayIndex.toLong()).format(day)
            appendLine("BEGIN:VEVENT")
            appendLine("UID:trippin-${t.trip.id}-${s.id}@trippin.ai")
            appendLine("DTSTAMP:${date}T000000")
            appendLine("DTSTART:${date}T%02d%02d00".format(s.start / 60, s.start % 60))
            appendLine("DTEND:${date}T%02d%02d00".format(s.leave / 60, s.leave % 60))
            appendLine("SUMMARY:${escape(s.name)}")
            appendLine("GEO:${s.lat};${s.lng}")
            appendLine("DESCRIPTION:${s.riskLevel} risk (${(s.pDisrupted * 100).toInt()}%) · planned by Trippin' AI")
            appendLine("END:VEVENT")
        }
        appendLine("END:VCALENDAR")
    }

    private fun escape(v: String) = v.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;")

    fun share(context: Context, t: TripWithStops) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "trippin-${t.trip.id}.ics")
        file.writeText(ics(t))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Add to calendar"))
    }
}
