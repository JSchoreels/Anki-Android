/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.libanki.template

object MathJax {
    // MathJax opening delimiters
    private val sMathJaxOpenings = arrayOf("\\(", "\\[")

    // MathJax closing delimiters
    private val sMathJaxClosings = arrayOf("\\)", "\\]")

    // MathJax never typesets inside these; a template's regexes (/\[\d*\]+/) looked like math and loaded
    // MathJax on every card
    private val scriptOrStyle = Regex("<(script|style)\\b.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun textContainsMathjax(html: String): Boolean {
        val txt = html.replace(scriptOrStyle, "")
        // Do you have the first opening and then the first closing,
        // or the second opening and the second closing...?

        // This assumes that the openings and closings are the same length.
        var opening: String
        var closing: String
        for (i in sMathJaxOpenings.indices) {
            opening = sMathJaxOpenings[i]
            closing = sMathJaxClosings[i]

            // What if there are more than one thing?
            // Let's look for the first opening, and the last closing, and if they're in the right order,
            // we are good.
            val firstOpeningIndex = txt.indexOf(opening)
            val lastClosingIndex = txt.lastIndexOf(closing)
            if (firstOpeningIndex != -1 && lastClosingIndex != -1 && firstOpeningIndex < lastClosingIndex) {
                return true
            }
        }
        return false
    }
}
