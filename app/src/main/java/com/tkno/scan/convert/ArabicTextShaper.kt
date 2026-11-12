package com.tkno.scan.convert

import java.text.Bidi

/**
 * Pure Kotlin Arabic contextual letter shaper + Bidi visual line converter.
 *
 * Converts logical Arabic Unicode text into Arabic Presentation Forms-B glyphs
 * (including Lam-Alef ligatures) and applies `java.text.Bidi` run reordering per
 * wrapped visual line so low-level PDF streams render connected RTL/mixed text cleanly.
 */
object ArabicTextShaper {

    private data class CharForms(
        val isolated: Char,
        val initial: Char?, // null for right-joining-only characters
        val medial: Char?,
        val finalForm: Char
    ) {
        val canJoinNext: Boolean get() = initial != null && medial != null
    }

    private val arabicForms = hashMapOf(
        '\u0621' to CharForms('\uFE80', null, null, '\uFE80'), // HAMZA
        '\u0622' to CharForms('\uFE81', null, null, '\uFE82'), // ALEF WITH MADDA
        '\u0623' to CharForms('\uFE83', null, null, '\uFE84'), // ALEF WITH HAMZA ABOVE
        '\u0624' to CharForms('\uFE85', null, null, '\uFE86'), // WAW WITH HAMZA ABOVE
        '\u0625' to CharForms('\uFE87', null, null, '\uFE88'), // ALEF WITH HAMZA BELOW
        '\u0626' to CharForms('\uFE89', '\uFE8B', '\uFE8C', '\uFE8A'), // YEH WITH HAMZA ABOVE
        '\u0627' to CharForms('\uFE8D', null, null, '\uFE8E'), // ALEF
        '\u0628' to CharForms('\uFE8F', '\uFE91', '\uFE92', '\uFE90'), // BEH
        '\u0629' to CharForms('\uFE93', null, null, '\uFE94'), // TEH MARBUTA
        '\u062A' to CharForms('\uFE95', '\uFE97', '\uFE98', '\uFE96'), // TEH
        '\u062B' to CharForms('\uFE99', '\uFE9B', '\uFE9C', '\uFE9A'), // THEH
        '\u062C' to CharForms('\uFE9D', '\uFE9F', '\uFEA0', '\uFE9E'), // JEEM
        '\u062D' to CharForms('\uFEA1', '\uFEA3', '\uFEA4', '\uFEA2'), // HAH
        '\u062E' to CharForms('\uFEA5', '\uFEA7', '\uFEA8', '\uFEA6'), // KHAH
        '\u062F' to CharForms('\uFEA9', null, null, '\uFEAA'), // DAL
        '\u0630' to CharForms('\uFEAB', null, null, '\uFEAC'), // THAL
        '\u0631' to CharForms('\uFEAD', null, null, '\uFEAE'), // REH
        '\u0632' to CharForms('\uFEAF', null, null, '\uFEB0'), // ZAIN
        '\u0633' to CharForms('\uFEB1', '\uFEB3', '\uFEB4', '\uFEB2'), // SEEN
        '\u0634' to CharForms('\uFEB5', '\uFEB7', '\uFEB8', '\uFEB6'), // SHEEN
        '\u0635' to CharForms('\uFEB9', '\uFEBB', '\uFEBC', '\uFEBA'), // SAD
        '\u0636' to CharForms('\uFEBD', '\uFEBF', '\uFEC0', '\uFEBE'), // DAD
        '\u0637' to CharForms('\uFEC1', '\uFEC3', '\uFEC4', '\uFEC2'), // TAH
        '\u0638' to CharForms('\uFEC5', '\uFEC7', '\uFEC8', '\uFEC6'), // ZAH
        '\u0639' to CharForms('\uFEC9', '\uFECB', '\uFECC', '\uFECA'), // AIN
        '\u063A' to CharForms('\uFECD', '\uFECF', '\uFED0', '\uFECE'), // GHAIN
        '\u0640' to CharForms('\u0640', '\u0640', '\u0640', '\u0640'), // TATWEEL
        '\u0641' to CharForms('\uFED1', '\uFED3', '\uFED4', '\uFED2'), // FEH
        '\u0642' to CharForms('\uFED5', '\uFED7', '\uFED8', '\uFED6'), // QAF
        '\u0643' to CharForms('\uFED9', '\uFEDB', '\uFEDC', '\uFEDA'), // KAF
        '\u0644' to CharForms('\uFEDD', '\uFEDF', '\uFEE0', '\uFEDE'), // LAM
        '\u0645' to CharForms('\uFEE1', '\uFEE3', '\uFEE4', '\uFEE2'), // MEEM
        '\u0646' to CharForms('\uFEE5', '\uFEE7', '\uFEE8', '\uFEE6'), // NOON
        '\u0647' to CharForms('\uFEE9', '\uFEEB', '\uFEEC', '\uFEEA'), // HEH
        '\u0648' to CharForms('\uFEED', null, null, '\uFEEE'), // WAW
        '\u0649' to CharForms('\uFEEF', null, null, '\uFEF0'), // ALEF MAKSURA
        '\u064A' to CharForms('\uFEF1', '\uFEF3', '\uFEF4', '\uFEF2'), // YEH
        // Persian / extended letters
        '\u067E' to CharForms('\uFB56', '\uFB58', '\uFB59', '\uFB57'), // PEH
        '\u0686' to CharForms('\uFB7A', '\uFB7C', '\uFB7D', '\uFB7B'), // TCHEH
        '\u0698' to CharForms('\uFB8A', null, null, '\uFB8B'), // JEH
        '\u06A9' to CharForms('\uFB8E', '\uFB90', '\uFB91', '\uFB8F'), // KEHEH
        '\u06AF' to CharForms('\uFB92', '\uFB94', '\uFB95', '\uFB93'), // GAF
        '\u06CC' to CharForms('\uFBFC', '\uFBFE', '\uFBFF', '\uFBFD')  // FARSI YEH
    )

    private fun isTransparentDiacritic(ch: Char): Boolean {
        return ch in '\u064B'..'\u065F' || ch == '\u0670' || ch in '\u06D6'..'\u06ED'
    }

    private fun lamAlefLigature(alef: Char, joinPrev: Boolean): Char? {
        return when (alef) {
            '\u0622' -> if (joinPrev) '\uFEF6' else '\uFEF5' // لآ
            '\u0623' -> if (joinPrev) '\uFEF8' else '\uFEF7' // لأ
            '\u0625' -> if (joinPrev) '\uFEFA' else '\uFEF9' // لإ
            '\u0627' -> if (joinPrev) '\uFEFC' else '\uFEFB' // لا
            else -> null
        }
    }

    /**
     * Shapes Arabic letters into Presentation Forms in logical order so line wrapping
     * can measure exact glyph widths before visual Bidi reordering.
     */
    fun shapeLogical(input: String): String {
        if (input.isEmpty()) return input

        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            val currentForm = arabicForms[ch]
            if (currentForm == null) {
                out.append(ch)
                i++
                continue
            }

            // Look backward past any transparent diacritics to check if previous letter connects left
            var prevJoinNext = false
            var b = i - 1
            while (b >= 0 && isTransparentDiacritic(input[b])) b--
            if (b >= 0) {
                prevJoinNext = arabicForms[input[b]]?.canJoinNext == true
            }

            // Check for Lam + (optional transparent marks) + Alef ligature
            if (ch == '\u0644') {
                var n = i + 1
                while (n < input.length && isTransparentDiacritic(input[n])) n++
                if (n < input.length) {
                    val ligature = lamAlefLigature(input[n], prevJoinNext)
                    if (ligature != null) {
                        out.append(ligature)
                        // Preserve any intermediate combining mark after the ligature if needed
                        for (m in (i + 1) until n) {
                            out.append(input[m])
                        }
                        i = n + 1
                        continue
                    }
                }
            }

            // Look forward past any transparent diacritics to see if next char accepts right-side joining
            var nextJoinPrev = false
            var f = i + 1
            while (f < input.length && isTransparentDiacritic(input[f])) f++
            if (f < input.length) {
                nextJoinPrev = arabicForms.containsKey(input[f])
            }

            val shapedChar = when {
                prevJoinNext && nextJoinPrev && currentForm.medial != null -> currentForm.medial
                prevJoinNext -> currentForm.finalForm
                nextJoinPrev && currentForm.initial != null -> currentForm.initial
                else -> currentForm.isolated
            }
            out.append(shapedChar)
            i++
        }
        return out.toString()
    }

    /**
     * Returns true if the paragraph contains strong RTL characters before strong LTR characters.
     */
    fun isRtlParagraph(text: String): Boolean {
        for (ch in text) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
            }
        }
        return false
    }

    private fun mirrorBracket(ch: Char): Char = when (ch) {
        '(' -> ')'
        ')' -> '('
        '[' -> ']'
        ']' -> '['
        '{' -> '}'
        '}' -> '{'
        '<' -> '>'
        '>' -> '<'
        '«' -> '»'
        '»' -> '«'
        else -> ch
    }

    /**
     * Converts a single already-wrapped, logically shaped line into left-to-right visual order
     * for direct drawing into a PDF content stream.
     */
    fun toVisualOrder(shapedLogicalLine: String, baseRtl: Boolean): String {
        if (shapedLogicalLine.isEmpty()) return shapedLogicalLine
        val hasRtlChar = shapedLogicalLine.any { ch ->
            val dir = Character.getDirectionality(ch)
            dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
        }
        if (!hasRtlChar && !baseRtl) return shapedLogicalLine

        val flags = if (baseRtl) Bidi.DIRECTION_RIGHT_TO_LEFT else Bidi.DIRECTION_LEFT_TO_RIGHT
        val bidi = Bidi(shapedLogicalLine, flags)
        val runCount = bidi.runCount
        if (runCount == 0) return shapedLogicalLine

        val levels = ByteArray(runCount)
        val runs = Array(runCount) { "" }
        for (r in 0 until runCount) {
            val level = bidi.getRunLevel(r)
            levels[r] = level.toByte()
            val slice = shapedLogicalLine.substring(bidi.getRunStart(r), bidi.getRunLimit(r))
            runs[r] = if ((level and 1) == 1) {
                // Reverse RTL run for LTR PDF canvas rendering and mirror paired punctuation
                val sb = StringBuilder(slice.length)
                for (idx in slice.length - 1 downTo 0) {
                    sb.append(mirrorBracket(slice[idx]))
                }
                sb.toString()
            } else {
                slice
            }
        }

        Bidi.reorderVisually(levels, 0, runs, 0, runCount)
        return buildString(shapedLogicalLine.length) {
            for (run in runs) append(run)
        }
    }
}
