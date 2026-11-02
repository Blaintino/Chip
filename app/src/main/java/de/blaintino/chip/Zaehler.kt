package de.blaintino.chip

/** Reine Logik ohne Android-Abhängigkeit – damit per Unit-Test prüfbar. */
fun naechsterStand(stand: Int): Int = stand + 1

fun beschriftung(stand: Int): String = when (stand) {
    0 -> "Noch nicht getippt"
    1 -> "1 Mal getippt"
    else -> "$stand Mal getippt"
}
