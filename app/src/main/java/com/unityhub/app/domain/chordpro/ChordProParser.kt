package com.unityhub.app.domain.chordpro

data class ChordPosition(
    val position: Int,
    val chord: String
)

data class ChordProLine(
    val lyrics: String,
    val chords: List<ChordPosition> = emptyList(),
    val section: String? = null,
    val comment: Boolean = false
)

data class ChordProDocument(
    val title: String? = null,
    val artist: String? = null,
    val key: String? = null,
    val lines: List<ChordProLine>,
    val tempo: Int? = null,
    val timeSignature: String? = null,
    val capo: Int? = null,
    val isTruncated: Boolean = false
)

object ChordProParser {
    const val MAX_CHORDS_PER_LINE = 256
    const val MAX_DISPLAY_LINES = 6_000
    const val MAX_LINE_CHARACTERS = 4_096
    private const val MAX_CHORD_LABEL_CHARACTERS = 64
    private val inlineChord = Regex("\\[([^\\]]+)]")
    private val directive = Regex("""^\{[ \t]*([a-zA-Z0-9_]+)[ \t]*(?::[ \t]*(.*?))?[ \t]*\}$""")
    private val timeSignaturePattern = Regex("^\\d{1,2}/\\d{1,2}$")

    private data class BoundedSourceLine(
        val value: String,
        val truncated: Boolean
    )

    private fun normalizeSource(source: String): String =
        source.removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u2028', '\n')
            .replace('\u2029', '\n')

    private fun boundedSourceLines(source: String): Sequence<BoundedSourceLine> = sequence {
        var start = 0
        var index = 0
        while (index < source.length) {
            val character = source[index]
            if (character == '\n' || character == '\r') {
                val lineLength = index - start
                val end = minOf(index, start + MAX_LINE_CHARACTERS)
                yield(BoundedSourceLine(source.substring(start, end), lineLength > MAX_LINE_CHARACTERS))
                index++
                if (character == '\r' && index < source.length && source[index] == '\n') {
                    index++
                }
                start = index
            } else {
                index++
            }
        }
        val lineLength = source.length - start
        val end = minOf(source.length, start + MAX_LINE_CHARACTERS)
        yield(BoundedSourceLine(source.substring(start, end), lineLength > MAX_LINE_CHARACTERS))
    }

    fun parse(source: String): ChordProDocument {
        var title: String? = null
        var artist: String? = null
        var key: String? = null
        var tempo: Int? = null
        var timeSignature: String? = null
        var capo: Int? = null
        var activeSection: String? = null
        var isTruncated = false
        val lines = mutableListOf<ChordProLine>()

        // Saved charts are user-provided text. Parse lazily and cap both the
        // number of rows and the size of an individual row before Compose sees
        // them. This prevents newline-heavy or single-line 1 MB charts from
        // allocating/rendering an unbounded number of text nodes.
        boundedSourceLines(normalizeSource(source)).take(MAX_DISPLAY_LINES + 1).forEachIndexed { lineIndex, sourceLine ->
            if (lineIndex >= MAX_DISPLAY_LINES) {
                isTruncated = true
                return@forEachIndexed
            }
            if (sourceLine.truncated) isTruncated = true

            // The line sequence already bounds each substring, so even a single
            // malformed mega-line cannot allocate an equally large temporary copy.
            // Keep tabs and printable characters, but remove NUL and other control
            // bytes that can cause platform text renderers to behave unpredictably.
            val rawLine = sourceLine.value.filter { it == '\t' || it >= ' ' }
            val trimmed = rawLine.trim()
            val match = directive.matchEntire(trimmed)
            if (match != null) {
                val name = match.groupValues[1].lowercase()
                val value = match.groupValues[2].trim()
                when (name) {
                    "title", "t" -> title = value.takeIf(String::isNotBlank)
                    "artist", "subtitle", "st" -> if (name != "subtitle" && name != "st") {
                        artist = value.takeIf(String::isNotBlank)
                    }
                    "key" -> key = value.takeIf(String::isNotBlank)
                    "capo" -> capo = value.toIntOrNull()?.takeIf { it in 0..12 }
                    "tempo" -> {
                        val parsedTempo = Regex("\\d{1,3}").find(value)?.value?.toIntOrNull()
                        tempo = parsedTempo?.takeIf { it in 20..300 }
                    }
                    "time", "timesig", "time_signature" -> {
                        timeSignature = value.takeIf { timeSignaturePattern.matches(it) }
                    }
                    "sov", "start_of_verse" -> addSection("Verse", lines) { activeSection = it }
                    "soc", "start_of_chorus", "start_of_refrain" ->
                        addSection("Chorus", lines) { activeSection = it }
                    "sob", "start_of_bridge" -> addSection("Bridge", lines) { activeSection = it }
                    "start_of_pre_chorus" -> addSection("Pre-Chorus", lines) { activeSection = it }
                    "start_of_intro" -> addSection("Intro", lines) { activeSection = it }
                    "start_of_outro" -> addSection("Outro", lines) { activeSection = it }
                    "start_of_interlude" -> addSection("Interlude", lines) { activeSection = it }
                    "start_of_solo" -> addSection("Solo", lines) { activeSection = it }
                    "start_of_tab" -> addSection("Tab", lines) { activeSection = it }
                    "start_of_grid" -> addSection("Chord Grid", lines) { activeSection = it }
                    "start_of_instruction" -> addSection("Instruction", lines) { activeSection = it }
                    "start_of_section" -> addSection(value.ifBlank { "Section" }, lines) { activeSection = it }
                    "end_of_verse", "eov", "end_of_chorus", "eoc", "end_of_refrain",
                    "end_of_bridge", "end_of_pre_chorus", "end_of_intro", "end_of_outro",
                    "end_of_interlude", "end_of_solo", "end_of_tab", "end_of_grid",
                    "end_of_instruction", "end_of_section" -> activeSection = null
                    "comment", "c" -> if (value.isNotBlank()) {
                        lines += ChordProLine(value, section = activeSection, comment = true)
                    }
                    "new_page", "np", "new_song", "ns" -> Unit
                    else -> when {
                        name.matches(Regex("start_of_verse_\\d+")) ->
                            addSection("Verse", lines) { activeSection = it }
                        name.matches(Regex("start_of_(?:chorus|refrain)_\\d+")) ->
                            addSection("Chorus", lines) { activeSection = it }
                        name.matches(Regex("start_of_bridge_\\d+")) ->
                            addSection("Bridge", lines) { activeSection = it }
                        name.matches(Regex("start_of_pre_chorus_\\d+")) ->
                            addSection("Pre-Chorus", lines) { activeSection = it }
                        name.matches(Regex("start_of_(?:intro|outro|interlude|solo|tab|grid|instruction)_\\d+")) -> {
                            val label = name.substringAfter("start_of_").substringBefore('_')
                                .replaceFirstChar(Char::uppercase)
                            addSection(label, lines) { activeSection = it }
                        }
                        name.matches(Regex("start_of_section_\\d+")) ->
                            addSection(value.ifBlank { "Section" }, lines) { activeSection = it }
                        name.matches(
                            Regex("end_of_(?:verse|chorus|refrain|bridge|pre_chorus|intro|outro|interlude|solo|tab|grid|instruction|section)_\\d+")
                        ) -> activeSection = null
                    }
                }
            } else if (trimmed.isEmpty()) {
                lines += ChordProLine("")
            } else {
                val lyrics = StringBuilder()
                val chords = mutableListOf<ChordPosition>()
                var cursor = 0
                inlineChord.findAll(rawLine).forEach { token ->
                    val before = rawLine.substring(cursor, token.range.first)
                    lyrics.append(before)
                    val chord = token.groupValues[1].trim().take(MAX_CHORD_LABEL_CHARACTERS)
                    if (chord.isNotEmpty() && chords.size < MAX_CHORDS_PER_LINE) {
                        chords += ChordPosition(lyrics.length, chord)
                    }
                    cursor = token.range.last + 1
                }
                lyrics.append(rawLine.substring(cursor))
                lines += ChordProLine(lyrics.toString(), chords, activeSection)
            }
        }

        return ChordProDocument(
            title = title,
            artist = artist,
            key = key,
            lines = lines,
            tempo = tempo,
            timeSignature = timeSignature,
            capo = capo,
            isTruncated = isTruncated
        )
    }

    private inline fun addSection(
        label: String,
        lines: MutableList<ChordProLine>,
        updateActiveSection: (String) -> Unit
    ) {
        updateActiveSection(label)
        lines += ChordProLine("", section = label)
    }
}

object ChordTransposer {
    private val sharpNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flatNames = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val chordPattern = Regex("^([A-G])([#b]?)(.*?)(?:/([A-G])([#b]?))?$")

    fun transposeChord(chord: String, semitones: Int): String {
        if (semitones % 12 == 0) return chord
        val match = chordPattern.matchEntire(chord.trim()) ?: return chord
        val root = match.groupValues[1] + match.groupValues[2]
        val suffix = match.groupValues[3]
        val bassLetter = match.groupValues[4]
        val bassAccidental = match.groupValues[5]
        val preferFlats = root.contains('b') || (root.contains('#').not() && bassAccidental == "b")

        val transposedRoot = transposeNote(root, semitones, preferFlats)
        val bass = if (bassLetter.isNotEmpty()) {
            "/" + transposeNote(bassLetter + bassAccidental, semitones, preferFlats)
        } else {
            ""
        }
        return transposedRoot + suffix + bass
    }

    fun transposeKey(key: String?, semitones: Int): String? {
        if (key.isNullOrBlank() || semitones % 12 == 0) return key
        val match = Regex("^([A-G])([#b]?)(.*)$").matchEntire(key.trim()) ?: return key
        val note = match.groupValues[1] + match.groupValues[2]
        val suffix = match.groupValues[3]
        val preferFlats = note.contains('b')
        return transposeNote(note, semitones, preferFlats) + suffix
    }

    private fun transposeNote(note: String, semitones: Int, preferFlats: Boolean): String {
        val normalized = when (note) {
            "Cb" -> "B"
            "B#" -> "C"
            "E#" -> "F"
            "Fb" -> "E"
            else -> note
        }
        val pitch = when (normalized) {
            "C" -> 0
            "C#", "Db" -> 1
            "D" -> 2
            "D#", "Eb" -> 3
            "E" -> 4
            "F" -> 5
            "F#", "Gb" -> 6
            "G" -> 7
            "G#", "Ab" -> 8
            "A" -> 9
            "A#", "Bb" -> 10
            "B" -> 11
            else -> return note
        }
        val names = if (preferFlats) flatNames else sharpNames
        return names[Math.floorMod(pitch + semitones, 12)]
    }
}
