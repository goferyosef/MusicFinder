package com.musicfinder

object MusicDetector {

    private val patterns: List<Triple<Regex, (MatchResult) -> Pair<String, String?>, Confidence>> = listOf(

        // HIGH: "Title" by Artist
        Triple(
            Regex("""[""'']([^""'']{2,60})[""'']\s+by\s+([A-Z][^\s,.!?;:]{1,40}(?:\s+[A-Z][^\s,.!?;:]{0,40})?)"""),
            { m -> Pair(m.groupValues[1], m.groupValues[2]) },
            Confidence.HIGH
        ),
        // HIGH: keyword + Title by Artist
        Triple(
            Regex("""(?:song|track|piece|aria|hymn|anthem|ballad|tune)\s+([A-Z][^,.!?;:\n]{2,50})\s+by\s+([A-Z][^\s,.!?;:]{1,40}(?:\s+[A-Z][^\s,.!?;:]{0,40})?)"""),
            { m -> Pair(m.groupValues[1].trim(), m.groupValues[2]) },
            Confidence.HIGH
        ),
        // HIGH: Artist's song/track "Title"
        Triple(
            Regex("""([A-Z][^\s,.!?;:]{1,30}(?:\s+[A-Z][^\s,.!?;:]{0,30})?)'s\s+(?:song|track|piece|aria)\s+[""'']([^""'']{2,60})[""'']"""),
            { m -> Pair(m.groupValues[2], m.groupValues[1]) },
            Confidence.HIGH
        ),
        // HIGH: unquoted "Artist - Title" or "Title - Artist" (common copy-paste format)
        Triple(
            Regex("""([A-Z][^–\-\n]{2,40})\s*[–\-]\s*([A-Z][^–\-\n]{2,40})"""),
            { m -> Pair(m.groupValues[1].trim(), m.groupValues[2].trim()) },
            Confidence.HIGH
        ),

        // MEDIUM: keyword called/titled "Title"
        Triple(
            Regex("""(?:song|track|piece|aria|hymn|anthem|ballad|tune)\s+(?:called|titled|named)\s+[""'']([^""'']{2,60})[""'']""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[1], null) },
            Confidence.MEDIUM
        ),
        // MEDIUM: played/sang/performed "Title"
        Triple(
            Regex("""(?:played|sang|performed|hummed|whistled|singing|playing)\s+[""'']([^""'']{2,60})[""'']""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[1], null) },
            Confidence.MEDIUM
        ),
        // MEDIUM: listening to "Title" / listening to Artist
        Triple(
            Regex("""listening\s+to\s+[""'']([^""'']{2,60})[""'']""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[1], null) },
            Confidence.MEDIUM
        ),
        Triple(
            Regex("""listening\s+to\s+([A-Z][^\s,.!?;:\n]{1,40}(?:\s+[A-Z][^\s,.!?;:]{0,30})?)"""),
            { m -> Pair(m.groupValues[1].trim(), null) },
            Confidence.MEDIUM
        ),
        // MEDIUM: the song/melody "Title"
        Triple(
            Regex("""the\s+(?:song|music|melody|tune|aria|hymn)\s+(?:of\s+)?[""'']([^""'']{2,60})[""'']""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[1], null) },
            Confidence.MEDIUM
        ),
        // MEDIUM: played/sang/performed ArtistName (unquoted)
        Triple(
            Regex("""(?:played|sang|performed|singing|playing|humming)\s+([A-Z][^\s,.!?;:\n]{1,30}(?:\s+[A-Z][^\s,.!?;:]{0,30})?)"""),
            { m -> Pair(m.groupValues[1].trim(), null) },
            Confidence.MEDIUM
        ),

        // LOW: classical form — Symphony/Concerto/Sonata No. N [by Artist]
        Triple(
            Regex("""(Symphony|Concerto|Sonata|Quartet|Quintet|Nocturne|Prelude|Étude|Etude|Fugue|Overture|Requiem|Mass)\s+(?:No\.\s*\d+|in\s+[A-G][^\s,]{0,10}|\d+)(?:[^,.\n]{0,40})?(?:\s+by\s+([A-Z][^\s,.!?;:]{1,30}(?:\s+[A-Z][^\s,.!?;:]{0,30})?))?""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[0].trim(), m.groupValues[2].ifBlank { null }) },
            Confidence.LOW
        ),
        // LOW: "Title", Op. N
        Triple(
            Regex("""[""'']([^""'']{2,60})[""'']\s*,?\s*Op\.\s*\d+""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[1], null) },
            Confidence.LOW
        ),
        // LOW: music/song by ArtistName (unquoted)
        Triple(
            Regex("""(?:music|song|track|piece)\s+by\s+([A-Z][^\s,.!?;:\n]{1,30}(?:\s+[A-Z][^\s,.!?;:]{0,30})?)""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[1].trim(), null) },
            Confidence.LOW
        ),
    )

    fun detect(text: String): List<MusicMention> {
        val sentences = text.split(Regex("""(?<=[.!?])\s+"""))
        val seen = mutableSetOf<String>()
        val results = mutableListOf<MusicMention>()

        for (sentence in sentences) {
            for ((regex, extractor, confidence) in patterns) {
                for (match in regex.findAll(sentence)) {
                    val (title, artist) = extractor(match)
                    val normalizedTitle = title.trim().lowercase()
                    if (normalizedTitle.isBlank() || normalizedTitle in seen) continue
                    seen.add(normalizedTitle)

                    val query = buildString {
                        append(title.trim())
                        if (!artist.isNullOrBlank()) append(" ${artist.trim()}")
                    }

                    results.add(
                        MusicMention(
                            title = title.trim(),
                            artist = artist?.trim()?.ifBlank { null },
                            context = sentence.trim().take(120),
                            searchQuery = query,
                            confidence = confidence
                        )
                    )
                }
            }
        }

        return results.sortedBy { it.confidence.ordinal }.take(4)
    }
}
