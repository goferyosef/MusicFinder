package com.musicfinder

object TextCleaner {

    /**
     * Cleans text shared from browsers, PDF readers, or Libby before it is
     * sent to Gemini or MusicDetector.  Removes URLs, HTML tags, markdown
     * links, and other noise that can confuse detection.
     */
    fun clean(raw: String): String = raw
        .replace(Regex("""https?://\S+"""), "")              // remove URLs
        .replace(Regex("""www\.\S+"""), "")                  // remove bare www. links
        .replace(Regex("""<[^>]{0,200}>"""), " ")            // strip HTML tags
        .replace(Regex("""\[[^\]]{0,100}]\([^)]{0,200}\)"""), "") // strip markdown [text](url)
        .replace(Regex("""[^\p{L}\p{N}\p{P}\s]"""), " ")    // remove non-printable / control chars
        .replace(Regex("""\s{2,}"""), " ")                   // collapse whitespace
        .lines()
        .map { it.trim() }
        .filter { it.length > 2 }                            // drop one/two-char noise lines
        .joinToString(" ")
        .trim()
        .take(500)
}
