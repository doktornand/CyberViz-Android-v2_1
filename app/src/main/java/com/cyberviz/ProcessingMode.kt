package com.cyberviz

enum class ProcessingMode(
    val displayName: String,
    val paramLabel: String,
    val paramMin: Float,
    val paramMax: Float,
    val paramDefault: Float,
    val paramStep: Float,
    val category: ModeCategory,
    val clinicalTags: List<String> = emptyList()
) {
    RAW("RAW", "-", 0f, 0f, 0f, 0f, ModeCategory.BASE),
    LUMINOSITE("LUMINOSITE", "expo", 0f, 100f, 50f, 5f, ModeCategory.BASE),
    CONTRASTE("CONTRASTE", "force", 0f, 100f, 50f, 5f, ModeCategory.BASE),
    GAMMA("GAMMA", "gamma", 0.5f, 5f, 1f, 0.2f, ModeCategory.BASE),
    HI_CONTRAST("HI-CONTRAST", "seuil", 50f, 100f, 70f, 5f, ModeCategory.ACCESSIBILITY, listOf("basse acuite")),
    CONTOURS_P("CONTOURS+", "force", 1f, 20f, 5f, 1f, ModeCategory.ACCESSIBILITY, listOf("basse acuite")),
    CONTOURS("CONTOURS", "seuil", 5f, 60f, 20f, 5f, ModeCategory.ACCESSIBILITY),
    NEGATIF("NEGATIF", "-", 0f, 0f, 0f, 0f, ModeCategory.ACCESSIBILITY, listOf("photophobie")),
    GRIS("GRIS", "-", 0f, 0f, 0f, 0f, ModeCategory.ACCESSIBILITY),
    SEUIL("SEUIL", "seuil", 10f, 245f, 128f, 10f, ModeCategory.ACCESSIBILITY),
    PAL_CHAUD("PAL CHAUD", "-", 0f, 0f, 0f, 0f, ModeCategory.COLORBLIND, listOf("deuteranopie")),
    PAL_FROID("PAL FROID", "-", 0f, 0f, 0f, 0f, ModeCategory.COLORBLIND, listOf("protanopie")),
    PAL_FEU("PAL FEU", "-", 0f, 0f, 0f, 0f, ModeCategory.COLORBLIND),
    PAL_GLACE("PAL GLACE", "-", 0f, 0f, 0f, 0f, ModeCategory.COLORBLIND),
    PAL_NEON("PAL NEON", "-", 0f, 0f, 0f, 0f, ModeCategory.COLORBLIND),
    DEUTAN("DEUTAN", "-", 0f, 0f, 0f, 0f, ModeCategory.COLORBLIND, listOf("deuteranopie")),
    PROTAN("PROTAN", "-", 0f, 0f, 0f, 0f, ModeCategory.COLORBLIND, listOf("protanopie")),
    ZOOM_2X("ZOOM x2", "-", 0f, 0f, 0f, 0f, ModeCategory.ZOOM, listOf("DMLA")),
    ZOOM_4X("ZOOM x4", "-", 0f, 0f, 0f, 0f, ModeCategory.ZOOM, listOf("DMLA")),
    POSTER("POSTER", "niveaux", 2f, 8f, 4f, 1f, ModeCategory.CREATIVE),
    RELIEF("RELIEF", "-", 0f, 0f, 0f, 0f, ModeCategory.CREATIVE),
    FLOU("FLOU", "-", 0f, 0f, 0f, 0f, ModeCategory.CREATIVE),
    NETTETE("NETTETE", "force", 5f, 20f, 10f, 1f, ModeCategory.CREATIVE),
    NUIT("NUIT", "gain", 20f, 100f, 50f, 5f, ModeCategory.CREATIVE),
    CROQUIS("CROQUIS", "seuil", 10f, 80f, 30f, 5f, ModeCategory.CREATIVE),
    THERMIQUE("THERMIQUE", "-", 0f, 0f, 0f, 0f, ModeCategory.CREATIVE),
    TRITAN("TRITAN", "-", 0f, 0f, 0f, 0f, ModeCategory.COLORBLIND, listOf("tritanopie")),
    ACHROMATOPSIE("ACHROMATOPSIE", "-", 0f, 0f, 0f, 0f, ModeCategory.PHOTOSENSITIVITY),
    ANTI_EBLOUISSEMENT("ANTI-EBLOUIS.", "force", 0f, 100f, 50f, 5f, ModeCategory.PHOTOSENSITIVITY),
    DEJAUNISSEMENT("DEJAUNISSEMENT", "intens.", 0f, 100f, 60f, 5f, ModeCategory.PHOTOSENSITIVITY),
    ANTI_HALO("ANTI-HALO", "seuil", 20f, 150f, 60f, 10f, ModeCategory.PHOTOSENSITIVITY),
    CONTRASTE_LOCAL("CONTR. LOCAL", "force", 0f, 100f, 60f, 10f, ModeCategory.FIELD_OF_VIEW),
    CHAMP_LARGE("CHAMP LARGE", "taille", 30f, 90f, 60f, 5f, ModeCategory.FIELD_OF_VIEW),
    HEMI_DROIT("HEMI DROIT", "-", 0f, 0f, 0f, 0f, ModeCategory.FIELD_OF_VIEW),
    HEMI_GAUCHE("HEMI GAUCHE", "-", 0f, 0f, 0f, 0f, ModeCategory.FIELD_OF_VIEW),
    EXCENTRATION("EXCENTRATION", "angle", 0f, 350f, 0f, 10f, ModeCategory.FIELD_OF_VIEW),
    NYCTALOPIE_PLUS("NYCTALOPIE+", "gain", 20f, 100f, 50f, 5f, ModeCategory.LOW_LIGHT),
    STABILISATION("STABILISATION", "lissage", 0f, 90f, 40f, 10f, ModeCategory.MOTOR),
    LECTURE("LECTURE", "sensib.", 20f, 100f, 60f, 10f, ModeCategory.OCR, listOf("DMLA", "basse acuite")),
    MONO_OCCLUSION("MONO OCCLUS.", "altern.", 0f, 100f, 50f, 10f, ModeCategory.MOTOR, listOf("diplopie")),
    MELANOPIE("MELANOPIE", "-", 0f, 0f, 0f, 0f, ModeCategory.PHOTOSENSITIVITY),
    ASTIGMATISME("ASTIGMATISME", "axe", 0f, 180f, 90f, 10f, ModeCategory.ACCESSIBILITY),
    ALBINISME("ALBINISME", "-", 0f, 0f, 0f, 0f, ModeCategory.PHOTOSENSITIVITY);

    fun hasParam() = paramMin < paramMax
    companion object {
        fun byPathology(tag: String) = values().filter { it.clinicalTags.any { t -> t.contains(tag, true) } }
    }
}

enum class ModeCategory { BASE, ACCESSIBILITY, COLORBLIND, ZOOM, CREATIVE, PHOTOSENSITIVITY, FIELD_OF_VIEW, LOW_LIGHT, MOTOR, OCR }
