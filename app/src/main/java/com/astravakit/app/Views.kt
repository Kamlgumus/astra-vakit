package com.astravakit.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun View.dpf(v: Float): Float = v * resources.displayMetrics.density

private const val C_BG = 0xFF050510.toInt()
private const val C_ACC = 0xFF7C5CFF.toInt()
private const val C_ACC2 = 0xFF00D4FF.toInt()
private const val C_GOLD = 0xFFFFC857.toInt()
private const val C_GREEN = 0xFF3DFF9A.toInt()

/** Hareketli yıldızlar, nebula bulutları, halkalı gezegen ve kayan yıldız */
class SpaceView(c: Context) : View(c) {
    private class Star(val x: Float, val y: Float, val r: Float, val ph: Float, val sp: Float, val col: Int)

    private val stars = ArrayList<Star>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t0 = System.nanoTime()
    private var nebA: Paint? = null
    private var nebB: Paint? = null
    private var nebC: Paint? = null
    private var planet: Paint? = null
    private var pr = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        stars.clear()
        val rnd = Random(11)
        val cols = intArrayOf(0xFFFFFFFF.toInt(), 0xFFCFD8FF.toInt(), 0xFFFFE9C4.toInt(), 0xFFB9A8FF.toInt())
        repeat(190) {
            stars.add(Star(rnd.nextFloat() * w, rnd.nextFloat() * h,
                (0.5f + rnd.nextFloat() * 1.5f) * dpf(1f),
                rnd.nextFloat() * 6.28f, 0.5f + rnd.nextFloat() * 2.2f, cols[rnd.nextInt(cols.size)]))
        }
        val big = maxOf(w, h).toFloat()
        nebA = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, big * 0.55f, 0x667C5CFF, 0x007C5CFF, Shader.TileMode.CLAMP) }
        nebB = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, big * 0.5f, 0x4400D4FF, 0x0000D4FF, Shader.TileMode.CLAMP) }
        nebC = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, big * 0.4f, 0x44FF5C8A, 0x00FF5C8A, Shader.TileMode.CLAMP) }
        pr = w * 0.13f
        planet = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(-pr * 0.35f, -pr * 0.35f, pr * 1.3f,
                intArrayOf(0xFFB9A8FF.toInt(), 0xFF5B3FD6.toInt(), 0xFF150B45.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) }
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = (System.nanoTime() - t0) / 1e9f
        cv.drawColor(C_BG)

        // nebula
        nebA?.let { cv.save(); cv.translate(w * 0.1f + sin(t * 0.07f) * w * 0.06f, h * 0.1f); cv.drawCircle(0f, 0f, maxOf(w, h) * 0.55f, it); cv.restore() }
        nebB?.let { cv.save(); cv.translate(w * 0.95f, h * 0.88f + cos(t * 0.05f) * h * 0.03f); cv.drawCircle(0f, 0f, maxOf(w, h) * 0.5f, it); cv.restore() }
        nebC?.let { cv.save(); cv.translate(w * 0.5f + cos(t * 0.04f) * w * 0.1f, h * 0.5f); cv.drawCircle(0f, 0f, maxOf(w, h) * 0.4f, it); cv.restore() }

        // yıldızlar
        for (s in stars) {
            val a = 0.25f + 0.75f * (0.5f + 0.5f * sin(t * s.sp + s.ph))
            p.color = s.col
            p.alpha = (a * 255).toInt()
            val y = (s.y + t * s.sp * 2.5f) % h
            cv.drawCircle(s.x, y, s.r, p)
        }

        // halkalı gezegen
        planet?.let { pl ->
            cv.save()
            cv.translate(w * 0.92f, h * 0.11f)
            cv.rotate(-24f)
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            ringPaint.style = Paint.Style.STROKE
            ringPaint.strokeWidth = dpf(3f)
            ringPaint.color = 0x55FFC857
            val ring = RectF(-pr * 1.9f, -pr * 0.5f, pr * 1.9f, pr * 0.5f)
            cv.drawArc(ring, 180f, 180f, false, ringPaint)       // arka yarı
            cv.drawCircle(0f, 0f, pr, pl)
            ringPaint.color = 0x99FFC857.toInt()
            cv.drawArc(ring, 0f, 180f, false, ringPaint)         // ön yarı
            cv.restore()
        }

        // kayan yıldız (her 8 sn)
        val ph = (t % 8f) / 8f
        if (ph < 0.12f) {
            val k = ph / 0.12f
            val sx = w * (0.15f + 0.1f * ((t / 8f).toInt() % 5)) + k * w * 0.5f
            val sy = h * 0.08f + k * h * 0.22f
            for (i in 0 until 8) {
                p.color = Color.WHITE
                p.alpha = ((1f - k) * (255 - i * 28)).toInt().coerceAtLeast(0)
                cv.drawCircle(sx - i * dpf(7f), sy - i * dpf(3.6f), dpf(1.8f) - i * dpf(0.15f), p)
            }
        }
        postInvalidateOnAnimation()
    }
}

/** Sıradaki vakte ilerlemeyi gösteren neon halka */
class RingView(c: Context) : View(c) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        arc.shader = SweepGradient(w / 2f, h / 2f,
            intArrayOf(C_ACC, C_ACC2, C_GREEN, C_ACC), floatArrayOf(0f, 0.45f, 0.8f, 1f))
    }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val sw = dpf(10f)
        val r = s / 2f - dpf(26f)
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)

        tick.color = 0x33FFFFFF
        tick.strokeWidth = dpf(1.2f)
        for (i in 0 until 72) {
            c.save(); c.rotate(i * 5f, cx, cy)
            val len = if (i % 6 == 0) dpf(9f) else dpf(4f)
            c.drawLine(cx, cy - r - dpf(16f), cx, cy - r - dpf(16f) + len, tick)
            c.restore()
        }

        track.style = Paint.Style.STROKE; track.strokeWidth = sw; track.color = 0x1AFFFFFF
        c.drawCircle(cx, cy, r, track)

        c.save()
        c.rotate(-90f, cx, cy)
        glow.style = Paint.Style.STROKE; glow.strokeWidth = sw * 2.2f; glow.color = 0x227C5CFF
        glow.strokeCap = Paint.Cap.ROUND
        c.drawArc(rect, 0f, 360f * progress, false, glow)
        arc.style = Paint.Style.STROKE; arc.strokeWidth = sw; arc.strokeCap = Paint.Cap.ROUND
        c.drawArc(rect, 0f, 360f * progress, false, arc)
        val a = progress * 2.0 * PI
        val hx = cx + r * cos(a).toFloat()
        val hy = cy + r * sin(a).toFloat()
        dot.color = 0x44FFFFFF; c.drawCircle(hx, hy, sw * 1.3f, dot)
        dot.color = Color.WHITE; c.drawCircle(hx, hy, sw * 0.55f, dot)
        c.restore()
    }
}

/** Güneşin/ayın gökyüzündeki yayını gösterir */
class SunArcView(c: Context) : View(c) {
    var start = 0L
    var end = 1L
    var now = 0L
    var isDay = true
    var leftLabel = ""
    var rightLabel = ""
    var title = ""

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setNowMs(n: Long) { now = n; invalidate() }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val baseY = height - dpf(30f)
        val r = min(width / 2f - dpf(34f), baseY - dpf(10f))
        val rect = RectF(cx - r, baseY - r, cx + r, baseY + r)
        val f = if (end > start) ((now - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f) else 0f
        val col = if (isDay) C_GOLD else C_ACC2

        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(1.5f)
        p.color = 0x44FFFFFF
        c.drawLine(cx - r - dpf(14f), baseY, cx + r + dpf(14f), baseY, p)

        p.pathEffect = DashPathEffect(floatArrayOf(dpf(4f), dpf(6f)), 0f)
        p.color = 0x55FFFFFF
        c.drawArc(rect, 180f, 180f, false, p)
        p.pathEffect = null

        p.strokeWidth = dpf(3f)
        p.strokeCap = Paint.Cap.ROUND
        p.color = col
        c.drawArc(rect, 180f, 180f * f, false, p)

        val th = PI * f
        val x = cx - r * cos(th).toFloat()
        val y = baseY - r * sin(th).toFloat()
        p.style = Paint.Style.FILL
        p.color = (col and 0x00FFFFFF) or 0x22000000; c.drawCircle(x, y, dpf(22f), p)
        p.color = (col and 0x00FFFFFF) or 0x44000000; c.drawCircle(x, y, dpf(14f), p)
        p.color = if (isDay) col else 0xFFEAF6FF.toInt(); c.drawCircle(x, y, dpf(8f), p)
        if (!isDay) { p.color = 0xFF1A1550.toInt(); c.drawCircle(x + dpf(3.5f), y - dpf(2f), dpf(6.5f), p) }

        p.textAlign = Paint.Align.CENTER
        p.textSize = dpf(12f)
        p.color = 0xFF9A9AB8.toInt()
        c.drawText(leftLabel, cx - r, baseY + dpf(18f), p)
        c.drawText(rightLabel, cx + r, baseY + dpf(18f), p)
        p.textSize = dpf(13f)
        p.color = 0xFFE8E8FF.toInt()
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(title, cx, baseY - r * 0.3f, p)
        p.typeface = Typeface.DEFAULT
    }
}

/** Kıble pusulası: cihaz yönüne göre döner, Kâbe yönünü gösterir */
class CompassView(c: Context) : View(c) {
    var heading = 0f
        set(v) { field = v; invalidate() }
    var qibla = 0f
        set(v) { field = v; invalidate() }
    var hasSensor = true

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun diff(): Float {
        var d = (qibla - heading) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - dpf(10f)
        val aligned = hasSensor && abs(diff()) < 4f

        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r, 0x55231454, 0x11050510, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(2.5f)
        p.color = if (aligned) C_GREEN else 0x887C5CFF.toInt()
        c.drawCircle(cx, cy, r, p)
        p.strokeWidth = dpf(1f)
        p.color = 0x33FFFFFF
        c.drawCircle(cx, cy, r * 0.62f, p)

        c.save()
        c.rotate(-heading, cx, cy)
        // ölçek
        for (i in 0 until 72) {
            c.save(); c.rotate(i * 5f, cx, cy)
            val major = i % 9 == 0
            p.strokeWidth = if (major) dpf(2f) else dpf(1f)
            p.color = if (major) 0xCCFFFFFF.toInt() else 0x44FFFFFF
            c.drawLine(cx, cy - r + dpf(3f), cx, cy - r + dpf(if (major) 14f else 8f), p)
            c.restore()
        }
        // yön harfleri
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.textSize = dpf(17f)
        p.typeface = Typeface.DEFAULT_BOLD
        val labels = arrayOf("K", "D", "G", "B")
        for (k in 0 until 4) {
            c.save(); c.rotate(k * 90f, cx, cy)
            p.color = if (k == 0) 0xFFFF5C7A.toInt() else 0xFFE8E8FF.toInt()
            c.drawText(labels[k], cx, cy - r + dpf(38f), p)
            c.restore()
        }
        p.typeface = Typeface.DEFAULT

        // kıble ibresi + Kâbe
        c.save()
        c.rotate(qibla, cx, cy)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = dpf(3f)
        p.color = if (aligned) C_GREEN else C_GOLD
        c.drawLine(cx, cy, cx, cy - r + dpf(58f), p)
        p.style = Paint.Style.FILL
        p.color = 0x33FFC857
        c.drawCircle(cx, cy - r + dpf(44f), dpf(24f), p)
        p.color = 0xFF111111.toInt()
        val kb = RectF(cx - dpf(12f), cy - r + dpf(32f), cx + dpf(12f), cy - r + dpf(56f))
        c.drawRoundRect(kb, dpf(3f), dpf(3f), p)
        p.color = C_GOLD
        c.drawRect(cx - dpf(12f), cy - r + dpf(37f), cx + dpf(12f), cy - r + dpf(40f), p)
        c.restore()
        c.restore()

        // sabit telefon üst işareti
        val tri = Path()
        tri.moveTo(cx, cy - r - dpf(2f))
        tri.lineTo(cx - dpf(9f), cy - r - dpf(16f))
        tri.lineTo(cx + dpf(9f), cy - r - dpf(16f))
        tri.close()
        p.style = Paint.Style.FILL
        p.color = if (aligned) C_GREEN else C_ACC2
        c.drawPath(tri, p)

        // merkez
        p.color = 0xFF0A0A1A.toInt(); c.drawCircle(cx, cy, dpf(9f), p)
        p.color = if (aligned) C_GREEN else C_ACC; c.drawCircle(cx, cy, dpf(5f), p)
    }
}
