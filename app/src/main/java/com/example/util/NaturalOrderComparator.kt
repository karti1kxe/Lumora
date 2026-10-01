package com.example.util

import java.util.Comparator

/**
 * Natural order comparator for strings that naturally orders numbers numerically
 * (e.g. Episode 1, Episode 2, ... Episode 9, Episode 10, Episode 11, Episode 12).
 */
object NaturalOrderComparator : Comparator<String> {

    override fun compare(a: String?, b: String?): Int {
        if (a == null && b == null) return 0
        if (a == null) return -1
        if (b == null) return 1

        var ia = 0
        var ib = 0
        val nzA: Int
        val nzB: Int
        var ca: Char
        var cb: Char

        val lengthA = a.length
        val lengthB = b.length

        while (true) {
            // Count and skip leading zeroes in chunks
            var nza = 0
            var nzb = 0

            ca = charAt(a, ia)
            cb = charAt(b, ib)

            // Skip spaces/symbols if needed, but natural walk handles chars directly
            while (ca.isWhitespace()) {
                ia++
                ca = charAt(a, ia)
            }
            while (cb.isWhitespace()) {
                ib++
                cb = charAt(b, ib)
            }

            // Process digits
            if (ca.isDigit() && cb.isDigit()) {
                if (ca == '0') {
                    while (charAt(a, ia) == '0') {
                        nza++
                        ia++
                    }
                }
                if (cb == '0') {
                    while (charAt(b, ib) == '0') {
                        nzb++
                        ib++
                    }
                }

                // Gather digits
                val startA = ia
                while (charAt(a, ia).isDigit()) {
                    ia++
                }
                val endA = ia
                val numDigitsA = endA - startA

                val startB = ib
                while (charAt(b, ib).isDigit()) {
                    ib++
                }
                val endB = ib
                val numDigitsB = endB - startB

                // Compare numeric lengths first
                if (numDigitsA != numDigitsB) {
                    return numDigitsA - numDigitsB
                }

                // If same number of digits, compare digit by digit
                for (k in 0 until numDigitsA) {
                    val da = a[startA + k]
                    val db = b[startB + k]
                    if (da != db) {
                        return da - db
                    }
                }

                // If numerical value is identical, prefer fewer leading zeros
                if (nza != nzb) {
                    return nza - nzb
                }

                continue
            }

            if (ca == '\u0000' && cb == '\u0000') {
                // The strings compare the same naturally. Compare case sensitively or by length.
                return lengthA - lengthB
            }

            if (ca == '\u0000') return -1
            if (cb == '\u0000') return 1

            val lca = ca.lowercaseChar()
            val lcb = cb.lowercaseChar()
            if (lca != lcb) {
                return lca - lcb
            }

            ia++
            ib++
        }
    }

    private fun charAt(s: String, i: Int): Char {
        return if (i >= s.length) '\u0000' else s[i]
    }
}
