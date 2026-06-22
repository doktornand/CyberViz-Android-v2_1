
package com.cyberviz

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.*

/**
 * ImageProcessor v2.0
 * 
 * Corrections par rapport a v1.0 :
 * - STABILISATION : alpha = (1f - param/100f) au lieu de 111
 * - STABILISATION : ThreadLocal pour eviter fuites memoire/concurrence
 * - CONTRASTE_LOCAL : tuile adaptative + interpolation bilineaire
 * - EXCENTRATION : Math.toRadians remplace par (param * PI / 180f)
 * - applyContoursPlus : verification overflow force max
 * 
 * Ajouts v2.0 :
 * - Mode LECTURE (detection zones texte + binarisation)
 * - Mode MONO_OCCLUSION (diplopie)
 * - Mode MELANOPIE (ipRGC)
 * - Mode ASTIGMATISME (compensation directionnelle)
 * - Mode ALBINISME (combinaison optimisee)
 */
object ImageProcessor {

    private val gammaTable = Array(256) { IntArray(256) }
    private var lastGamma = -1f
    private lateinit var currentGammaLut: IntArray

    private val lutFeu: IntArray    by lazy { buildLutFeu() }
    private val lutGlace: IntArray  by lazy { buildLutGlace() }
    private val lutNeon: IntArray   by lazy { buildLutNeon() }
    private val lutJet: IntArray    by lazy { buildLutJet() }
    private val lutAchroma: IntArray by lazy { buildLutAchroma() }

    private val prevFrameLocal = ThreadLocal<IntArray>()
    private var lastTileConfig: Pair<Int, Int>? = null
    private var tileMinCache: IntArray? = null
    private var tileMaxCache: IntArray? = null

    fun process(
        src: IntArray, dst: IntArray,
        width: Int, height: Int,
        mode: ProcessingMode, param: Float
    ) {
        when (mode) {
            ProcessingMode.RAW        -> src.copyInto(dst)
            ProcessingMode.LUMINOSITE -> applyLuminosite(src, dst, width * height, param)
            ProcessingMode.CONTRASTE  -> applyContraste(src, dst, width * height, param)
            ProcessingMode.GAMMA      -> applyGamma(src, dst, width * height, param)
            ProcessingMode.HI_CONTRAST-> applyHiContrast(src, dst, width * height, param)
            ProcessingMode.CONTOURS_P -> applyContoursPlus(src, dst, width, height, param)
            ProcessingMode.CONTOURS   -> applyContours(src, dst, width, height, param)
            ProcessingMode.NEGATIF    -> applyNegatif(src, dst, width * height)
            ProcessingMode.GRIS       -> applyGris(src, dst, width * height)
            ProcessingMode.SEUIL      -> applySeuil(src, dst, width * height, param)
            ProcessingMode.PAL_CHAUD  -> applyPalChaud(src, dst, width * height)
            ProcessingMode.PAL_FROID  -> applyPalFroid(src, dst, width * height)
            ProcessingMode.PAL_FEU    -> applyLut(src, dst, width * height, lutFeu)
            ProcessingMode.PAL_GLACE  -> applyLut(src, dst, width * height, lutGlace)
            ProcessingMode.PAL_NEON   -> applyLut(src, dst, width * height, lutNeon)
            ProcessingMode.DEUTAN     -> applyDeutan(src, dst, width * height)
            ProcessingMode.PROTAN     -> applyProtan(src, dst, width * height)
            ProcessingMode.ZOOM_2X    -> applyZoom(src, dst, width, height, 2)
            ProcessingMode.ZOOM_4X    -> applyZoom(src, dst, width, height, 4)
            ProcessingMode.POSTER     -> applyPoster(src, dst, width * height, param.toInt())
            ProcessingMode.RELIEF     -> applyRelief(src, dst, width, height)
            ProcessingMode.FLOU       -> applyFlou(src, dst, width, height)
            ProcessingMode.NETTETE    -> applyNettete(src, dst, width, height, param)
            ProcessingMode.NUIT       -> applyNuit(src, dst, width * height, param)
            ProcessingMode.CROQUIS    -> applyCroquis(src, dst, width, height, param)
            ProcessingMode.THERMIQUE  -> applyLut(src, dst, width * height, lutJet)
            ProcessingMode.TRITAN              -> applyTritan(src, dst, width * height)
            ProcessingMode.ACHROMATOPSIE       -> applyAchromatopsie(src, dst, width * height)
            ProcessingMode.ANTI_EBLOUISSEMENT  -> applyAntiEblouissement(src, dst, width * height, param)
            ProcessingMode.DEJAUNISSEMENT      -> applyDejaunissement(src, dst, width * height, param)
            ProcessingMode.ANTI_HALO           -> applyAntiHalo(src, dst, width, height, param)
            ProcessingMode.CONTRASTE_LOCAL     -> applyContrasteLocalV2(src, dst, width, height, param)
            ProcessingMode.CHAMP_LARGE         -> applyChampLarge(src, dst, width, height, param)
            ProcessingMode.HEMI_DROIT          -> applyHemiDroit(src, dst, width, height)
            ProcessingMode.HEMI_GAUCHE         -> applyHemiGauche(src, dst, width, height)
            ProcessingMode.EXCENTRATION        -> applyExcentrationV2(src, dst, width, height, param)
            ProcessingMode.NYCTALOPIE_PLUS     -> applyNyctalopiePlus(src, dst, width, height, param)
            ProcessingMode.STABILISATION       -> applyStabilisationV2(src, dst, width * height, param)
            ProcessingMode.LECTURE         -> applyLecture(src, dst, width, height, param)
            ProcessingMode.MONO_OCCLUSION  -> applyMonoOcclusion(src, dst, width, height, param)
            ProcessingMode.MELANOPIE       -> applyMelanopie(src, dst, width * height)
            ProcessingMode.ASTIGMATISME    -> applyAstigmatisme(src, dst, width, height, param)
            ProcessingMode.ALBINISME       -> applyAlbinisme(src, dst, width, height, param)
        }
    }

    private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v

    private fun luma(c: Int): Int =
        (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)).toInt()

    private fun rgb(r: Int, g: Int, b: Int) =
        (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)

    private fun colorDistance(c1: Int, c2: Int): Int {
        val dr = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
        val dg = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
        val db = (c1 and 0xFF) - (c2 and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    // ===== MODES DE BASE =====

    private fun applyLuminosite(src: IntArray, dst: IntArray, n: Int, param: Float) {
        val off = ((param - 50f) * 2.55f).toInt()
        for (i in 0 until n) {
            val c = src[i]
            dst[i] = rgb(((c shr 16) and 0xFF) + off, ((c shr 8) and 0xFF) + off, (c and 0xFF) + off)
        }
    }

    private fun applyContraste(src: IntArray, dst: IntArray, n: Int, param: Float) {
        val f = (param / 50f)
        for (i in 0 until n) {
            val c = src[i]
            val r = clamp(((((c shr 16) and 0xFF) - 128) * f + 128).toInt())
            val g = clamp(((((c shr 8) and 0xFF) - 128) * f + 128).toInt())
            val b = clamp((((c and 0xFF) - 128) * f + 128).toInt())
            dst[i] = rgb(r, g, b)
        }
    }

    private fun applyGamma(src: IntArray, dst: IntArray, n: Int, param: Float) {
        if (param != lastGamma) {
            currentGammaLut = IntArray(256) { i -> clamp((255 * (i / 255.0).pow(1.0 / param)).toInt()) }
            lastGamma = param
        }
        val lut = currentGammaLut
        for (i in 0 until n) {
            val c = src[i]
            dst[i] = rgb(lut[(c shr 16) and 0xFF], lut[(c shr 8) and 0xFF], lut[c and 0xFF])
        }
    }

    private fun applyHiContrast(src: IntArray, dst: IntArray, n: Int, param: Float) {
        val thr = (param * 2.55f).toInt()
        for (i in 0 until n) {
            val l = luma(src[i])
            dst[i] = if (l >= thr) rgb(255, 255, 255) else rgb(0, 0, 0)
        }
    }

    // ===== CONTOURS =====

    private fun applyContoursPlus(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val force = param.toInt().coerceIn(1, 20)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = src[idx]
                val r = ((c shr 16) and 0xFF)
                val g = ((c shr 8) and 0xFF)
                val b = (c and 0xFF)
                fun ch(offset: Int, shift: Int) = (src[idx + offset] shr shift) and 0xFF
                val lapR = 4 * r - ch(-1, 16) - ch(1, 16) - ch(-w, 16) - ch(w, 16)
                val lapG = 4 * g - ch(-1, 8) - ch(1, 8) - ch(-w, 8) - ch(w, 8)
                val lapB = 4 * b - ch(-1, 0) - ch(1, 0) - ch(-w, 0) - ch(w, 0)
                val lr = clamp(r + (force * lapR / 4))
                val lg = clamp(g + (force * lapG / 4))
                val lb = clamp(b + (force * lapB / 4))
                dst[idx] = rgb(lr, lg, lb)
            }
        }
        for (x in 0 until w) { dst[x] = src[x]; dst[(h - 1) * w + x] = src[(h - 1) * w + x] }
        for (y in 0 until h) { dst[y * w] = src[y * w]; dst[y * w + w - 1] = src[y * w + w - 1] }
    }

    private fun applyContours(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val thr = param.toInt()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                fun l(off: Int) = luma(src[idx + off])
                val gx = -l(-w - 1) + l(-w + 1) - 2 * l(-1) + 2 * l(1) - l(w - 1) + l(w + 1)
                val gy = l(-w - 1) + 2 * l(-w) + l(-w + 1) - l(w - 1) - 2 * l(w) - l(w + 1)
                val mag = clamp(sqrt((gx * gx + gy * gy).toDouble()).toInt())
                dst[idx] = if (mag > thr) rgb(mag, mag, mag) else rgb(0, 0, 0)
            }
        }
        for (x in 0 until w) { dst[x] = 0xFF000000.toInt(); dst[(h - 1) * w + x] = 0xFF000000.toInt() }
        for (y in 0 until h) { dst[y * w] = 0xFF000000.toInt(); dst[y * w + w - 1] = 0xFF000000.toInt() }
    }

    private fun applyNegatif(src: IntArray, dst: IntArray, n: Int) {
        for (i in 0 until n) {
            val c = src[i]
            dst[i] = (c and 0xFF000000.toInt()) or (c.inv() and 0x00FFFFFF)
        }
    }

    private fun applyGris(src: IntArray, dst: IntArray, n: Int) {
        for (i in 0 until n) {
            val l = luma(src[i])
            dst[i] = rgb(l, l, l)
        }
    }

    private fun applySeuil(src: IntArray, dst: IntArray, n: Int, param: Float) {
        val thr = param.toInt()
        for (i in 0 until n) {
            val v = if (luma(src[i]) >= thr) 255 else 0
            dst[i] = rgb(v, v, v)
        }
    }

    // ===== PALETTES & DALTONISME =====

    private fun applyPalChaud(src: IntArray, dst: IntArray, n: Int) {
        for (i in 0 until n) {
            val c = src[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            dst[i] = rgb(clamp(r + 40), clamp(g - 20), clamp(b - 30))
        }
    }

    private fun applyPalFroid(src: IntArray, dst: IntArray, n: Int) {
        for (i in 0 until n) {
            val c = src[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            dst[i] = rgb(clamp(r - 30), clamp(g + 10), clamp(b + 40))
        }
    }

    private fun applyLut(src: IntArray, dst: IntArray, n: Int, lut: IntArray) {
        for (i in 0 until n) {
            dst[i] = lut[luma(src[i])]
        }
    }

    private fun applyDeutan(src: IntArray, dst: IntArray, n: Int) {
        for (i in 0 until n) {
            val c = src[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val nr = clamp((r * 0.625 + g * 0.375).toInt())
            val ng = clamp((r * 0.7 + g * 0.3).toInt())
            val nb = clamp((g * 0.3 + b * 0.7).toInt())
            dst[i] = rgb(nr, ng, nb)
        }
    }

    private fun applyProtan(src: IntArray, dst: IntArray, n: Int) {
        for (i in 0 until n) {
            val c = src[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val nr = clamp((r * 0.567 + g * 0.433).toInt())
            val ng = clamp((r * 0.558 + g * 0.442).toInt())
            val nb = clamp((g * 0.242 + b * 0.758).toInt())
            dst[i] = rgb(nr, ng, nb)
        }
    }

    private fun applyTritan(src: IntArray, dst: IntArray, n: Int) {
        for (i in 0 until n) {
            val c = src[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val nr = clamp((r * 0.95 + b * 0.05).toInt())
            val ng = clamp((g * 0.433 + b * 0.567).toInt())
            val nb = clamp((g * 0.475 + b * 0.525).toInt())
            dst[i] = rgb(nr, ng, nb)
        }
    }

    // ===== ZOOM & CREATIFS =====

    private fun applyZoom(src: IntArray, dst: IntArray, w: Int, h: Int, factor: Int) {
        val cw = w / factor
        val ch = h / factor
        val ox = (w - cw) / 2
        val oy = (h - ch) / 2
        for (dy in 0 until h) {
            for (dx in 0 until w) {
                val sx = ox + (dx * cw) / w
                val sy = oy + (dy * ch) / h
                dst[dy * w + dx] = src[sy * w + sx]
            }
        }
    }

    private fun applyPoster(src: IntArray, dst: IntArray, n: Int, levels: Int) {
        val step = 256 / levels.coerceAtLeast(2)
        for (i in 0 until n) {
            val c = src[i]
            val r = ((c shr 16) and 0xFF) / step * step
            val g = ((c shr 8) and 0xFF) / step * step
            val b = (c and 0xFF) / step * step
            dst[i] = rgb(r, g, b)
        }
    }

    private fun applyRelief(src: IntArray, dst: IntArray, w: Int, h: Int) {
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val r = clamp(128 + luma(src[idx + w + 1]) - luma(src[idx - w - 1]))
                dst[idx] = rgb(r, r, r)
            }
        }
    }

    private fun applyFlou(src: IntArray, dst: IntArray, w: Int, h: Int) {
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var r = 0; var g = 0; var b = 0
                for (ky in -1..1) for (kx in -1..1) {
                    val c = src[(y + ky) * w + (x + kx)]
                    r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                }
                dst[y * w + x] = rgb(r / 9, g / 9, b / 9)
            }
        }
    }

    private fun applyNettete(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val amount = param / 10f
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = src[idx]
                var r = 0; var g = 0; var b = 0
                for (ky in -1..1) for (kx in -1..1) {
                    val n = src[(y + ky) * w + (x + kx)]
                    r += (n shr 16) and 0xFF; g += (n shr 8) and 0xFF; b += n and 0xFF
                }
                val cr = (c shr 16) and 0xFF; val cg = (c shr 8) and 0xFF; val cb = c and 0xFF
                dst[idx] = rgb(
                    clamp(cr + (amount * (cr - r / 9)).toInt()),
                    clamp(cg + (amount * (cg - g / 9)).toInt()),
                    clamp(cb + (amount * (cb - b / 9)).toInt())
                )
            }
        }
    }

    private fun applyNuit(src: IntArray, dst: IntArray, n: Int, param: Float) {
        val gain = param / 50f
        for (i in 0 until n) {
            val l = clamp((luma(src[i]) * gain).toInt())
            dst[i] = rgb(0, clamp((l * 1.2).toInt()), 0)
        }
    }

    private fun applyCroquis(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val thr = param.toInt()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                fun l(off: Int) = luma(src[idx + off])
                val gx = -l(-w - 1) + l(-w + 1) - 2 * l(-1) + 2 * l(1) - l(w - 1) + l(w + 1)
                val gy = l(-w - 1) + 2 * l(-w) + l(-w + 1) - l(w - 1) - 2 * l(w) - l(w + 1)
                val mag = clamp(sqrt((gx * gx + gy * gy).toDouble()).toInt())
                val v = if (mag > thr) 0 else 255
                dst[idx] = rgb(v, v, v)
            }
        }
        for (x in 0 until w) { dst[x] = rgb(255,255,255); dst[(h-1)*w+x] = rgb(255,255,255) }
        for (y in 0 until h) { dst[y*w] = rgb(255,255,255); dst[y*w+w-1] = rgb(255,255,255) }
    }

    // ===== PATHOLOGIQUES V1.0 =====

    private fun applyAchromatopsie(src: IntArray, dst: IntArray, n: Int) {
        for (i in 0 until n) dst[i] = lutAchroma[luma(src[i])]
    }

    private var lastEblouissement = -1f
    private var lutEblouissementCache: IntArray = IntArray(256) { it }

    private fun applyAntiEblouissement(src: IntArray, dst: IntArray, n: Int, param: Float) {
        if (param != lastEblouissement) {
            lutEblouissementCache = buildLutEblouissement(param)
            lastEblouissement = param
        }
        val lut = lutEblouissementCache
        for (i in 0 until n) {
            val c = src[i]
            dst[i] = rgb(lut[(c shr 16) and 0xFF], lut[(c shr 8) and 0xFF], lut[c and 0xFF])
        }
    }

    private fun buildLutEblouissement(param: Float): IntArray {
        val knee = (255 - param * 1.8f).toInt().coerceIn(40, 250)
        val ratio = 0.25f
        return IntArray(256) { v ->
            if (v <= knee) v else (knee + (v - knee) * ratio).toInt().coerceIn(0, 255)
        }
    }

    private fun applyDejaunissement(src: IntArray, dst: IntArray, n: Int, param: Float) {
        val f = param / 100f
        val rOff = (-15 * f).toInt()
        val gOff = (-5 * f).toInt()
        val bOff = (35 * f).toInt()
        for (i in 0 until n) {
            val c = src[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            dst[i] = rgb(clamp(r + rOff), clamp(g + gOff), clamp(b + bOff))
        }
    }

    private fun applyAntiHalo(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val thr = param.toInt()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = src[idx]
                val l = luma(c)
                var sum = 0
                for (ky in -1..1) for (kx in -1..1) {
                    if (ky != 0 || kx != 0) sum += luma(src[idx + ky * w + kx])
                }
                val avgN = sum / 8
                if (l - avgN > thr && l > 0) {
                    val ratio = avgN.toFloat() / l.toFloat()
                    val r = (((c shr 16) and 0xFF) * ratio).toInt()
                    val g = (((c shr 8) and 0xFF) * ratio).toInt()
                    val b = ((c and 0xFF) * ratio).toInt()
                    dst[idx] = rgb(clamp(r), clamp(g), clamp(b))
                } else {
                    dst[idx] = c
                }
            }
        }
        for (x in 0 until w) { dst[x] = src[x]; dst[(h - 1) * w + x] = src[(h - 1) * w + x] }
        for (y in 0 until h) { dst[y * w] = src[y * w]; dst[y * w + w - 1] = src[y * w + w - 1] }
    }

    // ===== CONTRASTE LOCAL V2.0 =====

    private fun applyContrasteLocalV2(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val tile = (min(w, h) / 16).coerceIn(8, 48)
        val tilesX = (w + tile - 1) / tile
        val tilesY = (h + tile - 1) / tile

        val config = Pair(tilesX, tilesY)
        val tMin: IntArray
        val tMax: IntArray
        if (lastTileConfig != config || tileMinCache == null) {
            tMin = IntArray(tilesX * tilesY) { 255 }
            tMax = IntArray(tilesX * tilesY) { 0 }
            tileMinCache = tMin
            tileMaxCache = tMax
            lastTileConfig = config
        } else {
            tMin = tileMinCache!!
            tMax = tileMaxCache!!
            tMin.fill(255)
            tMax.fill(0)
        }

        for (y in 0 until h) {
            val ty = y / tile
            for (x in 0 until w) {
                val tx = x / tile
                val l = luma(src[y * w + x])
                val ti = ty * tilesX + tx
                if (l < tMin[ti]) tMin[ti] = l
                if (l > tMax[ti]) tMax[ti] = l
            }
        }

        val blend = param / 100f

        for (y in 0 until h) {
            val ty = y / tile
            val tyf = (y % tile).toFloat() / tile

            for (x in 0 until w) {
                val tx = x / tile
                val txf = (x % tile).toFloat() / tile

                val ti00 = ty * tilesX + tx
                val ti01 = if (tx + 1 < tilesX) ti00 + 1 else ti00
                val ti10 = if (ty + 1 < tilesY) ti00 + tilesX else ti00
                val ti11 = if (tx + 1 < tilesX && ty + 1 < tilesY) ti00 + tilesX + 1 else ti00

                val min00 = tMin[ti00].toFloat(); val max00 = tMax[ti00].toFloat()
                val min01 = tMin[ti01].toFloat(); val max01 = tMax[ti01].toFloat()
                val min10 = tMin[ti10].toFloat(); val max10 = tMax[ti10].toFloat()
                val min11 = tMin[ti11].toFloat(); val max11 = tMax[ti11].toFloat()

                val lo = (min00 * (1-txf) * (1-tyf) + min01 * txf * (1-tyf) +
                         min10 * (1-txf) * tyf + min11 * txf * tyf).toInt().coerceIn(0, 255)
                val hi = (max00 * (1-txf) * (1-tyf) + max01 * txf * (1-tyf) +
                         max10 * (1-txf) * tyf + max11 * txf * tyf).toInt().coerceIn(0, 255)

                val range = (hi - lo).coerceAtLeast(1)

                val idx = y * w + x
                val c = src[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                fun stretch(v: Int): Int {
                    val s = (v - lo) * 255 / range
                    return clamp((v * (1 - blend) + s * blend).toInt())
                }
                dst[idx] = rgb(stretch(r), stretch(g), stretch(b))
            }
        }
    }

    private fun applyChampLarge(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val scale = (param / 100f).coerceIn(0.3f, 0.95f)
        val dw = (w * scale).toInt().coerceAtLeast(1)
        val dh = (h * scale).toInt().coerceAtLeast(1)
        val ox = (w - dw) / 2
        val oy = (h - dh) / 2
        dst.fill(0xFF000000.toInt())
        for (dy in 0 until dh) {
            val sy = dy * h / dh
            for (dx in 0 until dw) {
                val sx = dx * w / dw
                dst[(oy + dy) * w + (ox + dx)] = src[sy * w + sx]
            }
        }
    }

    private fun applyHemiDroit(src: IntArray, dst: IntArray, w: Int, h: Int) {
        val halfW = w / 2
        dst.fill(0xFF000000.toInt())
        for (y in 0 until h) {
            for (dx in 0 until halfW) {
                val sx = dx * w / halfW
                dst[y * w + dx] = src[y * w + sx]
            }
        }
    }

    private fun applyHemiGauche(src: IntArray, dst: IntArray, w: Int, h: Int) {
        val halfW = w / 2
        dst.fill(0xFF000000.toInt())
        for (y in 0 until h) {
            for (dx in 0 until halfW) {
                val sx = dx * w / halfW
                dst[y * w + (w - halfW + dx)] = src[y * w + sx]
            }
        }
    }

    private fun applyExcentrationV2(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val angleRad = (param * PI / 180f)
        val zoom = 1.6
        val offsetFrac = 0.22
        val ox = (cos(angleRad) * offsetFrac * w).toInt()
        val oy = (sin(angleRad) * offsetFrac * h).toInt()
        val cw = (w / zoom).toInt().coerceAtLeast(1)
        val ch = (h / zoom).toInt().coerceAtLeast(1)
        val baseX = ((w - cw) / 2 + ox).coerceIn(0, w - cw)
        val baseY = ((h - ch) / 2 + oy).coerceIn(0, h - ch)
        for (dy in 0 until h) {
            val sy = baseY + dy * ch / h
            for (dx in 0 until w) {
                val sx = baseX + dx * cw / w
                dst[dy * w + dx] = src[sy * w + sx]
            }
        }
    }

    private fun applyNyctalopiePlus(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val gain = param / 50f
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                fun l(off: Int) = luma(src[idx + off])
                val gx = -l(-w - 1) + l(-w + 1) - 2 * l(-1) + 2 * l(1) - l(w - 1) + l(w + 1)
                val gy = l(-w - 1) + 2 * l(-w) + l(-w + 1) - l(w - 1) - 2 * l(w) - l(w + 1)
                val edge = clamp(sqrt((gx * gx + gy * gy).toDouble()).toInt())
                if (edge > 25) {
                    val e = clamp((edge * 1.5).toInt())
                    dst[idx] = rgb(e, e, 0)
                } else {
                    val base = clamp((luma(src[idx]) * gain).toInt())
                    dst[idx] = rgb(0, clamp((base * 1.3).toInt()), 0)
                }
            }
        }
        for (x in 0 until w) { dst[x] = 0xFF000000.toInt(); dst[(h - 1) * w + x] = 0xFF000000.toInt() }
        for (y in 0 until h) { dst[y * w] = 0xFF000000.toInt(); dst[y * w + w - 1] = 0xFF000000.toInt() }
    }

    // ===== STABILISATION V2.0 =====

    private fun applyStabilisationV2(src: IntArray, dst: IntArray, n: Int, param: Float) {
        val alpha = (1f - param / 100f).coerceIn(0.1f, 0.9f)

        var prev = prevFrameLocal.get()
        if (prev == null || prev.size != n) {
            prev = src.copyOf()
            prevFrameLocal.set(prev)
        }

        for (i in 0 until n) {
            val c = src[i]; val p = prev[i]
            val r = (((c shr 16) and 0xFF) * alpha + ((p shr 16) and 0xFF) * (1 - alpha)).toInt()
            val g = (((c shr 8) and 0xFF) * alpha + ((p shr 8) and 0xFF) * (1 - alpha)).toInt()
            val b = ((c and 0xFF) * alpha + (p and 0xFF) * (1 - alpha)).toInt()
            dst[i] = rgb(r, g, b)
        }
        dst.copyInto(prev)
    }

    // ===== NOUVEAUTES V2.0 =====

    // --- LECTURE ---

    private fun applyLecture(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val sensitivity = (param / 100f).coerceIn(0.2f, 1.0f)
        val n = w * h

        val variance = FloatArray(n) { 0f }
        val window = 5
        val halfW = window / 2

        for (y in halfW until h - halfW) {
            for (x in halfW until w - halfW) {
                val idx = y * w + x
                var sum = 0f
                var sumSq = 0f
                var count = 0

                for (ky in -halfW..halfW) {
                    for (kx in -halfW..halfW) {
                        val li = luma(src[idx + ky * w + kx]).toFloat()
                        sum += li
                        sumSq += li * li
                        count++
                    }
                }

                val mean = sum / count
                val varia = (sumSq / count) - (mean * mean)
                variance[idx] = varia
            }
        }

        val varThreshold = (800f * (1.5f - sensitivity)).coerceIn(100f, 2000f)
        val textMask = BooleanArray(n) { false }

        for (y in halfW until h - halfW) {
            for (x in halfW until w - halfW) {
                val idx = y * w + x
                if (variance[idx] > varThreshold) {
                    for (ky in -halfW..halfW) {
                        for (kx in -halfW..halfW) {
                            textMask[idx + ky * w + kx] = true
                        }
                    }
                }
            }
        }

        var minX = w; var maxX = 0; var minY = h; var maxY = 0
        var hasText = false
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (textMask[y * w + x]) {
                    if (!hasText) { minX = x; maxX = x; minY = y; maxY = y; hasText = true }
                    else {
                        if (x < minX) minX = x; if (x > maxX) maxX = x
                        if (y < minY) minY = y; if (y > maxY) maxY = y
                    }
                }
            }
        }

        if (!hasText) {
            src.copyInto(dst)
            drawDashedRect(dst, w, h, w/4, h/4, w*3/4, h*3/4, 0xFF00FFFF.toInt())
            return
        }

        val margin = (min(w, h) / 20).coerceIn(8, 40)
        val boxX = (minX - margin).coerceIn(0, w - 1)
        val boxY = (minY - margin).coerceIn(0, h - 1)
        val boxW = (maxX - minX + 2 * margin).coerceIn(1, w - boxX)
        val boxH = (maxY - minY + 2 * margin).coerceIn(1, h - boxY)

        val k = -0.2f

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x

                if (x >= boxX && x < boxX + boxW && y >= boxY && y < boxY + boxH) {
                    val localMean = computeLocalMean(src, w, h, x, y, 7)
                    val localStd = computeLocalStd(src, w, h, x, y, 7, localMean)
                    val threshold = (localMean + k * localStd).toInt().coerceIn(0, 255)
                    val l = luma(src[idx])
                    val v = if (l > threshold) 255 else 0
                    dst[idx] = rgb(v, v, v)
                } else {
                    val c = src[idx]
                    val r = ((c shr 16) and 0xFF) / 3
                    val g = ((c shr 8) and 0xFF) / 3
                    val b = (c and 0xFF) / 3
                    dst[idx] = rgb(r, g, b)
                }
            }
        }

        drawRect(dst, w, h, boxX, boxY, boxX + boxW, boxY + boxH, 0xFF00FFFF.toInt(), 3)
    }

    private fun computeLocalMean(src: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int): Float {
        var sum = 0f
        var count = 0
        for (y in (cy - radius).coerceIn(0, h - 1)..(cy + radius).coerceIn(0, h - 1)) {
            for (x in (cx - radius).coerceIn(0, w - 1)..(cx + radius).coerceIn(0, w - 1)) {
                sum += luma(src[y * w + x])
                count++
            }
        }
        return if (count > 0) sum / count else 128f
    }

    private fun computeLocalStd(src: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int, mean: Float): Float {
        var sumSq = 0f
        var count = 0
        for (y in (cy - radius).coerceIn(0, h - 1)..(cy + radius).coerceIn(0, h - 1)) {
            for (x in (cx - radius).coerceIn(0, w - 1)..(cx + radius).coerceIn(0, w - 1)) {
                val diff = luma(src[y * w + x]) - mean
                sumSq += diff * diff
                count++
            }
        }
        return if (count > 0) sqrt(sumSq / count) else 0f
    }

    private fun drawRect(dst: IntArray, w: Int, h: Int, x1: Int, y1: Int, x2: Int, y2: Int, color: Int, thickness: Int) {
        for (t in 0 until thickness) {
            for (x in (x1 - t).coerceIn(0, w - 1)..(x2 + t).coerceIn(0, w - 1)) {
                if (y1 - t in 0 until h) dst[(y1 - t) * w + x] = color
                if (y2 + t in 0 until h) dst[(y2 + t) * w + x] = color
            }
            for (y in (y1 - t).coerceIn(0, h - 1)..(y2 + t).coerceIn(0, h - 1)) {
                if (x1 - t in 0 until w) dst[y * w + (x1 - t)] = color
                if (x2 + t in 0 until w) dst[y * w + (x2 + t)] = color
            }
        }
    }

    private fun drawDashedRect(dst: IntArray, w: Int, h: Int, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        val dashLen = 10
        val gapLen = 5
        var x = x1
        while (x < x2) {
            for (dx in 0 until dashLen.coerceAtMost(x2 - x)) {
                if (y1 in 0 until h && x + dx in 0 until w) dst[y1 * w + x + dx] = color
            }
            x += dashLen + gapLen
        }
        x = x1
        while (x < x2) {
            for (dx in 0 until dashLen.coerceAtMost(x2 - x)) {
                if (y2 in 0 until h && x + dx in 0 until w) dst[y2 * w + x + dx] = color
            }
            x += dashLen + gapLen
        }
        var y = y1
        while (y < y2) {
            for (dy in 0 until dashLen.coerceAtMost(y2 - y)) {
                if (y + dy in 0 until h && x1 in 0 until w) dst[(y + dy) * w + x1] = color
            }
            y += dashLen + gapLen
        }
        y = y1
        while (y < y2) {
            for (dy in 0 until dashLen.coerceAtMost(y2 - y)) {
                if (y + dy in 0 until h && x2 in 0 until w) dst[(y + dy) * w + x2] = color
            }
            y += dashLen + gapLen
        }
    }

    // --- MONO_OCCLUSION ---

    private fun applyMonoOcclusion(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val halfW = w / 2
        val now = System.currentTimeMillis()

        val side = when {
            param <= 5f -> 0
            param >= 95f -> 1
            else -> {
                val freq = 0.5f + (param / 100f) * 3.5f
                val period = (1000f / freq).toInt()
                if ((now % period) < (period / 2)) 0 else 1
            }
        }

        src.copyInto(dst)
        val maskColor = 0xFF000000.toInt()

        if (side == 0) {
            for (y in 0 until h) {
                for (x in halfW until w) {
                    dst[y * w + x] = maskColor
                }
            }
        } else {
            for (y in 0 until h) {
                for (x in 0 until halfW) {
                    dst[y * w + x] = maskColor
                }
            }
        }

        val sepColor = 0xFFFF0000.toInt()
        for (y in 0 until h) {
            dst[y * w + halfW] = sepColor
            dst[y * w + halfW - 1] = sepColor
        }

        val indicatorColor = if (side == 0) 0xFF00FF00.toInt() else 0xFF0000FF.toInt()
        for (y in 0..20) {
            for (x in 0..20) {
                dst[y * w + x] = indicatorColor
            }
        }
    }

    // --- MELANOPIE ---

    private fun applyMelanopie(src: IntArray, dst: IntArray, n: Int) {
        for (i in 0 until n) {
            val c = src[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val nr = clamp((r * 0.8).toInt())
            val ng = clamp((g * 1.1).toInt())
            val nb = clamp((b * 1.5).toInt())

            val l = luma(c)
            val contrastBoost = if (l > 128) 1.2f else 0.8f
            val finalR = clamp((nr * contrastBoost).toInt())
            val finalG = clamp((ng * contrastBoost).toInt())
            val finalB = clamp((nb * contrastBoost).toInt())

            dst[i] = rgb(finalR, finalG, finalB)
        }
    }

    // --- ASTIGMATISME ---

    private fun applyAstigmatisme(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val angleRad = ((param + 90f) * PI / 180f)
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        val blurRadius = 3

        val temp = src.copyOf()

        for (y in 0 until h) {
            for (x in 0 until w) {
                var sumR = 0; var sumG = 0; var sumB = 0
                var count = 0

                for (k in -blurRadius..blurRadius) {
                    val fx = x + (k * cosA).toInt()
                    val fy = y + (k * sinA).toInt()

                    if (fx in 0 until w && fy in 0 until h) {
                        val c = temp[fy * w + fx]
                        sumR += (c shr 16) and 0xFF
                        sumG += (c shr 8) and 0xFF
                        sumB += c and 0xFF
                        count++
                    }
                }

                if (count > 0) {
                    dst[y * w + x] = rgb(sumR / count, sumG / count, sumB / count)
                } else {
                    dst[y * w + x] = temp[y * w + x]
                }
            }
        }

        val cx = w / 2
        val cy = h / 2
        val lineLen = min(w, h) / 3
        val lineColor = 0xFFFFFF00.toInt()

        for (k in -lineLen..lineLen) {
            val lx = cx + (k * cos(angleRad)).toInt()
            val ly = cy + (k * sin(angleRad)).toInt()
            if (lx in 0 until w && ly in 0 until h) {
                dst[ly * w + lx] = lineColor
            }
        }
    }

    // --- ALBINISME ---

    private fun applyAlbinisme(src: IntArray, dst: IntArray, w: Int, h: Int, param: Float) {
        val n = w * h
        val intensity = param / 100f

        val temp = IntArray(n)
        for (i in 0 until n) {
            temp[i] = lutAchroma[luma(src[i])]
        }

        val eblouissementLut = buildLutEblouissement(80f * intensity + 20f)
        for (i in 0 until n) {
            val c = temp[i]
            temp[i] = rgb(
                eblouissementLut[(c shr 16) and 0xFF],
                eblouissementLut[(c shr 8) and 0xFF],
                eblouissementLut[c and 0xFF]
            )
        }

        val contoursForce = (5 + 10 * intensity).toInt().coerceIn(5, 15)
        applyContoursPlus(temp, dst, w, h, contoursForce.toFloat())

        val dilated = IntArray(n)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                var maxL = luma(dst[idx])
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val l = luma(dst[idx + ky * w + kx])
                        if (l > maxL) maxL = l
                    }
                }
                dilated[idx] = rgb(maxL, maxL, maxL)
            }
        }

        for (i in 0 until n) {
            val orig = dst[i]
            val dil = dilated[i]
            val blend = intensity * 0.5f
            val r = (((orig shr 16) and 0xFF) * (1 - blend) + ((dil shr 16) and 0xFF) * blend).toInt()
            val g = (((orig shr 8) and 0xFF) * (1 - blend) + ((dil shr 8) and 0xFF) * blend).toInt()
            val b = ((orig and 0xFF) * (1 - blend) + (dil and 0xFF) * blend).toInt()
            dst[i] = rgb(r, g, b)
        }
    }

    // ===== LUTS =====

    private fun buildLutFeu() = IntArray(256) { i ->
        when {
            i < 85  -> rgb(i * 3, 0, 0)
            i < 170 -> rgb(255, (i - 85) * 3, 0)
            else    -> rgb(255, 255, (i - 170) * 3)
        }
    }

    private fun buildLutGlace() = IntArray(256) { i ->
        when {
            i < 85  -> rgb(0, 0, i * 3)
            i < 170 -> rgb(0, (i - 85) * 3, 255)
            else    -> rgb((i - 170) * 3, 255, 255)
        }
    }

    private fun buildLutNeon() = IntArray(256) { i ->
        rgb(0, clamp(i * 2), 0)
    }

    private fun buildLutJet() = IntArray(256) { i ->
        val x = i / 255.0
        val r = clamp((1.5 - abs(4.0 * x - 3.0)).coerceIn(0.0, 1.0).times(255).toInt())
        val g = clamp((1.5 - abs(4.0 * x - 2.0)).coerceIn(0.0, 1.0).times(255).toInt())
        val b = clamp((1.5 - abs(4.0 * x - 1.0)).coerceIn(0.0, 1.0).times(255).toInt())
        rgb(r, g, b)
    }

    private fun buildLutAchroma() = IntArray(256) { l ->
        val knee = 160
        val v = if (l <= knee) l else knee + ((l - knee) * 0.35).toInt()
        val vc = clamp(v)
        rgb(vc, vc, vc)
    }
}

