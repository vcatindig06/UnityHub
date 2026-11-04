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
    val lines: List<ChordProLine>
)

object ChordProParser {
    private val inlineChord = Regex("\\[([^\\]]+)]")
    private val directive = Regex("^\\{\\s*([a-zA-Z0-9_]+)\\s*(?::\\s*(.*?))?\\s*}$")

    fun parse(source: String): ChordProDocument {
        var title: String? = null
        var artist: String? = null
        var key: String? = null
        var activeSection: String? = null
        val lines = mutableListOf<ChordProLine>()

        source.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            val match = directive.matchEntire(trimmed)
            if (match != null) {
                val name = match.groupValues[1].lowercase()
                val value = match.groupValues[2].trim()
                when (name) {
                    "title", "t" -> title = value.takeIf(String::isNotBlank)
                    "artist", "subtitle", "st" -> if (name != "subtitle" && name != "st") artist = value.takeIf(String::isNotBlank)
                    "key" -> key = value.takeIf(String::isNotBlank)
                    "start_of_verse", "sov" -> {
                        activeSection = "Verse"
                        lines += ChordProLine("", section = activeSection)
                    }
                    "start_of_chorus", "soc" -> {
                        activeSection = "Chorus"
                        lines += ChordProLine("", section = activeSection)
                    }
                    "start_of_bridge" -> {
                        activeSection = "Bridge"
                        lines += ChordProLine("", section = activeSection)
                    }
                    "start_of_pre_chorus" -> {
                        activeSection = "Pre-Chorus"
                        lines += ChordProLine("", section = activeSection)
                    }
                    "end_of_verse", "eov", "end_of_chorus", "eoc", "end_of_bridge",
                    "end_of_pre_chorus", "end_of_tab", "end_of_grid" -> activeSection = null
                    "comment", "c" -> if (value.isNotBlank()) {
                        lines += ChordProLine(value, section = activeSection, comment = true)
                    }
                    "new_page", "np", "new_song", "ns" -> Unit
                    else -> Unit
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
                    val chord = token.groupValues[1].trim()
                    if (chord.isNotEmpty()) chords += ChordPosition(lyrics.length, chord)
                    cursor = token.range.last + 1
                }
                lyrics.append(rawLine.substring(cursor))
                lines += ChordProLine(lyrics.toString(), chords, activeSection)
            }
        }

        return ChordProDocument(title, artist, key, lines)
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
