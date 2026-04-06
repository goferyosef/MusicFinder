package com.musicfinder

object MusicDetector {

    // Each pattern: Pair(regex, lambda that extracts (title, artist?) from MatchResult)
    private val patterns: List<Pair<Regex, (MatchResult) -> Pair<String, String?>>> = listOf(

        // "Title" by Artist  |  'Title' by Artist
        Pair(
            Regex("""[""'']([^""'']{2,60})[""'']\s+by\s+([A-Z][^\s,.!?;:]{1,40}(?:\s+[A-Z][^\s,.!?;:]{0,40})?)"""),
            { m -> Pair(m.groupValues[1], m.groupValues[2]) }
        ),

        // Title by Artist (unquoted, title starts with capital)
        Pair(
            Regex("""(?:song|track|piece|aria|hymn|anthem|ballad|tune)\s+([A-Z][^,.!?;:\n]{2,50})\s+by\s+([A-Z][^\s,.!?;:]{1,40}(?:\s+[A-Z][^\s,.!?;:]{0,40})?)"""),
            { m -> Pair(m.groupValues[1].trim(), m.groupValues[2]) }
        ),

        // song/track/piece called "Title"  |  called 'Title'
        Pair(
            Regex("""(?:song|track|piece|aria|hymn|anthem|ballad|tune)\s+(?:called|titled|named)\s+[""'']([^""'']{2,60})[""'']""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[1], null) }
        ),

        // Artist's song/track "Title"
        Pair(
            Regex("""([A-Z][^\s,.!?;:]{1,30}(?:\s+[A-Z][^\s,.!?;:]{0,30})?)'s\s+(?:song|track|piece|aria)\s+[""'']([^""'']{2,60})[""'']"""),
            { m -> Pair(m.groupValues[2], m.groupValues[1]) }
        ),

        // played/sang/performed/hummed/whistled "Title"
        Pair(
            Regex("""(?:played|sang|performed|hummed|whistled|singing|playing|performed)\s+[""'']([^""'']{2,60})[""'']""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[1], null) }
        ),

        // listening to "Title"  |  listening to Title by Artist
        Pair(
            Regex("""listening\s+to\s+[""'']([^""'']{2,60})[""'']""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[1], null) }
        ),

        // Symphony/Concerto/Sonata/Quartet No. N [in X] [by Artist]
        Pair(
            Regex("""(Symphony|Concerto|Sonata|Quartet|Quintet|Nocturne|Prelude|Étude|Etude|Fugue|Overture|Requiem|Mass)\s+(?:No\.\s*\d+|in\s+[A-G][^\s,]{0,10}|\d+)(?:[^,.\n]{0,40})?(?:\s+by\s+([A-Z][^\s,.!?;:]{1,30}(?:\s+[A-Z][^\s,.!?;:]{0,30})?))?""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[0].trim(), m.groupValues[2].ifBlank { null }) }
        ),

        // Op. N, No. N  (Opus references, e.g. "Op. 27, No. 2 by Beethoven")
        Pair(
            Regex("""[""'']([^""'']{2,60})[""'']\s*,?\s*Op\.\s*\d+""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[1], null) }
        ),

        // the song/music/melody of Title (unquoted)
        Pair(
            Regex("""the\s+(?:song|music|melody|tune|aria|hymn)\s+(?:of\s+)?[""'']([^""'']{2,60})[""'']""", RegexOption.IGNORE_CASE),
            { m -> Pair(m.groupValues[1], null) }
        ),
    )

    fun detect(text: String): List<MusicMention> {
        val sentences = text.split(Regex("""(?<=[.!?])\s+"""))
        val seen = mutableSetOf<String>()
        val results = mutableListOf<MusicMention>()

        for (sentence in sentences) {
            for ((regex, extractor) in patterns) {
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
                            searchQuery = query
                        )
                    )
                }
            }
        }

        return results
    }
}
