package com.example.domain.model

enum class SortOrder(val label: String) {
    ALPHA_ASC("Alphabet A–Z"),
    ALPHA_DESC("Alphabet Z–A"),
    VIDEOS_DESC("Meiste Videos"),
    VIDEOS_ASC("Wenigste Videos"),
}

enum class AlphabetBucket(val label: String, val range: CharRange?) {
    ALL("Alle", null),
    A_E("A–E", 'A'..'E'),
    F_J("F–J", 'F'..'J'),
    K_O("K–O", 'K'..'O'),
    P_T("P–T", 'P'..'T'),
    U_Z("U–Z", 'U'..'Z');

    fun matches(name: String): Boolean {
        val allowed = range ?: return true
        val firstChar = name.trim().firstOrNull()?.uppercaseChar() ?: return false
        return firstChar in allowed
    }
}
