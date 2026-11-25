// =====================================================================
//  COLAGEM SHORTS — app Android de colagem de imagens (arquivo único)
//  Coloque este arquivo na RAIZ do repositório e o build.yml em
//  .github/workflows/build.yml. O GitHub monta o projeto e gera o APK.
// =====================================================================
package com.colagem.shorts

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ------------------------------- Cores -------------------------------
val C_BG = 0xFF121212.toInt()
val C_PANEL = 0xFF1C1C1C.toInt()
val C_CARD = 0xFF262626.toInt()
val C_BTN = 0xFF343434.toInt()
val C_ACCENT = 0xFF3D7BFF.toInt()
val C_ACCENT_DARK = 0xFF1E3770.toInt()
val C_TEXT = 0xFFF2F2F2.toInt()
val C_DIM = 0xFFA8A8A8.toInt()
val C_DANGER = 0xFFB3261E.toInt()
val C_OK = 0xFF2E7D32.toInt()
const val MATCH = -1
const val WRAP = -2

private var idSeq = System.currentTimeMillis()
fun newId(): Long { idSeq += 1; return idSeq }

// ------------------------------- Dados -------------------------------
class Layer {
    var id = 0L
    var name = "Camada"
    var src: String? = null          // URI da imagem (content:// ou file://)
    var isFrame = false              // true = forma/moldura que recebe imagem
    var x = 0f; var y = 0f; var w = 100f; var h = 100f
    var rot = 0f
    var radius = 0f
    var opacity = 100
    var locked = false
    var visible = true
    var flipH = false
    var cropL = 0f; var cropT = 0f; var cropR = 1f; var cropB = 1f
    var zoom = 1f; var panX = 0f; var panY = 0f
    var border = 0f
    var borderColor = Color.WHITE

    fun cx() = x + w / 2f
    fun cy() = y + h / 2f

    fun toJson(sx: Float = 1f, sy: Float = 1f): JSONObject {
        val o = JSONObject()
        o.put("id", id); o.put("nm", name); o.put("src", src ?: ""); o.put("fr", isFrame)
        o.put("x", (x * sx).toDouble()); o.put("y", (y * sy).toDouble())
        o.put("w", (w * sx).toDouble()); o.put("h", (h * sy).toDouble())
        o.put("rot", rot.toDouble()); o.put("rad", (radius * sx).toDouble())
        o.put("op", opacity); o.put("lk", locked); o.put("vis", visible); o.put("fl", flipH)
        o.put("cl", cropL.toDouble()); o.put("ct", cropT.toDouble())
        o.put("cr", cropR.toDouble()); o.put("cb", cropB.toDouble())
        o.put("z", zoom.toDouble()); o.put("px", panX.toDouble()); o.put("py", panY.toDouble())
        o.put("bd", (border * sx).toDouble()); o.put("bc", borderColor)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject, sx: Float = 1f, sy: Float = 1f): Layer {
            val l = Layer()
            l.id = if (o.has("id")) o.optLong("id") else newId()
            l.name = o.optString("nm", "Camada")
            val s = o.optString("src", "")
            l.src = if (s.isEmpty()) null else s
            l.isFrame = o.optBoolean("fr", false)
            l.x = o.optDouble("x", 0.0).toFloat() * sx
            l.y = o.optDouble("y", 0.0).toFloat() * sy
            l.w = max(1f, o.optDouble("w", 100.0).toFloat() * sx)
            l.h = max(1f, o.optDouble("h", 100.0).toFloat() * sy)
            l.rot = o.optDouble("rot", 0.0).toFloat()
            l.radius = o.optDouble("rad", 0.0).toFloat() * sx
            l.opacity = o.optInt("op", 100)
            l.locked = o.optBoolean("lk", false)
            l.visible = o.optBoolean("vis", true)
            l.flipH = o.optBoolean("fl", false)
            l.cropL = o.optDouble("cl", 0.0).toFloat(); l.cropT = o.optDouble("ct", 0.0).toFloat()
            l.cropR = o.optDouble("cr", 1.0).toFloat(); l.cropB = o.optDouble("cb", 1.0).toFloat()
            l.zoom = o.optDouble("z", 1.0).toFloat()
            l.panX = o.optDouble("px", 0.0).toFloat(); l.panY = o.optDouble("py", 0.0).toFloat()
            l.border = o.optDouble("bd", 0.0).toFloat() * sx
            l.borderColor = o.optInt("bc", Color.WHITE)
            return l
        }
    }
}

class Doc {
    var id = ""
    var name = ""
    var w = 1080
    var h = 1920
    var bgColor = Color.WHITE
    var bgSrc: String? = null
    var bgBlur = 0
    var bgDim = 0
    var updated = 0L
    val layers = ArrayList<Layer>()

    fun find(id: Long): Layer? = layers.firstOrNull { it.id == id }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id); o.put("name", name); o.put("w", w); o.put("h", h)
        o.put("bg", bgColor); o.put("bgSrc", bgSrc ?: ""); o.put("blur", bgBlur); o.put("dim", bgDim)
        o.put("upd", updated)
        val a = JSONArray()
        for (l in layers) a.put(l.toJson())
        o.put("layers", a)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Doc {
            val d = Doc()
            d.id = o.optString("id", System.currentTimeMillis().toString())
            d.name = o.optString("name", "Colagem")
            d.w = o.optInt("w", 1080); d.h = o.optInt("h", 1920)
            d.bgColor = o.optInt("bg", Color.WHITE)
            val s = o.optString("bgSrc", "")
            d.bgSrc = if (s.isEmpty()) null else s
            d.bgBlur = o.optInt("blur", 0); d.bgDim = o.optInt("dim", 0)
            d.updated = o.optLong("upd", 0L)
            val a = o.optJSONArray("layers")
            if (a != null) for (i in 0 until a.length()) {
                val lo = a.optJSONObject(i) ?: continue
                d.layers.add(Layer.fromJson(lo))
            }
            return d
        }
    }
}

data class Preset(val label: String, val sub: String, val w: Int, val h: Int)

val PRESETS = listOf(
    Preset("9:16", "Shorts / Reels / TikTok", 1080, 1920),
    Preset("1:1", "Quadrado", 1080, 1080),
    Preset("4:5", "Retrato (Instagram)", 1080, 1350),
    Preset("16:9", "Paisagem / YouTube", 1920, 1080),
    Preset("3:4", "Retrato", 1080, 1440),
    Preset("4:3", "Paisagem", 1440, 1080),
    Preset("2:3", "Pôster", 1080, 1620),
    Preset("Personalizada", "Sua resolução", 0, 0)
)

class Template(val name: String, val cells: List<FloatArray>)

fun cell(a: Float, b: Float, c: Float, d: Float) = floatArrayOf(a, b, c, d)

val TEMPLATES = listOf(
    Template("2 lado a lado", listOf(cell(0f, 0f, 0.5f, 1f), cell(0.5f, 0f, 1f, 1f))),
    Template("2 empilhadas", listOf(cell(0f, 0f, 1f, 0.5f), cell(0f, 0.5f, 1f, 1f))),
    Template("3 empilhadas", listOf(cell(0f, 0f, 1f, 1f / 3f), cell(0f, 1f / 3f, 1f, 2f / 3f), cell(0f, 2f / 3f, 1f, 1f))),
    Template("3 colunas", listOf(cell(0f, 0f, 1f / 3f, 1f), cell(1f / 3f, 0f, 2f / 3f, 1f), cell(2f / 3f, 0f, 1f, 1f))),
    Template("Grade 2×2", listOf(cell(0f, 0f, 0.5f, 0.5f), cell(0.5f, 0f, 1f, 0.5f), cell(0f, 0.5f, 0.5f, 1f), cell(0.5f, 0.5f, 1f, 1f))),
    Template("1 grande + 2", listOf(cell(0f, 0f, 1f, 0.5f), cell(0f, 0.5f, 0.5f, 1f), cell(0.5f, 0.5f, 1f, 1f))),
    Template("2 + 1 grande", listOf(cell(0f, 0f, 0.5f, 0.5f), cell(0.5f, 0f, 1f, 0.5f), cell(0f, 0.5f, 1f, 1f))),
    Template("Grade 2×3", listOf(
        cell(0f, 0f, 0.5f, 1f / 3f), cell(0.5f, 0f, 1f, 1f / 3f),
        cell(0f, 1f / 3f, 0.5f, 2f / 3f), cell(0.5f, 1f / 3f, 1f, 2f / 3f),
        cell(0f, 2f / 3f, 0.5f, 1f), cell(0.5f, 2f / 3f, 1f, 1f))),
    Template("Destaque + 3", listOf(cell(0f, 0f, 1f, 0.6f), cell(0f, 0.6f, 1f / 3f, 1f), cell(1f / 3f, 0.6f, 2f / 3f, 1f), cell(2f / 3f, 0.6f, 1f, 1f))),
    Template("1 + 2 lateral", listOf(cell(0f, 0f, 0.6f, 1f), cell(0.6f, 0f, 1f, 0.5f), cell(0.6f, 0.5f, 1f, 1f))),
    Template("Moldura central", listOf(cell(0.06f, 0.2f, 0.94f, 0.8f)))
)

// ------------------------- Imagens (cache) ---------------------------
fun exifOrientation(ctx: Context, uri: Uri): Int {
    return try {
        val ins = ctx.contentResolver.openInputStream(uri) ?: return 1
        ins.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } catch (t: Throwable) { 1 }
}

fun decodeImage(ctx: Context, src: String, maxDim: Int): Bitmap? {
    try {
        val uri = Uri.parse(src)
        val cr = ctx.contentResolver
        val ob = BitmapFactory.Options()
        ob.inJustDecodeBounds = true
        val ins = cr.openInputStream(uri) ?: return null
        ins.use { BitmapFactory.decodeStream(it, null, ob) }
        if (ob.outWidth <= 0 || ob.outHeight <= 0) return null
        var s = 1
        while (max(ob.outWidth, ob.outHeight) / s > maxDim) s *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        val ins2 = cr.openInputStream(uri) ?: return null
        var bmp = ins2.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
        val m = Matrix()
        when (exifOrientation(ctx, uri)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> {}
        }
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        return bmp
    } catch (t: Throwable) {
        return null
    }
}

fun readImageSize(ctx: Context, uri: Uri): IntArray? {
    try {
        val ob = BitmapFactory.Options()
        ob.inJustDecodeBounds = true
        val ins = ctx.contentResolver.openInputStream(uri) ?: return null
        ins.use { BitmapFactory.decodeStream(it, null, ob) }
        if (ob.outWidth <= 0 || ob.outHeight <= 0) return null
        val o = exifOrientation(ctx, uri)
        return if (o == 5 || o == 6 || o == 7 || o == 8) intArrayOf(ob.outHeight, ob.outWidth)
        else intArrayOf(ob.outWidth, ob.outHeight)
    } catch (t: Throwable) {
        return null
    }
}

object Images {
    private val cache = HashMap<String, Bitmap>()
    private val loading = HashSet<String>()
    private val missingSet = HashSet<String>()
    private val blurCache = HashMap<String, Bitmap>()
    private val exec = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    @Volatile var maxDim = 2048
    var listener: (() -> Unit)? = null
    lateinit var app: Context

    fun isMissing(src: String?): Boolean {
        if (src == null) return false
        synchronized(this) { return missingSet.contains(src) }
    }

    fun peek(src: String?): Bitmap? {
        if (src == null) return null
        synchronized(this) { return cache[src] }
    }

    fun get(src: String?): Bitmap? {
        val b = peek(src)
        if (b == null) request(src)
        return b
    }

    fun request(src: String?) {
        if (src == null) return
        synchronized(this) {
            if (cache.containsKey(src) || loading.contains(src) || missingSet.contains(src)) return
            loading.add(src)
        }
        val md = maxDim
        exec.execute {
            val b = decodeImage(app, src, md)
            synchronized(this) {
                loading.remove(src)
                if (b != null) cache[src] = b else missingSet.add(src)
            }
            main.post { listener?.invoke() }
        }
    }

    fun loadSync(src: String): Bitmap? {
        val c = peek(src)
        if (c != null) return c
        if (isMissing(src)) return null
        val b = decodeImage(app, src, maxDim)
        synchronized(this) { if (b != null) cache[src] = b else missingSet.add(src) }
        return b
    }

    fun put(src: String, b: Bitmap) { synchronized(this) { cache[src] = b; missingSet.remove(src) } }
    fun forget(src: String) { synchronized(this) { missingSet.remove(src) } }
    fun clear() { synchronized(this) { cache.clear(); missingSet.clear(); blurCache.clear() } }

    fun blurred(src: String, level: Int): Bitmap? {
        val key = "$src#$level"
        synchronized(this) {
            val c = blurCache[key]
            if (c != null) return c
        }
        val b = peek(src) ?: return null
        return try {
            val f = 2f + level / 100f * 38f
            val tw = max(2, (b.width / f).toInt())
            val th = max(2, (b.height / f).toInt())
            var cur = b
            var w = b.width
            var h = b.height
            while (w / 2 >= tw && h / 2 >= th) {
                w /= 2; h /= 2
                cur = Bitmap.createScaledBitmap(cur, w, h, true)
            }
            cur = Bitmap.createScaledBitmap(cur, tw, th, true)
            val k = max(1, 300 / max(tw, th))
            if (k > 1) cur = Bitmap.createScaledBitmap(cur, tw * k, th * k, true)
            synchronized(this) {
                if (blurCache.size > 6) blurCache.clear()
                blurCache[key] = cur
            }
            cur
        } catch (t: Throwable) { null }
    }
}

// --------------------------- Geometria -------------------------------
fun coverRect(bw: Float, bh: Float, boxW: Float, boxH: Float, out: RectF) {
    val a = boxW / max(0.01f, boxH)
    if (bw / bh > a) {
        val sw = bh * a
        out.set((bw - sw) / 2f, 0f, (bw + sw) / 2f, bh)
    } else {
        val sh = bw / a
        out.set(0f, (bh - sh) / 2f, bw, (bh + sh) / 2f)
    }
}

fun frameSrc(l: Layer, b: Bitmap, out: RectF) {
    val bw = b.width.toFloat()
    val bh = b.height.toFloat()
    val boxA = max(0.01f, l.w) / max(0.01f, l.h)
    var sw: Float
    var sh: Float
    if (bw / bh > boxA) { sh = bh; sw = bh * boxA } else { sw = bw; sh = bw / boxA }
    val z = max(1f, l.zoom)
    sw /= z; sh /= z
    val maxPx = max(0f, (bw - sw) / 2f / bw)
    val maxPy = max(0f, (bh - sh) / 2f / bh)
    l.panX = l.panX.coerceIn(-maxPx, maxPx)
    l.panY = l.panY.coerceIn(-maxPy, maxPy)
    val cx = bw / 2f + l.panX * bw
    val cy = bh / 2f + l.panY * bh
    out.set(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)
}

fun layerPoint(l: Layer, lx: Float, ly: Float, out: PointF) {
    val t = Math.toRadians(l.rot.toDouble())
    val c = cos(t).toFloat()
    val s = sin(t).toFloat()
    out.set(l.cx() + lx * c - ly * s, l.cy() + lx * s + ly * c)
}

fun layerContains(l: Layer, px: Float, py: Float): Boolean {
    val t = Math.toRadians(l.rot.toDouble())
    val c = cos(t).toFloat()
    val s = sin(t).toFloat()
    val dx = px - l.cx()
    val dy = py - l.cy()
    val lx = dx * c + dy * s
    val ly = -dx * s + dy * c
    return abs(lx) <= l.w / 2f && abs(ly) <= l.h / 2f
}

fun layerBounds(l: Layer, out: RectF) {
    val p = PointF()
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    val signs = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)
    for (i in 0 until 4) {
        layerPoint(l, signs[i * 2] * l.w / 2f, signs[i * 2 + 1] * l.h / 2f, p)
        minX = min(minX, p.x); minY = min(minY, p.y)
        maxX = max(maxX, p.x); maxY = max(maxY, p.y)
    }
    out.set(minX, minY, maxX, maxY)
}

fun normAngle(v: Float): Float {
    var r = (v + 180f) % 360f
    if (r < 0f) r += 360f
    return r - 180f
}

fun makeChecker(dens: Float): BitmapShader {
    val s = max(4, (8 * dens).toInt())
    val b = Bitmap.createBitmap(s * 2, s * 2, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    c.drawColor(0xFFDDDDDD.toInt())
    val p = Paint()
    p.color = 0xFFAAAAAA.toInt()
    c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
    c.drawRect(s.toFloat(), s.toFloat(), 2f * s, 2f * s, p)
    return BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

// ---------------------------- Renderização ---------------------------
class Renderer {
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mtx = Matrix()
    private val rSrc = RectF()
    private val rDst = RectF()
    private val rTmp = RectF()

    init {
        stroke.style = Paint.Style.STROKE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
    }

    fun drawDoc(c: Canvas, d: Doc, export: Boolean, sync: Boolean, ui: Float) {
        val dw = d.w.toFloat()
        val dh = d.h.toFloat()
        if (Color.alpha(d.bgColor) > 0) {
            fill.style = Paint.Style.FILL
            fill.color = d.bgColor
            c.drawRect(0f, 0f, dw, dh, fill)
        }
        val bs = d.bgSrc
        if (bs != null) {
            val b0 = if (sync) Images.loadSync(bs) else Images.get(bs)
            if (b0 != null) {
                val b = if (d.bgBlur > 0) (Images.blurred(bs, d.bgBlur) ?: b0) else b0
                coverRect(b.width.toFloat(), b.height.toFloat(), dw, dh, rSrc)
                rDst.set(0f, 0f, dw, dh)
                drawBmp(c, b, rSrc, rDst, 0f, 255)
                if (d.bgDim > 0) {
                    fill.color = Color.argb(d.bgDim * 255 / 100, 0, 0, 0)
                    c.drawRect(0f, 0f, dw, dh, fill)
                }
            }
        }
        for (l in d.layers) if (l.visible) drawLayer(c, l, export, sync, ui)
    }

    private fun drawBmp(c: Canvas, b: Bitmap, s: RectF, dst: RectF, r: Float, alpha: Int) {
        if (s.width() <= 0f || s.height() <= 0f) return
        val sh = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        mtx.setRectToRect(s, dst, Matrix.ScaleToFit.FILL)
        sh.setLocalMatrix(mtx)
        bmpPaint.shader = sh
        bmpPaint.alpha = alpha
        if (r > 0.5f) c.drawRoundRect(dst, r, r, bmpPaint) else c.drawRect(dst, bmpPaint)
        bmpPaint.shader = null
    }

    fun drawLayer(c: Canvas, l: Layer, export: Boolean, sync: Boolean, ui: Float) {
        val src = l.src
        val b = if (src == null) null else if (sync) Images.loadSync(src) else Images.get(src)
        c.save()
        c.translate(l.cx(), l.cy())
        c.rotate(l.rot)
        rDst.set(-l.w / 2f, -l.h / 2f, l.w / 2f, l.h / 2f)
        val r = min(max(0f, l.radius), min(l.w, l.h) / 2f)
        val a = l.opacity.coerceIn(0, 100) * 255 / 100
        if (b != null) {
            if (l.isFrame) frameSrc(l, b, rSrc)
            else rSrc.set(l.cropL * b.width, l.cropT * b.height, l.cropR * b.width, l.cropB * b.height)
            if (l.flipH) {
                c.save(); c.scale(-1f, 1f)
                drawBmp(c, b, rSrc, rDst, r, a)
                c.restore()
            } else drawBmp(c, b, rSrc, rDst, r, a)
        } else if (!export) {
            fill.style = Paint.Style.FILL
            fill.color = 0x66808080
            c.drawRoundRect(rDst, r, r, fill)
            stroke.color = 0xCCFFFFFF.toInt()
            stroke.strokeWidth = 2f * ui
            stroke.pathEffect = DashPathEffect(floatArrayOf(10f * ui, 7f * ui), 0f)
            c.drawRoundRect(rDst, r, r, stroke)
            stroke.pathEffect = null
            val label = when {
                src != null && Images.isMissing(src) -> "Imagem não encontrada"
                src != null -> "Carregando…"
                l.isFrame -> "+ Imagem"
                else -> ""
            }
            if (label.isNotEmpty()) {
                textPaint.textSize = max(11f * ui, min(min(l.w, l.h) * 0.12f, 30f * ui))
                c.drawText(label, 0f, textPaint.textSize * 0.35f, textPaint)
            }
        }
        if (l.border > 0f) {
            stroke.color = l.borderColor
            stroke.alpha = a * Color.alpha(l.borderColor) / 255
            stroke.strokeWidth = l.border
            val hb = l.border / 2f
            rTmp.set(rDst.left + hb, rDst.top + hb, rDst.right - hb, rDst.bottom - hb)
            val rr = max(0f, r - hb)
            c.drawRoundRect(rTmp, rr, rr, stroke)
        }
        c.restore()
    }
}

// ----------------------------- Canvas --------------------------------
class CollageView(private val act: MainActivity) : View(act) {
    private val dens = act.resources.displayMetrics.density
    private val renderer = Renderer()
    var vs = 1f
    var vx = 0f
    var vy = 0f
    private var userView = false

    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint()
    private val checker = Paint()
    private val p = PointF()
    private val path = Path()

    private enum class Mode { NONE, MOVE, RESIZE, ROTATE, PINCH, PAN_CONTENT, PINCH_CONTENT, VIEW_PAN, VIEW_PINCH }

    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var changed = false
    private var tapLayer: Layer? = null
    private var sX = 0f; private var sY = 0f; private var sW = 0f; private var sH = 0f; private var sRot = 0f
    private var sZoom = 1f; private var sPanX = 0f; private var sPanY = 0f
    private var sDist = 1f; private var sAng = 0f; private var sMidX = 0f; private var sMidY = 0f
    private var sVs = 1f; private var sVx = 0f; private var sVy = 0f
    private var lastTap = 0L

    init {
        selPaint.style = Paint.Style.STROKE; selPaint.strokeWidth = 2f * dens; selPaint.color = C_ACCENT
        lockPaint.style = Paint.Style.STROKE; lockPaint.strokeWidth = 2f * dens; lockPaint.color = 0xFFFFA000.toInt()
        lockPaint.pathEffect = DashPathEffect(floatArrayOf(8f * dens, 6f * dens), 0f)
        hFill.color = Color.WHITE
        hStroke.style = Paint.Style.STROKE; hStroke.strokeWidth = 2.5f * dens; hStroke.color = C_ACCENT
        hText.color = C_ACCENT; hText.textAlign = Paint.Align.CENTER; hText.textSize = 13f * dens; hText.isFakeBoldText = true
        borderPaint.style = Paint.Style.STROKE; borderPaint.color = 0x66FFFFFF; borderPaint.strokeWidth = 1f
        checker.shader = makeChecker(dens)
    }

    fun fit() {
        val d = act.doc ?: return
        if (width == 0 || height == 0) return
        val m = 14f * dens
        vs = min((width - 2 * m) / d.w, (height - 2 * m) / d.h)
        if (vs <= 0f) vs = 0.1f
        vx = (width - d.w * vs) / 2f
        vy = (height - d.h * vs) / 2f
        userView = false
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!userView || oldw == 0) fit() else {
            vx += (w - oldw) / 2f
            vy += (h - oldh) / 2f
        }
    }

    private fun toSX(x: Float) = x * vs + vx
    private fun toSY(y: Float) = y * vs + vy

    private fun rotHandle(l: Layer): PointF {
        val q = PointF()
        layerPoint(l, 0f, -l.h / 2f, q)
        val t = Math.toRadians(l.rot.toDouble())
        val off = 34f * dens
        return PointF(toSX(q.x) + sin(t).toFloat() * off, toSY(q.y) - cos(t).toFloat() * off)
    }

    private fun resizeHandle(l: Layer): PointF {
        val q = PointF()
        layerPoint(l, l.w / 2f, l.h / 2f, q)
        return PointF(toSX(q.x), toSY(q.y))
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF0B0B0B.toInt())
        val d = act.doc ?: return
        val left = vx
        val top = vy
        val right = vx + d.w * vs
        val bottom = vy + d.h * vs
        if (Color.alpha(d.bgColor) < 255) c.drawRect(left, top, right, bottom, checker)
        c.save()
        c.clipRect(left, top, right, bottom)
        c.translate(vx, vy)
        c.scale(vs, vs)
        renderer.drawDoc(c, d, false, false, 1f / vs)
        c.restore()
        c.drawRect(left, top, right, bottom, borderPaint)

        val l = act.selected() ?: return
        path.reset()
        layerPoint(l, -l.w / 2f, -l.h / 2f, p); path.moveTo(toSX(p.x), toSY(p.y))
        layerPoint(l, l.w / 2f, -l.h / 2f, p); path.lineTo(toSX(p.x), toSY(p.y))
        layerPoint(l, l.w / 2f, l.h / 2f, p); path.lineTo(toSX(p.x), toSY(p.y))
        layerPoint(l, -l.w / 2f, l.h / 2f, p); path.lineTo(toSX(p.x), toSY(p.y))
        path.close()
        c.drawPath(path, if (l.locked) lockPaint else selPaint)
        if (!l.locked) {
            val rh = rotHandle(l)
            val zh = resizeHandle(l)
            layerPoint(l, 0f, -l.h / 2f, p)
            c.drawLine(toSX(p.x), toSY(p.y), rh.x, rh.y, selPaint)
            val hr = 12f * dens
            c.drawCircle(rh.x, rh.y, hr, hFill); c.drawCircle(rh.x, rh.y, hr, hStroke)
            c.drawText("↻", rh.x, rh.y + hText.textSize * 0.35f, hText)
            c.drawCircle(zh.x, zh.y, hr, hFill); c.drawCircle(zh.x, zh.y, hr, hStroke)
            c.drawText("⤡", zh.x, zh.y + hText.textSize * 0.35f, hText)
        }
    }

    private fun near(x: Float, y: Float, q: PointF) = hypot(x - q.x, y - q.y) < 26f * dens
    private fun dist(e: MotionEvent) = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    private fun ang(e: MotionEvent) =
        Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()

    private fun hitTest(d: Doc, px: Float, py: Float): Layer? {
        for (i in d.layers.indices.reversed()) {
            val l = d.layers[i]
            if (l.visible && !l.locked && layerContains(l, px, py)) return l
        }
        return null
    }

    private fun snapStart(l: Layer?) {
        if (l == null) return
        sX = l.x; sY = l.y; sW = l.w; sH = l.h; sRot = l.rot
        sZoom = l.zoom; sPanX = l.panX; sPanY = l.panY
    }

    private fun begin() {
        if (!changed) { act.pushUndo(); changed = true }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val d = act.doc ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = e.x; downY = e.y
                moved = false; changed = false; tapLayer = null
                mode = Mode.NONE
                val cur = act.selected()
                if (cur != null && !cur.locked && cur.visible) {
                    if (near(e.x, e.y, rotHandle(cur))) mode = Mode.ROTATE
                    else if (near(e.x, e.y, resizeHandle(cur))) mode = Mode.RESIZE
                }
                if (mode == Mode.NONE) {
                    val px = (e.x - vx) / vs
                    val py = (e.y - vy) / vs
                    val hit = if (cur != null && !cur.locked && cur.visible && layerContains(cur, px, py)) cur else hitTest(d, px, py)
                    if (hit != null) {
                        if (hit.id != act.selectedId) act.select(hit.id)
                        tapLayer = hit
                        mode = if (act.contentMode && hit.isFrame && Images.peek(hit.src) != null) Mode.PAN_CONTENT else Mode.MOVE
                    } else {
                        if (act.selectedId != 0L) act.select(0L)
                        mode = Mode.VIEW_PAN
                    }
                }
                snapStart(act.selected())
                sVs = vs; sVx = vx; sVy = vy
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount == 2) {
                    val cur = act.selected()
                    sDist = max(1f, dist(e)); sAng = ang(e)
                    sMidX = (e.getX(0) + e.getX(1)) / 2f
                    sMidY = (e.getY(0) + e.getY(1)) / 2f
                    snapStart(cur)
                    sVs = vs; sVx = vx; sVy = vy
                    moved = true
                    mode = if (cur != null && !cur.locked && cur.visible) {
                        if (act.contentMode && cur.isFrame && Images.peek(cur.src) != null) Mode.PINCH_CONTENT else Mode.PINCH
                    } else Mode.VIEW_PINCH
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(e.x - downX, e.y - downY) > 8f * dens) moved = true
                if (!moved) return true
                val l = act.selected()
                when (mode) {
                    Mode.MOVE -> if (l != null && !l.locked) {
                        begin()
                        l.x = sX + (e.x - downX) / vs
                        l.y = sY + (e.y - downY) / vs
                    }
                    Mode.RESIZE -> if (l != null) { begin(); resize(l, e) }
                    Mode.ROTATE -> if (l != null) { begin(); rotate(l, e) }
                    Mode.PINCH -> if (l != null && e.pointerCount >= 2) {
                        begin()
                        val s = dist(e) / sDist
                        l.w = max(8f, sW * s); l.h = max(8f, sH * s)
                        l.rot = normAngle(sRot + ang(e) - sAng)
                        val ncx = sX + sW / 2f + ((e.getX(0) + e.getX(1)) / 2f - sMidX) / vs
                        val ncy = sY + sH / 2f + ((e.getY(0) + e.getY(1)) / 2f - sMidY) / vs
                        l.x = ncx - l.w / 2f; l.y = ncy - l.h / 2f
                    }
                    Mode.PINCH_CONTENT -> if (l != null && e.pointerCount >= 2) {
                        begin()
                        l.zoom = (sZoom * dist(e) / sDist).coerceIn(1f, 10f)
                    }
                    Mode.PAN_CONTENT -> if (l != null) { begin(); panContent(l, e.x - downX, e.y - downY) }
                    Mode.VIEW_PAN -> {
                        vx = sVx + e.x - downX; vy = sVy + e.y - downY
                        userView = true
                    }
                    Mode.VIEW_PINCH -> if (e.pointerCount >= 2) {
                        val ns = (sVs * dist(e) / sDist).coerceIn(0.03f, 30f)
                        val docX = (sMidX - sVx) / sVs
                        val docY = (sMidY - sVy) / sVs
                        vs = ns
                        vx = (e.getX(0) + e.getX(1)) / 2f - docX * ns
                        vy = (e.getY(0) + e.getY(1)) / 2f - docY * ns
                        userView = true
                    }
                    Mode.NONE -> {}
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == Mode.PINCH || mode == Mode.PINCH_CONTENT || mode == Mode.VIEW_PINCH) mode = Mode.NONE
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (changed) {
                    act.onLayerChanged()
                } else if (!moved && e.actionMasked == MotionEvent.ACTION_UP) {
                    val t = tapLayer
                    if (t != null) {
                        if (t.isFrame && !t.locked && (t.src == null || Images.isMissing(t.src))) act.pickFill(t.id)
                    } else if (mode == Mode.VIEW_PAN) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTap < 350) fit()
                        lastTap = now
                    }
                }
                mode = Mode.NONE
                changed = false
            }
        }
        return true
    }

    private fun resize(l: Layer, e: MotionEvent) {
        val t = Math.toRadians(sRot.toDouble())
        val c = cos(t).toFloat()
        val s = sin(t).toFloat()
        val dx = (e.x - downX) / vs
        val dy = (e.y - downY) / vs
        val ldx = dx * c + dy * s
        val ldy = -dx * s + dy * c
        var nw = sW + ldx
        var nh = sH + ldy
        if (act.keepAspect) {
            val k = ((sW + ldx) * sW + (sH + ldy) * sH) / (sW * sW + sH * sH)
            nw = sW * k; nh = sH * k
        }
        nw = max(8f, nw); nh = max(8f, nh)
        val hw = (nw - sW) / 2f
        val hh = (nh - sH) / 2f
        val cx = sX + sW / 2f + hw * c - hh * s
        val cy = sY + sH / 2f + hw * s + hh * c
        l.w = nw; l.h = nh
        l.x = cx - nw / 2f; l.y = cy - nh / 2f
    }

    private fun rotate(l: Layer, e: MotionEvent) {
        val cx = toSX(sX + sW / 2f)
        val cy = toSY(sY + sH / 2f)
        val a0 = Math.toDegrees(atan2((downY - cy).toDouble(), (downX - cx).toDouble())).toFloat()
        val a1 = Math.toDegrees(atan2((e.y - cy).toDouble(), (e.x - cx).toDouble())).toFloat()
        var r = normAngle(sRot + a1 - a0)
        for (g in floatArrayOf(-180f, -90f, 0f, 90f, 180f)) if (abs(r - g) < 4f) r = g
        l.rot = r
    }

    private fun panContent(l: Layer, sdx: Float, sdy: Float) {
        val b = Images.peek(l.src) ?: return
        val t = Math.toRadians(l.rot.toDouble())
        val c = cos(t).toFloat()
        val s = sin(t).toFloat()
        val dx = sdx / vs
        val dy = sdy / vs
        var lx = dx * c + dy * s
        val ly = -dx * s + dy * c
        if (l.flipH) lx = -lx
        l.panX = sPanX; l.panY = sPanY
        val r = RectF()
        frameSrc(l, b, r)
        val k = r.width() / max(1f, l.w)
        l.panX = sPanX - lx * k / b.width
        l.panY = sPanY - ly * k / b.height
    }
}

// ------------------------ Miniatura de estilo ------------------------
class PreviewView(ctx: Context, private val frames: List<RectF>, private val fixed: List<RectF>,
                  private val aspect: Float, private val bg: Int) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val vw = width.toFloat()
        val vh = height.toFloat()
        var w = vw
        var h = vw / max(0.05f, aspect)
        if (h > vh) { h = vh; w = vh * aspect }
        val ox = (vw - w) / 2f
        val oy = (vh - h) / 2f
        paint.color = if (Color.alpha(bg) == 0) 0xFF888888.toInt() else bg
        c.drawRect(ox, oy, ox + w, oy + h, paint)
        paint.color = 0xFF5C8DFF.toInt()
        for (f in frames) {
            r.set(ox + f.left * w, oy + f.top * h, ox + f.right * w, oy + f.bottom * h)
            r.inset(1.5f, 1.5f)
            c.drawRoundRect(r, 3f, 3f, paint)
        }
        paint.color = 0xFFFFB74D.toInt()
        for (f in fixed) {
            r.set(ox + f.left * w, oy + f.top * h, ox + f.right * w, oy + f.bottom * h)
            c.drawRoundRect(r, 3f, 3f, paint)
        }
    }
}

// ============================== ACTIVITY ==============================
class MainActivity : Activity() {
    companion object {
        const val REQ_IMPORT = 11
        const val REQ_FILL = 12
        const val REQ_BG = 13
        const val REQ_EXPORT = 14
    }

    var doc: Doc? = null
    var selectedId = 0L
    var keepAspect = true
    var contentMode = false

    private var screen = "home"
    private lateinit var canvasView: CollageView
    private lateinit var panel: LinearLayout
    private lateinit var panelScroll: ScrollView
    private var banner: TextView? = null
    private val tabViews = HashMap<String, TextView>()
    private var tab = ""
    private val undoStack = ArrayList<String>()
    private val redoStack = ArrayList<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveProject() }
    private val worker = Executors.newSingleThreadExecutor()
    private var fillTargetId = 0L
    private var exportFormat = "png"
    private var presetIndex = 0
    private var presetOpen = true
    private var customW = "1080"
    private var customH = "1920"
    private var tplGap = 2
    private var tplMargin = 3
    private var tplRadius = 2

    private val palette = intArrayOf(
        Color.WHITE, Color.BLACK, 0xFF2B2B2B.toInt(), 0xFF757575.toInt(), 0xFFBDBDBD.toInt(),
        0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
        0xFF00ACC1.toInt(), 0xFF1E88E5.toInt(), 0xFF3949AB.toInt(), 0xFF8E24AA.toInt(),
        0xFFD81B60.toInt(), 0xFF6D4C41.toInt(), 0xFFFFC1E3.toInt(), 0xFFFFF3E0.toInt()
    )

    private val hideBanner = Runnable {
        val b = banner
        if (b != null) b.animate().alpha(0f).setDuration(300).withEndAction { b.visibility = View.GONE }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Images.app = applicationContext
        window.statusBarColor = C_PANEL
        window.navigationBarColor = C_PANEL
        showHome()
    }

    override fun onPause() {
        super.onPause()
        if (screen == "editor") {
            handler.removeCallbacks(saveRunnable)
            saveProject()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (screen == "editor") {
            if (tab.isNotEmpty()) setTab(tab) else leaveEditor()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ------------------------- Helpers de UI -------------------------
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
    private fun dpf(v: Float): Float = v * resources.displayMetrics.density
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun rounded(color: Int, r: Int = 10, strokeColor: Int = 0, strokeW: Int = 1): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.setCornerRadius(dpf(r.toFloat()))
        if (strokeColor != 0) g.setStroke(dp(strokeW), strokeColor)
        return g
    }

    private fun tv(s: String, size: Float = 14f, color: Int = C_TEXT, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        return t
    }

    private fun btn(s: String, color: Int = C_BTN, onClick: () -> Unit): TextView {
        val t = tv(s, 13f)
        t.gravity = Gravity.CENTER
        t.background = rounded(color, 8)
        t.setPadding(dp(6), dp(10), dp(6), dp(10))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun vbox(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    private fun hrow(vararg views: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (v in views) {
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            r.addView(v, lp)
        }
        return r
    }

    private fun lp(w: Int, h: Int, weight: Float = 0f, top: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.topMargin = top
        return p
    }

    private fun section(title: String): TextView {
        val t = tv(title, 12f, C_DIM, true)
        t.setPadding(dp(4), dp(14), dp(4), dp(4))
        return t
    }

    private fun numField(v: Float): EditText {
        val e = EditText(this)
        e.setText(fmt(v))
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        e.setTextColor(C_TEXT)
        e.textSize = 14f
        e.setSingleLine()
        e.background = rounded(C_BTN, 6)
        e.setPadding(dp(8), dp(8), dp(8), dp(8))
        return e
    }

    private fun labeled(label: String, v: View): LinearLayout {
        val b = vbox()
        b.addView(tv(label, 11f, C_DIM))
        b.addView(v)
        return b
    }

    private fun EditText.num(def: Float): Float = text.toString().replace(',', '.').trim().toFloatOrNull() ?: def

    private fun fmt(v: Float): String =
        if (abs(v - v.roundToInt()) < 0.05f) v.roundToInt().toString() else String.format(Locale.US, "%.1f", v)

    private fun stamp(t: Long, pattern: String = "dd/MM/yyyy HH:mm"): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(t))

    private fun askText(title: String, initial: String, onOk: (String) -> Unit) {
        val e = EditText(this)
        e.setText(initial)
        e.setSingleLine()
        e.setTextColor(C_TEXT)
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(e)
        AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton("OK") { _, _ ->
                val s = e.text.toString().trim()
                if (s.isNotEmpty()) onOk(s)
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun confirmDialog(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg)
            .setPositiveButton("Sim") { _, _ -> onYes() }
            .setNegativeButton("Não", null).show()
    }

    private fun slider(label: String, lo: Int, hi: Int, value: Int, suffix: String,
                       undoable: Boolean = true, onChange: (Int) -> Unit): View {
        val box = vbox()
        box.setPadding(0, dp(4), 0, 0)
        val t = tv("$label: $value$suffix", 12f, C_DIM)
        val sb = SeekBar(this)
        sb.max = hi - lo
        sb.progress = (value - lo).coerceIn(0, hi - lo)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = progress + lo
                t.text = "$label: $v$suffix"
                onChange(v)
                if (screen == "editor") canvasView.invalidate()
            }
            override fun onStartTrackingTouch(s: SeekBar?) { if (undoable) pushUndo() }
            override fun onStopTrackingTouch(s: SeekBar?) { if (undoable) dirty() }
        })
        box.addView(t)
        box.addView(sb)
        return box
    }

    private fun swatches(current: Int, withTransparent: Boolean, onPick: (Int) -> Unit): View {
        val colors = ArrayList<Int>()
        if (withTransparent) colors.add(Color.TRANSPARENT)
        for (c in palette) colors.add(c)
        val perRow = 9
        val box = vbox()
        var row = LinearLayout(this)
        for ((i, c) in colors.withIndex()) {
            if (i % perRow == 0) {
                row = LinearLayout(this)
                box.addView(row)
            }
            val v = tv(if (c == Color.TRANSPARENT) "∅" else "", 14f, C_TEXT)
            v.gravity = Gravity.CENTER
            val sel = c == current
            v.background = rounded(if (c == Color.TRANSPARENT) C_BTN else c, 6,
                if (sel) C_ACCENT else 0x55FFFFFF, if (sel) 3 else 1)
            v.setOnClickListener { onPick(c) }
            val p = LinearLayout.LayoutParams(0, dp(32), 1f)
            p.setMargins(dp(2), dp(2), dp(2), dp(2))
            row.addView(v, p)
        }
        val rem = colors.size % perRow
        if (rem != 0) {
            for (k in 0 until perRow - rem) {
                val p = LinearLayout.LayoutParams(0, dp(32), 1f)
                p.setMargins(dp(2), dp(2), dp(2), dp(2))
                row.addView(View(this), p)
            }
        }
        return box
    }

    // ============================ HOME ===============================
    private fun showHome() {
        screen = "home"
        handler.removeCallbacks(saveRunnable)
        doc = null; selectedId = 0L; tab = ""
        Images.listener = null
        Images.clear()

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(C_BG)
        scroll.isFillViewport = true
        val root = vbox()
        root.setPadding(dp(16), dp(20), dp(16), dp(28))
        scroll.addView(root)

        root.addView(tv("Colagem Shorts", 26f, C_TEXT, true))
        val sub = tv("Monte colagens para os seus vídeos", 13f, C_DIM)
        sub.setPadding(0, dp(2), 0, dp(16))
        root.addView(sub)

        // --------- Menu colapsável de proporção ---------
        val card = vbox()
        card.background = rounded(C_CARD, 14)
        card.setPadding(dp(14), dp(10), dp(14), dp(14))
        val header = tv("", 15f, C_TEXT, true)
        header.setPadding(0, dp(8), 0, dp(8))
        val body = vbox()
        val ew = numField(customW.toFloatOrNull() ?: 1080f)
        val eh = numField(customH.toFloatOrNull() ?: 1920f)
        ew.inputType = InputType.TYPE_CLASS_NUMBER
        eh.inputType = InputType.TYPE_CLASS_NUMBER
        val customRow = vbox()
        customRow.setPadding(0, dp(10), 0, 0)
        customRow.addView(tv("Resolução personalizada (px, de 64 a 4096)", 12f, C_DIM))
        customRow.addView(hrow(labeled("Largura", ew), labeled("Altura", eh)))
        val rowViews = ArrayList<LinearLayout>()

        fun refresh() {
            val p = PRESETS[presetIndex]
            val sel = if (p.w == 0) "Personalizada" else "${p.label} (${p.w}×${p.h})"
            header.text = (if (presetOpen) "▾  " else "▸  ") + "Proporção: " + sel
            body.visibility = if (presetOpen) View.VISIBLE else View.GONE
            for ((i, r) in rowViews.withIndex()) {
                r.background = if (i == presetIndex) rounded(C_ACCENT_DARK, 10, C_ACCENT, 2) else rounded(C_BTN, 10)
            }
            customRow.visibility = if (p.w == 0) View.VISIBLE else View.GONE
        }

        for ((i, p) in PRESETS.withIndex()) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(10), dp(10), dp(10), dp(10))
            val box = FrameLayout(this)
            val shape = View(this)
            shape.background = rounded(0, 3, C_TEXT, 2)
            val pw = if (p.w == 0) 1f else p.w.toFloat()
            val ph = if (p.h == 0) 1f else p.h.toFloat()
            val k = 28f / max(pw, ph)
            box.addView(shape, FrameLayout.LayoutParams(dp((pw * k).roundToInt()), dp((ph * k).roundToInt()), Gravity.CENTER))
            r.addView(box, LinearLayout.LayoutParams(dp(36), dp(36)))
            val texts = vbox()
            texts.setPadding(dp(12), 0, 0, 0)
            texts.addView(tv(p.label + "  •  " + p.sub, 14f, C_TEXT, true))
            texts.addView(tv(if (p.w == 0) "Defina largura e altura" else "${p.w} × ${p.h} px", 12f, C_DIM))
            r.addView(texts)
            r.setOnClickListener { presetIndex = i; refresh() }
            rowViews.add(r)
            body.addView(r, lp(MATCH, WRAP, 0f, dp(6)))
        }
        body.addView(customRow)
        header.setOnClickListener { presetOpen = !presetOpen; refresh() }
        card.addView(header)
        card.addView(body)
        root.addView(card)
        refresh()

        val confirm = btn("Confirmar", C_ACCENT) {
            val p = PRESETS[presetIndex]
            var w = p.w
            var h = p.h
            if (w == 0) {
                customW = ew.text.toString().trim()
                customH = eh.text.toString().trim()
                w = customW.toIntOrNull() ?: 0
                h = customH.toIntOrNull() ?: 0
                if (w !in 64..4096 || h !in 64..4096) {
                    toast("Use valores entre 64 e 4096 px")
                    return@btn
                }
            }
            val d = Doc()
            d.id = System.currentTimeMillis().toString()
            d.name = "Colagem " + stamp(System.currentTimeMillis(), "dd/MM HH:mm")
            d.w = w; d.h = h
            d.bgColor = Color.WHITE
            doc = d
            saveProject()
            openEditor(d)
        }
        confirm.textSize = 16f
        root.addView(confirm, lp(MATCH, WRAP, 0f, dp(12)))

        // --------- Histórico ---------
        root.addView(section("HISTÓRICO DE EDIÇÕES"))
        val list = listProjects()
        if (list.isEmpty()) {
            root.addView(tv("Nenhuma edição ainda. Seus projetos aparecem aqui automaticamente.", 13f, C_DIM))
        }
        for (p in list) root.addView(projectRow(p), lp(MATCH, WRAP, 0f, dp(8)))
        if (list.isNotEmpty()) {
            val hint = tv("Toque para continuar editando • segure para mais opções", 11f, C_DIM)
            hint.setPadding(dp(4), dp(10), 0, 0)
            root.addView(hint)
        }
        setContentView(scroll)
    }

    private fun projectRow(p: Doc): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = rounded(C_CARD, 12)
        r.setPadding(dp(10), dp(10), dp(10), dp(10))
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.background = rounded(C_BTN, 6)
        val f = File(projDir(), p.id + ".jpg")
        if (f.exists()) {
            val bm = try { BitmapFactory.decodeFile(f.absolutePath) } catch (t: Throwable) { null }
            if (bm != null) iv.setImageBitmap(bm)
        }
        val ih = dp(76)
        val iw = max(dp(34), (ih * p.w / p.h.toFloat()).toInt()).coerceAtMost(dp(120))
        r.addView(iv, LinearLayout.LayoutParams(iw, ih))
        val t = vbox()
        t.setPadding(dp(12), 0, 0, 0)
        t.addView(tv(p.name, 15f, C_TEXT, true))
        t.addView(tv("${p.w}×${p.h} • ${p.layers.size} camada(s)", 12f, C_DIM))
        t.addView(tv("Editado em " + stamp(p.updated), 12f, C_DIM))
        r.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        r.setOnClickListener { openEditor(p) }
        r.setOnLongClickListener { projectMenu(p); true }
        return r
    }

    private fun projectMenu(p: Doc) {
        AlertDialog.Builder(this).setTitle(p.name)
            .setItems(arrayOf("Abrir", "Renomear", "Duplicar", "Excluir")) { _, w ->
                when (w) {
                    0 -> openEditor(p)
                    1 -> askText("Renomear projeto", p.name) { n -> p.name = n; writeProject(p, false); showHome() }
                    2 -> {
                        val c = Doc.fromJson(p.toJson())
                        c.id = System.currentTimeMillis().toString()
                        c.name = p.name + " (cópia)"
                        c.updated = System.currentTimeMillis()
                        writeProject(c, false)
                        val th = File(projDir(), p.id + ".jpg")
                        if (th.exists()) try { th.copyTo(File(projDir(), c.id + ".jpg"), true) } catch (e: Exception) { }
                        showHome()
                    }
                    3 -> confirmDialog("Excluir \"${p.name}\" do histórico?") {
                        File(projDir(), p.id + ".json").delete()
                        File(projDir(), p.id + ".jpg").delete()
                        showHome()
                    }
                }
            }.show()
    }

    // ------------------------- Armazenamento -------------------------
    private fun projDir(): File {
        val f = File(filesDir, "projects")
        f.mkdirs()
        return f
    }

    private fun listProjects(): List<Doc> {
        val files = projDir().listFiles() ?: return emptyList()
        return files.filter { it.name.endsWith(".json") }
            .mapNotNull { try { Doc.fromJson(JSONObject(it.readText())) } catch (e: Exception) { null } }
            .sortedByDescending { it.updated }
    }

    private fun writeProject(d: Doc, thumb: Boolean) {
        try {
            val tmp = File(projDir(), d.id + ".tmp")
            tmp.writeText(d.toJson().toString())
            tmp.renameTo(File(projDir(), d.id + ".json"))
            if (thumb) {
                val tw = 320
                val s = tw / d.w.toFloat()
                val th = max(1, (d.h * s).roundToInt())
                val b = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                c.drawColor(Color.WHITE)
                c.scale(s, s)
                Renderer().drawDoc(c, d, true, false, 1f)
                File(projDir(), d.id + ".jpg").outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                b.recycle()
            }
        } catch (t: Throwable) { }
    }

    private fun saveProject() {
        val d = doc ?: return
        d.updated = System.currentTimeMillis()
        writeProject(d, true)
    }

    private fun stylesFile() = File(filesDir, "styles.json")

    private fun loadStyles(): JSONArray = try {
        val f = stylesFile()
        if (f.exists()) JSONArray(f.readText()) else JSONArray()
    } catch (e: Exception) { JSONArray() }

    private fun saveStyles(a: JSONArray) {
        try { stylesFile().writeText(a.toString()) } catch (e: Exception) { }
    }

    // =========================== EDITOR ==============================
    private fun openEditor(d: Doc) {
        screen = "editor"
        doc = d
        selectedId = 0L; tab = ""; contentMode = false
        undoStack.clear(); redoStack.clear()
        Images.clear()
        Images.maxDim = max(d.w, d.h).coerceIn(1280, 3000)

        val root = vbox()
        root.setBackgroundColor(C_BG)

        // Barra superior
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(6), dp(6), dp(6), dp(6))
        bar.setBackgroundColor(C_PANEL)
        val back = btn("←") { leaveEditor() }
        val title = tv(d.name + "\n${d.w}×${d.h}", 12f, C_DIM)
        title.setPadding(dp(10), 0, dp(6), 0)
        title.maxLines = 2
        title.setOnClickListener {
            askText("Nome do projeto", d.name) { n -> d.name = n; title.text = n + "\n${d.w}×${d.h}"; dirty() }
        }
        val undoB = btn("↶") { doUndo() }
        val redoB = btn("↷") { doRedo() }
        val save = btn("💾 Salvar", C_ACCENT) { askExport() }
        bar.addView(back, LinearLayout.LayoutParams(dp(46), dp(42)))
        bar.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        val ulp = LinearLayout.LayoutParams(dp(46), dp(42)); ulp.rightMargin = dp(4)
        bar.addView(undoB, ulp)
        val rlp = LinearLayout.LayoutParams(dp(46), dp(42)); rlp.rightMargin = dp(6)
        bar.addView(redoB, rlp)
        save.setPadding(dp(12), dp(10), dp(12), dp(10))
        bar.addView(save, LinearLayout.LayoutParams(WRAP, dp(42)))
        root.addView(bar)

        // Área do canvas
        val frame = FrameLayout(this)
        canvasView = CollageView(this)
        frame.addView(canvasView, FrameLayout.LayoutParams(MATCH, MATCH))
        val bn = tv("✅  Imagem salva", 15f, Color.WHITE, true)
        bn.background = rounded(C_OK, 22)
        bn.setPadding(dp(18), dp(10), dp(18), dp(10))
        bn.visibility = View.GONE
        val blp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        blp.topMargin = dp(14)
        frame.addView(bn, blp)
        banner = bn
        root.addView(frame, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Painel de opções
        panelScroll = ScrollView(this)
        panelScroll.setBackgroundColor(C_PANEL)
        panelScroll.visibility = View.GONE
        panel = vbox()
        panel.setPadding(dp(10), dp(8), dp(10), dp(16))
        panelScroll.addView(panel)
        root.addView(panelScroll, LinearLayout.LayoutParams(MATCH, (resources.displayMetrics.heightPixels * 0.42f).toInt()))

        // Abas (menu estilo Photoshop)
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.setBackgroundColor(C_PANEL)
        tabs.setPadding(dp(4), dp(4), dp(4), dp(6))
        tabViews.clear()
        val items = listOf(
            "import" to "📥\nImportar", "layers" to "🗂\nCamadas", "props" to "🎛\nAjustes",
            "styles" to "🧩\nEstilos", "bg" to "🎨\nFundo"
        )
        for ((key, label) in items) {
            val t = tv(label, 11f, C_TEXT)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(6), 0, dp(6))
            t.background = rounded(C_PANEL, 8)
            t.setOnClickListener { setTab(key) }
            tabViews[key] = t
            val p = LinearLayout.LayoutParams(0, WRAP, 1f)
            p.setMargins(dp(2), 0, dp(2), 0)
            tabs.addView(t, p)
        }
        root.addView(tabs)
        setContentView(root)

        Images.listener = {
            if (screen == "editor") {
                canvasView.invalidate()
                if (tab == "layers") refreshPanel()
            }
        }
        for (l in d.layers) Images.request(l.src)
        Images.request(d.bgSrc)
    }

    private fun leaveEditor() {
        handler.removeCallbacks(saveRunnable)
        saveProject()
        showHome()
    }

    private fun setTab(t: String) {
        if (t == "import") { pickImages(REQ_IMPORT, true); return }
        tab = if (tab == t) "" else t
        panelScroll.visibility = if (tab.isEmpty()) View.GONE else View.VISIBLE
        for ((k, v) in tabViews) v.background = rounded(if (k == tab) C_ACCENT_DARK else C_PANEL, 8)
        panelScroll.scrollTo(0, 0)
        refreshPanel()
    }

    private fun refreshPanel() {
        if (screen != "editor" || tab.isEmpty()) return
        val y = panelScroll.scrollY
        panel.removeAllViews()
        when (tab) {
            "layers" -> buildLayers()
            "props" -> buildProps()
            "styles" -> buildStyles()
            "bg" -> buildBg()
        }
        panelScroll.post { panelScroll.scrollTo(0, y) }
    }

    fun selected(): Layer? {
        val d = doc ?: return null
        if (selectedId == 0L) return null
        return d.find(selectedId)
    }

    fun select(id: Long) {
        selectedId = id
        if (screen == "editor") {
            canvasView.invalidate()
            if (tab == "props" || tab == "layers") refreshPanel()
        }
    }

    fun pushUndo() {
        val d = doc ?: return
        undoStack.add(d.toJson().toString())
        if (undoStack.size > 60) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun dirty() {
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 900)
    }

    fun onLayerChanged() {
        dirty()
        if (tab == "props" || tab == "layers") refreshPanel()
    }

    private fun commit() {
        if (screen != "editor") return
        canvasView.invalidate()
        dirty()
        refreshPanel()
    }

    private fun doUndo() {
        val d = doc ?: return
        if (undoStack.isEmpty()) { toast("Nada para desfazer"); return }
        redoStack.add(d.toJson().toString())
        restore(undoStack.removeAt(undoStack.size - 1))
    }

    private fun doRedo() {
        val d = doc ?: return
        if (redoStack.isEmpty()) { toast("Nada para refazer"); return }
        undoStack.add(d.toJson().toString())
        restore(redoStack.removeAt(redoStack.size - 1))
    }

    private fun restore(json: String) {
        val old = doc ?: return
        val n = Doc.fromJson(JSONObject(json))
        n.id = old.id
        n.name = old.name
        doc = n
        if (n.find(selectedId) == null) selectedId = 0L
        for (l in n.layers) Images.request(l.src)
        Images.request(n.bgSrc)
        commit()
    }

    private fun showBanner(s: String) {
        val b = banner ?: return
        b.text = s
        b.animate().cancel()
        b.alpha = 1f
        b.visibility = View.VISIBLE
        handler.removeCallbacks(hideBanner)
        handler.postDelayed(hideBanner, 2600)
    }

    // -------------------- Seletores de arquivo -----------------------
    private fun pickImages(req: Int, multiple: Boolean) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        if (multiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try { startActivityForResult(i, req) } catch (e: Exception) { toast("Seletor de arquivos indisponível") }
    }

    fun pickFill(id: Long) {
        fillTargetId = id
        pickImages(REQ_FILL, false)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null || doc == null) return
        if (requestCode == REQ_EXPORT) {
            val u = data.data
            if (u != null) doExport(u)
            return
        }
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (k in 0 until clip.itemCount) uris.add(clip.getItemAt(k).uri)
        } else {
            val u = data.data
            if (u != null) uris.add(u)
        }
        for (u in uris) {
            try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
        }
        when (requestCode) {
            REQ_IMPORT -> handleImport(uris)
            REQ_FILL -> { val u = uris.firstOrNull(); if (u != null) fillLayer(u) }
            REQ_BG -> { val u = uris.firstOrNull(); if (u != null) setBg(u) }
        }
    }

    private fun emptyFrames(d: Doc): List<Layer> =
        d.layers.filter { it.isFrame && !it.locked && (it.src == null || Images.isMissing(it.src)) }

    private fun handleImport(uris: List<Uri>) {
        val d = doc ?: return
        if (uris.isEmpty()) return
        val empty = emptyFrames(d)
        if (empty.isEmpty()) { importImages(uris, false); return }
        AlertDialog.Builder(this).setTitle("${uris.size} imagem(ns) selecionada(s)")
            .setItems(arrayOf("Preencher as ${empty.size} forma(s) vazia(s) em ordem", "Adicionar como novas camadas")) { _, w ->
                importImages(uris, w == 0)
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun importImages(uris: List<Uri>, fill: Boolean) {
        val d = doc ?: return
        pushUndo()
        var i = 0
        if (fill) {
            for (f in emptyFrames(d)) {
                if (i >= uris.size) break
                val s = uris[i].toString()
                f.src = s; f.zoom = 1f; f.panX = 0f; f.panY = 0f
                Images.forget(s); Images.request(s)
                i++
            }
        }
        var last = 0L
        var n = 0
        while (i < uris.size) {
            last = addImageLayer(d, uris[i], n)
            n++; i++
        }
        if (last != 0L) selectedId = last
        commit()
        if (fill) toast(if (n == 0) "Formas preenchidas" else "Formas preenchidas • $n imagem(ns) extra(s) adicionada(s)")
    }

    private fun addImageLayer(d: Doc, u: Uri, offset: Int): Long {
        val sz = readImageSize(this, u) ?: intArrayOf(1000, 1000)
        val s = min(d.w * 0.8f / sz[0], d.h * 0.8f / sz[1])
        val l = Layer()
        l.id = newId()
        l.name = "Imagem " + (d.layers.count { !it.isFrame } + 1)
        val src = u.toString()
        l.src = src
        l.w = sz[0] * s; l.h = sz[1] * s
        l.x = (d.w - l.w) / 2f + offset * 30f
        l.y = (d.h - l.h) / 2f + offset * 30f
        d.layers.add(l)
        Images.forget(src); Images.request(src)
        return l.id
    }

    private fun fillLayer(u: Uri) {
        val d = doc ?: return
        val l = d.find(fillTargetId) ?: return
        pushUndo()
        val s = u.toString()
        l.src = s; l.zoom = 1f; l.panX = 0f; l.panY = 0f
        if (!l.isFrame) {
            l.cropL = 0f; l.cropT = 0f; l.cropR = 1f; l.cropB = 1f
            val sz = readImageSize(this, u)
            if (sz != null) {
                val cx = l.cx(); val cy = l.cy()
                l.h = l.w * sz[1] / sz[0].toFloat()
                l.x = cx - l.w / 2f; l.y = cy - l.h / 2f
            }
        }
        Images.forget(s); Images.request(s)
        selectedId = l.id
        commit()
    }

    private fun setBg(u: Uri) {
        val d = doc ?: return
        pushUndo()
        val s = u.toString()
        d.bgSrc = s
        Images.forget(s); Images.request(s)
        commit()
    }

    // ---------------------------- Exportar ---------------------------
    private fun askExport() {
        AlertDialog.Builder(this).setTitle("Salvar imagem final")
            .setItems(arrayOf("PNG  (máxima qualidade)", "JPG  (arquivo menor)")) { _, w ->
                exportFormat = if (w == 0) "png" else "jpg"
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = if (exportFormat == "png") "image/png" else "image/jpeg"
                i.putExtra(Intent.EXTRA_TITLE, "colagem_" + stamp(System.currentTimeMillis(), "yyyyMMdd_HHmmss") + "." + exportFormat)
                try { startActivityForResult(i, REQ_EXPORT) } catch (e: Exception) { toast("Não foi possível abrir o seletor de pastas") }
            }.show()
    }

    private fun doExport(uri: Uri) {
        val d = doc ?: return
        val snap = Doc.fromJson(d.toJson())
        val fmt = exportFormat
        toast("Salvando…")
        worker.execute {
            var ok = false
            var err = ""
            try {
                val bmp = Bitmap.createBitmap(snap.w, snap.h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                if (fmt == "jpg") c.drawColor(Color.WHITE)
                Renderer().drawDoc(c, snap, true, true, 1f)
                val os = contentResolver.openOutputStream(uri)
                if (os != null) {
                    os.use {
                        bmp.compress(if (fmt == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it)
                    }
                    ok = true
                } else err = "não foi possível abrir o arquivo"
                bmp.recycle()
            } catch (t: Throwable) {
                err = t.message ?: t.toString()
            }
            handler.post {
                if (ok) showBanner("✅  Imagem salva") else toast("Erro ao salvar: $err")
            }
        }
    }

    // ========================= PAINEL: AJUSTES =======================
    private fun buildProps() {
        val d = doc ?: return
        val l = selected()
        if (l == null) {
            panel.addView(tv("Toque numa imagem ou forma no canvas (ou escolha em 🗂 Camadas) para ver as opções.", 13f, C_DIM))
            return
        }
        panel.addView(tv(l.name + "  •  " + (if (l.isFrame) "Forma (moldura)" else "Imagem"), 15f, C_TEXT, true))
        if (l.locked) {
            panel.addView(tv("🔒 Esta camada está bloqueada.", 13f, C_DIM))
            panel.addView(hrow(btn("🔓 Desbloquear", C_ACCENT) { pushUndo(); l.locked = false; commit() }))
            return
        }

        panel.addView(section("POSIÇÃO E TAMANHO (px)"))
        val ex = numField(l.x)
        val ey = numField(l.y)
        val ew = numField(l.w)
        val eh = numField(l.h)
        panel.addView(hrow(labeled("X", ex), labeled("Y", ey), labeled("Largura", ew), labeled("Altura", eh)))
        val keep = CheckBox(this)
        keep.text = "Manter proporção"
        keep.setTextColor(C_TEXT)
        keep.isChecked = keepAspect
        keep.setOnCheckedChangeListener { _, c -> keepAspect = c }
        panel.addView(keep)
        panel.addView(hrow(
            btn("Aplicar", C_ACCENT) {
                val nx = ex.num(l.x)
                val ny = ey.num(l.y)
                var nw = max(8f, ew.num(l.w))
                var nh = max(8f, eh.num(l.h))
                if (keepAspect) {
                    val a = l.w / l.h
                    if (abs(nw - l.w) > 0.5f) nh = nw / a else if (abs(nh - l.h) > 0.5f) nw = nh * a
                }
                pushUndo()
                l.x = nx; l.y = ny; l.w = nw; l.h = nh
                commit()
            },
            btn("Centralizar") { pushUndo(); l.x = (d.w - l.w) / 2f; l.y = (d.h - l.h) / 2f; commit() }
        ))
        panel.addView(hrow(
            btn("Caber na tela") { fitLayer(l, false) },
            btn("Preencher tela") { fitLayer(l, true) },
            btn("Proporção original") { originalRatio(l) }
        ))

        panel.addView(section("APARÊNCIA"))
        val minHalf = min(l.w, l.h) / 2f
        val radPct = if (minHalf > 0f) (l.radius / minHalf * 100f).roundToInt().coerceIn(0, 100) else 0
        panel.addView(slider("Cantos arredondados", 0, 100, radPct, "%") { v -> l.radius = v / 100f * min(l.w, l.h) / 2f })
        panel.addView(slider("Rotação", -180, 180, l.rot.roundToInt(), "°") { v -> l.rot = v.toFloat() })
        panel.addView(slider("Opacidade", 0, 100, l.opacity, "%") { v -> l.opacity = v })
        panel.addView(slider("Borda", 0, 100, l.border.roundToInt(), " px") { v -> l.border = v.toFloat() })
        panel.addView(tv("Cor da borda", 12f, C_DIM))
        panel.addView(swatches(l.borderColor, false) { c -> pushUndo(); l.borderColor = c; commit() })

        if (l.isFrame) {
            panel.addView(section("IMAGEM DENTRO DA FORMA"))
            val hasImg = l.src != null
            panel.addView(hrow(
                btn(if (hasImg) "🖼 Trocar imagem" else "🖼 Escolher imagem", C_ACCENT) { pickFill(l.id) },
                btn("Remover imagem") { pushUndo(); l.src = null; commit() }
            ))
            if (hasImg) {
                panel.addView(slider("Zoom da imagem", 100, 800, (l.zoom * 100).roundToInt(), "%") { v -> l.zoom = v / 100f })
                val cm = CheckBox(this)
                cm.text = "Arrastar move a imagem DENTRO da forma"
                cm.setTextColor(C_TEXT)
                cm.isChecked = contentMode
                cm.setOnCheckedChangeListener { _, c -> contentMode = c }
                panel.addView(cm)
                panel.addView(tv("Ligado: 1 dedo arrasta a imagem e 2 dedos dão zoom, sem mexer na forma.", 11f, C_DIM))
            }
        } else {
            panel.addView(section("IMAGEM"))
            panel.addView(hrow(btn("✂ Recortar") { cropDialog(l) }, btn("🖼 Trocar imagem") { pickFill(l.id) }))
        }
        panel.addView(hrow(
            btn("⇋ Espelhar") { pushUndo(); l.flipH = !l.flipH; commit() },
            btn(if (l.isFrame) "Virar imagem comum" else "Virar forma (moldura)") { toggleFrame(l) }
        ))

        panel.addView(section("CAMADA"))
        panel.addView(hrow(
            btn("⧉ Duplicar") { duplicate(l) },
            btn("🔒 Bloquear") { pushUndo(); l.locked = true; commit() },
            btn("🗑 Excluir", C_DANGER) { deleteLayer(l) }
        ))
    }

    private fun fitLayer(l: Layer, cover: Boolean) {
        val d = doc ?: return
        pushUndo()
        if (l.isFrame && cover) {
            l.x = 0f; l.y = 0f; l.w = d.w.toFloat(); l.h = d.h.toFloat()
        } else {
            val s = if (cover) max(d.w / l.w, d.h / l.h) else min(d.w / l.w, d.h / l.h)
            l.w *= s; l.h *= s
            l.x = (d.w - l.w) / 2f; l.y = (d.h - l.h) / 2f
        }
        l.rot = 0f
        commit()
    }

    private fun originalRatio(l: Layer) {
        val b = Images.peek(l.src)
        if (b == null) { toast("A imagem ainda não carregou"); return }
        pushUndo()
        val cx = l.cx()
        val cy = l.cy()
        if (l.isFrame) {
            l.h = l.w * b.height / b.width.toFloat()
            l.zoom = 1f; l.panX = 0f; l.panY = 0f
        } else {
            val a = ((l.cropR - l.cropL) * b.width) / ((l.cropB - l.cropT) * b.height)
            l.h = l.w / a
        }
        l.x = cx - l.w / 2f; l.y = cy - l.h / 2f
        commit()
    }

    private fun toggleFrame(l: Layer) {
        pushUndo()
        if (l.isFrame) {
            val b = Images.peek(l.src)
            if (b != null) {
                val r = RectF()
                frameSrc(l, b, r)
                l.cropL = r.left / b.width; l.cropT = r.top / b.height
                l.cropR = r.right / b.width; l.cropB = r.bottom / b.height
            } else {
                l.cropL = 0f; l.cropT = 0f; l.cropR = 1f; l.cropB = 1f
            }
            l.isFrame = false
        } else {
            l.isFrame = true
            l.zoom = 1f; l.panX = 0f; l.panY = 0f
        }
        commit()
    }

    private fun duplicate(l: Layer) {
        val d = doc ?: return
        pushUndo()
        val c = Layer.fromJson(l.toJson())
        c.id = newId()
        c.name = l.name + " cópia"
        c.x += 30f; c.y += 30f
        c.locked = false
        d.layers.add(d.layers.indexOf(l) + 1, c)
        selectedId = c.id
        commit()
    }

    private fun deleteLayer(l: Layer) {
        val d = doc ?: return
        pushUndo()
        d.layers.remove(l)
        selectedId = 0L
        commit()
    }

    private fun cropDialog(l: Layer) {
        val b = Images.peek(l.src)
        if (b == null) { toast("A imagem ainda não carregou"); return }
        val box = vbox()
        box.setPadding(dp(18), dp(8), dp(18), dp(8))

        fun setCrop(nl: Float, nt: Float, nr: Float, nb: Float) {
            val kx = l.w / (l.cropR - l.cropL)
            val ky = l.h / (l.cropB - l.cropT)
            l.x += (nl - l.cropL) * kx
            l.y += (nt - l.cropT) * ky
            l.cropL = nl; l.cropT = nt; l.cropR = nr; l.cropB = nb
            l.w = (nr - nl) * kx
            l.h = (nb - nt) * ky
            canvasView.invalidate()
        }

        box.addView(tv("Corte as bordas da imagem:", 13f, C_TEXT))
        box.addView(slider("Esquerda", 0, 95, (l.cropL * 100).roundToInt(), "%") { v ->
            setCrop(min(v / 100f, l.cropR - 0.05f), l.cropT, l.cropR, l.cropB) })
        box.addView(slider("Topo", 0, 95, (l.cropT * 100).roundToInt(), "%") { v ->
            setCrop(l.cropL, min(v / 100f, l.cropB - 0.05f), l.cropR, l.cropB) })
        box.addView(slider("Direita", 0, 95, ((1f - l.cropR) * 100).roundToInt(), "%") { v ->
            setCrop(l.cropL, l.cropT, max(1f - v / 100f, l.cropL + 0.05f), l.cropB) })
        box.addView(slider("Base", 0, 95, ((1f - l.cropB) * 100).roundToInt(), "%") { v ->
            setCrop(l.cropL, l.cropT, l.cropR, max(1f - v / 100f, l.cropT + 0.05f)) })
        val lbl = tv("Ou corte numa proporção (centralizado):", 13f, C_TEXT)
        lbl.setPadding(0, dp(12), 0, dp(4))
        box.addView(lbl)

        var dlg: AlertDialog? = null
        val ratios = listOf("1:1" to 1f, "9:16" to 9f / 16f, "16:9" to 16f / 9f, "4:5" to 0.8f, "3:4" to 0.75f)
        val rr = LinearLayout(this)
        for ((name, a) in ratios) {
            val p = LinearLayout.LayoutParams(0, WRAP, 1f)
            p.setMargins(dp(2), dp(2), dp(2), dp(2))
            rr.addView(btn(name) {
                pushUndo()
                val ia = b.width / b.height.toFloat()
                if (ia > a) {
                    val f = a / ia
                    setCrop((1f - f) / 2f, 0f, (1f + f) / 2f, 1f)
                } else {
                    val f = ia / a
                    setCrop(0f, (1f - f) / 2f, 1f, (1f + f) / 2f)
                }
                dirty()
                dlg?.dismiss()
            }, p)
        }
        box.addView(rr)
        val sc = ScrollView(this)
        sc.addView(box)
        val created = AlertDialog.Builder(this).setTitle("✂ Recortar").setView(sc)
            .setPositiveButton("OK", null)
            .setNeutralButton("Sem corte") { _, _ -> pushUndo(); setCrop(0f, 0f, 1f, 1f); dirty() }
            .create()
        created.setOnDismissListener { commit() }
        dlg = created
        created.show()
    }

    // ========================= PAINEL: CAMADAS =======================
    private fun buildLayers() {
        val d = doc ?: return
        panel.addView(hrow(btn("+ Forma vazia") { addFrame() }, btn("+ Imagem") { pickImages(REQ_IMPORT, true) }))
        if (d.layers.isEmpty()) {
            panel.addView(tv("Nenhuma camada. Importe imagens ou aplique um estilo.", 13f, C_DIM))
        }
        for (i in d.layers.indices.reversed()) {
            val l = d.layers[i]
            val sel = l.id == selectedId
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(6), dp(6), dp(6), dp(6))
            r.background = rounded(if (sel) C_ACCENT_DARK else C_CARD, 8, if (sel) C_ACCENT else 0, 1)
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.background = rounded(C_BTN, 4)
            val bm = Images.peek(l.src)
            if (bm != null) iv.setImageBitmap(bm)
            r.addView(iv, LinearLayout.LayoutParams(dp(40), dp(40)))
            val warn = if (l.src != null && Images.isMissing(l.src)) "  ⚠" else ""
            val name = tv((if (l.isFrame) "▢ " else "🖼 ") + l.name + warn, 13f, if (l.visible) C_TEXT else C_DIM)
            name.setPadding(dp(10), 0, dp(4), 0)
            name.maxLines = 1
            r.addView(name, LinearLayout.LayoutParams(0, WRAP, 1f))
            val eye = btn(if (l.visible) "👁" else "🚫") { pushUndo(); l.visible = !l.visible; commit() }
            val lock = btn(if (l.locked) "🔒" else "🔓", if (l.locked) 0xFF8D6E00.toInt() else C_BTN) {
                pushUndo(); l.locked = !l.locked; commit()
            }
            val elp = LinearLayout.LayoutParams(dp(42), dp(38)); elp.leftMargin = dp(4)
            r.addView(eye, elp)
            val llp = LinearLayout.LayoutParams(dp(42), dp(38)); llp.leftMargin = dp(4)
            r.addView(lock, llp)
            r.setOnClickListener { select(l.id) }
            r.setOnLongClickListener {
                askText("Nome da camada", l.name) { n -> pushUndo(); l.name = n; commit() }
                true
            }
            panel.addView(r, lp(MATCH, WRAP, 0f, dp(4)))
        }
        val l = selected() ?: return
        panel.addView(section("CAMADA SELECIONADA: " + l.name.uppercase()))
        panel.addView(hrow(
            btn("⧉ Duplicar") { duplicate(l) },
            btn("▲ Subir") { moveLayer(l, 1) },
            btn("▼ Descer") { moveLayer(l, -1) }
        ))
        panel.addView(hrow(
            btn("⤓ Fundir c/ baixo") { merge(l, true) },
            btn("⤒ Fundir c/ cima") { merge(l, false) },
            btn("🗑 Excluir", C_DANGER) { deleteLayer(l) }
        ))
        val tip = tv("Dica: segure uma camada para renomear. Camadas bloqueadas não se movem no canvas.", 11f, C_DIM)
        tip.setPadding(dp(4), dp(8), dp(4), 0)
        panel.addView(tip)
    }

    private fun addFrame() {
        val d = doc ?: return
        pushUndo()
        val l = Layer()
        l.id = newId()
        l.isFrame = true
        l.name = "Forma " + (d.layers.count { it.isFrame } + 1)
        val s = min(d.w, d.h) * 0.5f
        l.w = s; l.h = s
        l.x = (d.w - s) / 2f; l.y = (d.h - s) / 2f
        d.layers.add(l)
        selectedId = l.id
        commit()
    }

    private fun moveLayer(l: Layer, dir: Int) {
        val d = doc ?: return
        val i = d.layers.indexOf(l)
        val j = i + dir
        if (i < 0 || j < 0 || j >= d.layers.size) { toast(if (dir > 0) "Já está no topo" else "Já está no fundo"); return }
        pushUndo()
        val t = d.layers[i]
        d.layers[i] = d.layers[j]
        d.layers[j] = t
        commit()
    }

    private fun merge(l: Layer, down: Boolean) {
        val d = doc ?: return
        val i = d.layers.indexOf(l)
        val j = if (down) i - 1 else i + 1
        if (i < 0 || j < 0 || j >= d.layers.size) { toast(if (down) "Não há camada abaixo" else "Não há camada acima"); return }
        val lower = d.layers[min(i, j)]
        val upper = d.layers[max(i, j)]
        if (lower.locked || upper.locked) { toast("Desbloqueie as camadas antes de fundir"); return }
        for (x in listOf(lower, upper)) {
            val s = x.src
            if (s != null && !Images.isMissing(s) && Images.peek(s) == null) { toast("Aguarde as imagens carregarem"); return }
        }
        val bounds = RectF()
        val tmp = RectF()
        var first = true
        for (x in listOf(lower, upper)) {
            if (!x.visible) continue
            layerBounds(x, tmp)
            if (first) { bounds.set(tmp); first = false } else bounds.union(tmp)
        }
        if (first || bounds.width() < 1f || bounds.height() < 1f) { toast("Nada visível para fundir"); return }
        val k = min(1f, 3000f / max(bounds.width(), bounds.height()))
        val bw = max(1, (bounds.width() * k).roundToInt())
        val bh = max(1, (bounds.height() * k).roundToInt())
        val bm = try { Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888) } catch (t: Throwable) { null }
        if (bm == null) { toast("Memória insuficiente para fundir"); return }
        val c = Canvas(bm)
        c.scale(k, k)
        c.translate(-bounds.left, -bounds.top)
        val rd = Renderer()
        for (x in listOf(lower, upper)) if (x.visible) rd.drawLayer(c, x, true, false, 1f)
        val dir = File(filesDir, "merged")
        dir.mkdirs()
        val f = File(dir, "fusao_" + System.currentTimeMillis() + ".png")
        try {
            f.outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (t: Throwable) { toast("Erro ao fundir"); return }
        val src = Uri.fromFile(f).toString()
        Images.put(src, bm)
        pushUndo()
        val n = Layer()
        n.id = newId()
        n.name = "Fusão"
        n.src = src
        n.x = bounds.left; n.y = bounds.top; n.w = bounds.width(); n.h = bounds.height()
        val at = min(i, j)
        d.layers.remove(lower)
        d.layers.remove(upper)
        d.layers.add(at, n)
        selectedId = n.id
        commit()
        toast("Camadas fundidas")
    }

    // ========================= PAINEL: ESTILOS =======================
    private fun styleCard(name: String, frames: List<RectF>, fixed: List<RectF>, aspect: Float, bg: Int,
                          onLong: (() -> Unit)? = null, onClick: () -> Unit): View {
        val box = vbox()
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.background = rounded(C_CARD, 8)
        box.setPadding(dp(6), dp(6), dp(6), dp(6))
        box.addView(PreviewView(this, frames, fixed, aspect, bg), LinearLayout.LayoutParams(dp(72), dp(96)))
        val t = tv(name, 11f, C_TEXT)
        t.gravity = Gravity.CENTER
        t.maxLines = 2
        box.addView(t)
        box.setOnClickListener { onClick() }
        if (onLong != null) box.setOnLongClickListener { onLong(); true }
        return box
    }

    private fun styleRects(s: JSONObject): Pair<List<RectF>, List<RectF>> {
        val fr = ArrayList<RectF>()
        val fx = ArrayList<RectF>()
        val a = s.optJSONArray("layers") ?: return Pair(fr, fx)
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val x = o.optDouble("x", 0.0).toFloat()
            val y = o.optDouble("y", 0.0).toFloat()
            val w = o.optDouble("w", 0.1).toFloat()
            val h = o.optDouble("h", 0.1).toFloat()
            val r = RectF(x, y, x + w, y + h)
            if (o.optBoolean("fr", false)) fr.add(r) else fx.add(r)
        }
        return Pair(fr, fx)
    }

    private fun buildStyles() {
        val d = doc ?: return
        val aspect = d.w / d.h.toFloat()
        panel.addView(section("MODELOS PRONTOS (toque para aplicar)"))
        val hs = HorizontalScrollView(this)
        val strip = LinearLayout(this)
        hs.addView(strip)
        for (t in TEMPLATES) {
            val p = LinearLayout.LayoutParams(dp(84), WRAP); p.rightMargin = dp(8)
            strip.addView(styleCard(t.name, t.cells.map { RectF(it[0], it[1], it[2], it[3]) }, emptyList(), aspect, Color.WHITE) {
                applyTemplate(t)
            }, p)
        }
        panel.addView(hs)
        panel.addView(slider("Espaçamento entre formas", 0, 15, tplGap, "", false) { v -> tplGap = v })
        panel.addView(slider("Margem das bordas", 0, 15, tplMargin, "", false) { v -> tplMargin = v })
        panel.addView(slider("Cantos das formas", 0, 15, tplRadius, "", false) { v -> tplRadius = v })
        panel.addView(hrow(btn("+ Forma vazia") { addFrame() }))

        panel.addView(section("MEUS ESTILOS"))
        val styles = loadStyles()
        if (styles.length() == 0) {
            panel.addView(tv("Nenhum estilo salvo ainda.", 12f, C_DIM))
        } else {
            val hs2 = HorizontalScrollView(this)
            val strip2 = LinearLayout(this)
            hs2.addView(strip2)
            for (i in 0 until styles.length()) {
                val s = styles.optJSONObject(i) ?: continue
                val rects = styleRects(s)
                val bg = if (s.optBoolean("bg", false)) s.optInt("bgColor", Color.WHITE) else Color.WHITE
                val p = LinearLayout.LayoutParams(dp(84), WRAP); p.rightMargin = dp(8)
                strip2.addView(styleCard(s.optString("name", "Estilo"), rects.first, rects.second,
                    s.optDouble("ar", aspect.toDouble()).toFloat(), bg, { styleMenu(i) }) { applyStyle(s) }, p)
            }
            panel.addView(hs2)
            panel.addView(tv("Segure um estilo para renomear ou excluir.", 11f, C_DIM))
        }
        panel.addView(btn("💾 Salvar layout atual como estilo", C_ACCENT) { saveStyleDialog() }, lp(MATCH, WRAP, 0f, dp(10)))
        val info = tv("Formas são salvas vazias, prontas para receber imagens. Imagens comuns (ex.: um logo) são salvas fixas. " +
                "Ao importar várias imagens, elas preenchem as formas vazias em ordem.", 11f, C_DIM)
        info.setPadding(dp(4), dp(8), dp(4), 0)
        panel.addView(info)
    }

    private fun framesFromTemplate(t: Template): List<Layer> {
        val d = doc ?: return emptyList()
        val dw = d.w.toFloat()
        val dh = d.h.toFloat()
        val unit = min(dw, dh) / 100f
        val g = tplGap * unit
        val m = tplMargin * unit
        val rad = tplRadius * unit
        val ax = m - g / 2f
        val ay = m - g / 2f
        val aw = dw - 2f * m + g
        val ah = dh - 2f * m + g
        val out = ArrayList<Layer>()
        for ((i, c) in t.cells.withIndex()) {
            val l = Layer()
            l.id = newId()
            l.name = "Forma ${i + 1}"
            l.isFrame = true
            l.x = ax + c[0] * aw + g / 2f
            l.y = ay + c[1] * ah + g / 2f
            l.w = max(10f, (c[2] - c[0]) * aw - g)
            l.h = max(10f, (c[3] - c[1]) * ah - g)
            l.radius = rad
            out.add(l)
        }
        return out
    }

    private fun applyTemplate(t: Template) {
        applyLayers(framesFromTemplate(t), emptyList(), null)
    }

    private fun applyStyle(s: JSONObject) {
        val d = doc ?: return
        val arr = s.optJSONArray("layers") ?: JSONArray()
        val frames = ArrayList<Layer>()
        val fixed = ArrayList<Layer>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val l = Layer.fromJson(o, d.w.toFloat(), d.h.toFloat())
            l.id = newId()
            if (l.isFrame) { l.src = null; frames.add(l) } else fixed.add(l)
        }
        applyLayers(frames, fixed, if (s.optBoolean("bg", false)) s else null)
    }

    private fun applyLayers(frames: List<Layer>, fixed: List<Layer>, bgStyle: JSONObject?) {
        val d = doc ?: return
        fun go(replace: Boolean) {
            pushUndo()
            if (replace) {
                val srcs = d.layers.filter { it.isFrame }.mapNotNull { it.src }
                d.layers.removeAll { it.isFrame }
                for ((i, f) in frames.withIndex()) if (i < srcs.size) f.src = srcs[i]
                d.layers.addAll(0, frames)
            } else {
                d.layers.addAll(frames)
            }
            d.layers.addAll(fixed)
            if (bgStyle != null) {
                d.bgColor = bgStyle.optInt("bgColor", d.bgColor)
                val bs = bgStyle.optString("bgSrc", "")
                d.bgSrc = if (bs.isEmpty()) null else bs
                d.bgBlur = bgStyle.optInt("blur", 0)
                d.bgDim = bgStyle.optInt("dim", 0)
                Images.request(d.bgSrc)
            }
            for (l in frames) Images.request(l.src)
            for (l in fixed) Images.request(l.src)
            selectedId = 0L
            commit()
            toast("Estilo aplicado")
        }
        if (d.layers.none { it.isFrame }) { go(true); return }
        AlertDialog.Builder(this).setTitle("Aplicar estilo")
            .setItems(arrayOf("Substituir as formas atuais (as imagens são mantidas, em ordem)", "Adicionar por cima")) { _, w -> go(w == 0) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun saveStyleDialog() {
        val d = doc ?: return
        val box = vbox()
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val e = EditText(this)
        e.hint = "Nome do estilo"
        e.setText("Estilo " + (loadStyles().length() + 1))
        e.setTextColor(C_TEXT)
        e.setSingleLine()
        val cb = CheckBox(this)
        cb.text = "Incluir fundo (cor e imagem)"
        cb.isChecked = true
        cb.setTextColor(C_TEXT)
        box.addView(e)
        box.addView(cb)
        AlertDialog.Builder(this).setTitle("Salvar estilo").setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                val sx = 1f / d.w
                val sy = 1f / d.h
                val arr = JSONArray()
                for (l in d.layers) {
                    val o = l.toJson(sx, sy)
                    if (l.isFrame) { o.put("src", ""); o.put("z", 1.0); o.put("px", 0.0); o.put("py", 0.0) }
                    arr.put(o)
                }
                val s = JSONObject()
                val nm = e.text.toString().trim()
                s.put("name", if (nm.isEmpty()) "Estilo" else nm)
                s.put("ar", (d.w / d.h.toFloat()).toDouble())
                s.put("layers", arr)
                s.put("bg", cb.isChecked)
                s.put("bgColor", d.bgColor)
                s.put("bgSrc", d.bgSrc ?: "")
                s.put("blur", d.bgBlur)
                s.put("dim", d.bgDim)
                val all = loadStyles()
                all.put(s)
                saveStyles(all)
                refreshPanel()
                toast("Estilo salvo")
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun styleMenu(i: Int) {
        val all = loadStyles()
        val s = all.optJSONObject(i) ?: return
        AlertDialog.Builder(this).setTitle(s.optString("name", "Estilo"))
            .setItems(arrayOf("Aplicar", "Renomear", "Excluir")) { _, w ->
                when (w) {
                    0 -> applyStyle(s)
                    1 -> askText("Renomear estilo", s.optString("name", "")) { n -> s.put("name", n); saveStyles(all); refreshPanel() }
                    2 -> confirmDialog("Excluir este estilo?") { all.remove(i); saveStyles(all); refreshPanel() }
                }
            }.show()
    }

    // ========================== PAINEL: FUNDO ========================
    private fun buildBg() {
        val d = doc ?: return
        panel.addView(section("COR DE FUNDO"))
        panel.addView(swatches(d.bgColor, true) { c -> pushUndo(); d.bgColor = c; commit() })
        val hex = EditText(this)
        hex.setText(String.format(Locale.US, "#%08X", d.bgColor))
        hex.setTextColor(C_TEXT)
        hex.setSingleLine()
        hex.background = rounded(C_BTN, 6)
        hex.setPadding(dp(8), dp(8), dp(8), dp(8))
        panel.addView(hrow(hex, btn("Aplicar cor") {
            try {
                val c = Color.parseColor(hex.text.toString().trim())
                pushUndo(); d.bgColor = c; commit()
            } catch (e: Exception) { toast("Cor inválida. Ex.: #FF5722") }
        }))
        panel.addView(tv("∅ = transparente (só funciona ao salvar em PNG).", 11f, C_DIM))

        panel.addView(section("IMAGEM DE FUNDO"))
        panel.addView(hrow(
            btn(if (d.bgSrc == null) "🖼 Escolher imagem" else "🖼 Trocar imagem", C_ACCENT) { pickImages(REQ_BG, false) },
            btn("Remover") { pushUndo(); d.bgSrc = null; commit() }
        ))
        if (d.bgSrc != null) {
            if (Images.isMissing(d.bgSrc)) panel.addView(tv("⚠ Imagem de fundo não encontrada no aparelho.", 12f, 0xFFFFB74D.toInt()))
            panel.addView(slider("Desfoque", 0, 100, d.bgBlur, "%") { v -> d.bgBlur = v })
            panel.addView(slider("Escurecer", 0, 90, d.bgDim, "%") { v -> d.bgDim = v })
        }
    }
}
