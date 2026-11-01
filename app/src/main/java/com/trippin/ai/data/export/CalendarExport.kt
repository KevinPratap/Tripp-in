package com.trippin.ai.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.trippin.ai.data.model.Itinerary
import com.trippin.ai.data.model.Trip
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * File handling: writes the plan as an iCalendar (.ics, RFC 5545) file in the app's cache and
 * shares it through a FileProvider content:// URI so any calendar app can import it.
 * Times are "floating" (local to wherever you are), which is what a travel plan wants.
 */
object CalendarExport {
    private val day = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun ics(trip: Trip, itin: Itinerary): String {
        val lines = ArrayList<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:-//Trippin AI//EN"
        lines += "CALSCALE:GREGORIAN"
        val now = ZonedDateTime.now(ZoneOffset.UTC).format(stamp)
        itin.days.forEach { d ->
            val date = d.date.format(day)
            d.stops.forEach { s ->
                lines += "BEGIN:VEVENT"
                lines += "UID:${trip.id}-${s.key}@trippin.ai"
                lines += "DTSTAMP:$now"
                lines += "DTSTART:${date}T%02d%02d00".format(s.start / 60, s.start % 60)
                lines += "DTEND:${date}T%02d%02d00".format(minOf(s.leave, 24 * 60 - 1) / 60, minOf(s.leave, 24 * 60 - 1) % 60)
                lines += "SUMMARY:${escape(s.name)}"
                lines += "GEO:${s.lat};${s.lng}"
                lines += "LOCATION:${escape(s.name)}"
                lines += "DESCRIPTION:${escape("${trip.title} · ${s.risk} risk (${(s.pDisrupted * 100).toInt()}%)" + if (s.optional) " · optional" else "")}"
                lines += "END:VEVENT"
            }
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n", postfix = "\r\n") { fold(it) }
    }

    private fun escape(v: String) = v.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

    /** RFC 5545 §3.1: lines longer than 75 octets are folded with CRLF + space. */
    private fun fold(line: String): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 75) return line
        val sb = StringBuilder()
        var count = 0
        for (ch in line) {
            val n = ch.toString().toByteArray(Charsets.UTF_8).size
            if (count + n > 75) { sb.append("\r\n "); count = 1 }
            sb.append(ch); count += n
        }
        return sb.toString()
    }

    fun share(context: Context, trip: Trip, itin: Itinerary) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = trip.title.filter { it.isLetterOrDigit() }.take(30).ifBlank { "trip" }
        val file = File(dir, "trippin-$safe.ics")
        file.writeText(ics(trip, itin))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Add to calendar"))
    }
}
