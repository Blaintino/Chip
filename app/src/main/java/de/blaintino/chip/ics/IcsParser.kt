package de.blaintino.chip.ics

/**
 * Minimaler iCalendar-Leser (RFC 5545), reines Kotlin ohne Android-Abhängigkeit, damit er per
 * Unit-Test prüfbar ist. Liest nur, was für Abos gebraucht wird: VEVENTs mit ihren
 * Eigenschaften und den Kalendernamen. VTIMEZONE-Blöcke werden übersprungen, Zeitzonen kommen
 * aus dem TZID-Namen (siehe [IcsMapper]).
 */
data class IcsProperty(val name: String, val params: Map<String, String>, val value: String)

class IcsEvent(val properties: List<IcsProperty>) {
    fun first(name: String): IcsProperty? = properties.firstOrNull { it.name == name }
    fun all(name: String): List<IcsProperty> = properties.filter { it.name == name }
    fun text(name: String): String? = first(name)?.value?.let(IcsParser::unescapeText)
}

data class IcsCalendar(
    val name: String?,
    val timeZone: String?,
    val events: List<IcsEvent>,
)

object IcsParser {

    fun parse(content: String): IcsCalendar {
        var calendarName: String? = null
        var calendarZone: String? = null
        val events = mutableListOf<IcsEvent>()

        // Komponenten-Stapel: nur Eigenschaften direkt im VEVENT zählen (nicht in VALARM).
        val stack = ArrayDeque<String>()
        var current: MutableList<IcsProperty>? = null

        for (line in unfold(content)) {
            if (line.isBlank()) continue
            val prop = parseLine(line) ?: continue
            when (prop.name) {
                "BEGIN" -> {
                    val comp = prop.value.trim().uppercase()
                    stack.addLast(comp)
                    if (comp == "VEVENT" && stack.size == 2) current = mutableListOf()
                }
                "END" -> {
                    val comp = prop.value.trim().uppercase()
                    if (comp == "VEVENT" && stack.size == 2) {
                        current?.let { events += IcsEvent(it) }
                        current = null
                    }
                    if (stack.isNotEmpty()) stack.removeLast()
                }
                else -> when {
                    stack.size == 2 && stack.last() == "VEVENT" -> current?.add(prop)
                    stack.size == 1 && prop.name == "X-WR-CALNAME" -> calendarName = unescapeText(prop.value)
                    stack.size == 1 && prop.name == "X-WR-TIMEZONE" -> calendarZone = prop.value.trim()
                }
            }
        }
        return IcsCalendar(calendarName?.takeIf { it.isNotBlank() }, calendarZone, events)
    }

    /** Fortsetzungszeilen (beginnen mit Leerzeichen oder Tab) an die vorige Zeile hängen. */
    internal fun unfold(content: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (raw in content.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && sb.isNotEmpty()) {
                sb.append(raw, 1, raw.length)
            } else {
                if (sb.isNotEmpty()) result += sb.toString()
                sb.setLength(0)
                sb.append(raw)
            }
        }
        if (sb.isNotEmpty()) result += sb.toString()
        return result
    }

    /** `NAME;PARAM=WERT;PARAM2="a:b":Wert` – Doppelpunkte in Anführungszeichen gehören zum Parameter. */
    internal fun parseLine(line: String): IcsProperty? {
        var inQuotes = false
        var colon = -1
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ':' && !inQuotes) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)

        val parts = splitOutsideQuotes(head, ';')
        val name = parts.first().trim().uppercase()
        val params = mutableMapOf<String, String>()
        for (p in parts.drop(1)) {
            val eq = p.indexOf('=')
            if (eq <= 0) continue
            params[p.substring(0, eq).trim().uppercase()] = p.substring(eq + 1).trim().removeSurrounding("\"")
        }
        return IcsProperty(name, params, value)
    }

    private fun splitOutsideQuotes(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in s) {
            when {
                c == '"' -> { inQuotes = !inQuotes; sb.append(c) }
                c == sep && !inQuotes -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(c)
            }
        }
        out += sb.toString()
        return out
    }

    fun unescapeText(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val n = value[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n) // \\ \; \,
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString().trim()
    }
}
