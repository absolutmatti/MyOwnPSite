package com.example.domain.model

enum class SortOrder(val label: String) {
    A_TO_Z("A–Z"),
    Z_TO_A("Z–A"),
}

enum class CatalogFilter(val label: String) {
    ALL("Alle"),
    FAVORITES("Favoriten"),
    WITHOUT_IMAGE("Ohne Bild"),
    WITH_IMAGE("Mit Bild"),
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
