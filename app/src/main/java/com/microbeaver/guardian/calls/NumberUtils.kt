package com.microbeaver.guardian.calls

/**
 * Phone-number comparison helpers.
 *
 * Numbers reach us in wildly different shapes for the same person —
 * `0945830002`, `+963945830002`, `00963 945 830 002`, `963-945-830-002`.
 * Rather than depend on a full libphonenumber parse, we compare the last
 * [SIGNIFICANT_DIGITS] digits, which is what dialers themselves do.
 */
object NumberUtils {

    /** Comparing this many trailing digits avoids both false matches and country-code noise. */
    const val SIGNIFICANT_DIGITS = 9

    /** Strips everything that is not a digit. */
    fun digitsOnly(raw: String?): String =
        raw?.filter { it.isDigit() } ?: ""

    /**
     * A comparison key: the trailing significant digits.
     * Short numbers (service codes, 4-digit shortcodes) keep all their digits.
     */
    fun key(raw: String?): String {
        val d = digitsOnly(raw)
        return if (d.length <= SIGNIFICANT_DIGITS) d else d.takeLast(SIGNIFICANT_DIGITS)
    }

    /** True when the two numbers are the same line, regardless of formatting. */
    fun sameNumber(a: String?, b: String?): Boolean {
        val ka = key(a)
        val kb = key(b)
        return ka.isNotEmpty() && ka == kb
    }

    /** True when [number] matches any entry in [list]. */
    fun matchesAny(number: String?, list: Collection<String>): Boolean {
        val k = key(number)
        if (k.isEmpty()) return false
        return list.any { key(it) == k }
    }

    /**
     * Withheld / unavailable caller ID. These carry no digits at all, so they
     * can never be matched against an allow-list.
     */
    fun isWithheld(raw: String?): Boolean = digitsOnly(raw).isEmpty()

    /** Pretty form for notifications: keeps the original if it already looks formatted. */
    fun display(raw: String?): String =
        if (raw.isNullOrBlank()) "رقم محجوب / Withheld" else raw
}
