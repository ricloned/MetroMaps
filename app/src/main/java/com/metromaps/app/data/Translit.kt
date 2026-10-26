package com.metromaps.app.data

/** Транслитерация названий станций для «латинской» схемы. */
object Translit {
    private val map = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    fun lat(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            val low = ch.lowercaseChar()
            val t = map[low]
            if (t == null) { sb.append(ch); continue }
            if (ch.isUpperCase() && t.isNotEmpty()) sb.append(t.replaceFirstChar { it.uppercaseChar() }) else sb.append(t)
        }
        return sb.toString()
    }
}
