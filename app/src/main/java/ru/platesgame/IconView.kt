package ru.platesgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Иконки, нарисованные на Canvas (вместо эмодзи):
 * монета с рублём, мини-номерной знак и золотая звезда.
 */
class IconView(context: Context, private val kind: Int) : View(context) {

    companion object {
        const val COIN = 0
        const val PLATE = 1
        const val STAR = 2
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val ink = Color.parseColor("#0E0E12")

    override fun onDraw(canvas: Canvas) {
        when (kind) {
            COIN -> drawCoin(canvas)
            PLATE -> drawPlate(canvas)
            else -> drawStar(canvas)
        }
    }

    private fun gradient(x0: Float, y0: Float, x1: Float, y1: Float, vararg colors: String): LinearGradient =
        LinearGradient(
            x0, y0, x1, y1,
            IntArray(colors.size) { Color.parseColor(colors[it]) },
            null, Shader.TileMode.CLAMP
        )

    private fun sparkle(canvas: Canvas, x: Float, y: Float, a: Float) {
        path.reset()
        path.moveTo(x, y - a)
        path.quadTo(x, y, x + a, y)
        path.quadTo(x, y, x, y + a)
        path.quadTo(x, y, x - a, y)
        path.quadTo(x, y, x, y - a)
        path.close()
        p.reset(); p.isAntiAlias = true
        p.color = Color.WHITE
        canvas.drawPath(path, p)
    }

    // ───────────── Монета ─────────────
    private fun drawCoin(canvas: Canvas) {
        val s = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val r = s / 2f - s * 0.05f

        p.reset(); p.isAntiAlias = true
        p.color = 0x44000000
        canvas.drawCircle(cx, cy + s * 0.04f, r, p)

        p.shader = gradient(cx - r, cy - r, cx + r, cy + r, "#FFF3A0", "#FFC83D", "#D98A00")
        canvas.drawCircle(cx, cy, r, p)

        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.05f
        p.color = Color.parseColor("#B36B00")
        canvas.drawCircle(cx, cy, r - p.strokeWidth / 2f, p)

        val ri = r * 0.74f
        p.style = Paint.Style.FILL
        p.shader = gradient(cx, cy - ri, cx, cy + ri, "#FFDF6B", "#F2A516")
        canvas.drawCircle(cx, cy, ri, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.03f
        p.color = Color.parseColor("#C98200")
        canvas.drawCircle(cx, cy, ri, p)

        // ₽
        p.reset(); p.isAntiAlias = true
        p.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        p.textSize = s * 0.5f
        p.color = Color.parseColor("#9A5B00")
        canvas.drawText("₽", cx, cy - (p.ascent() + p.descent()) / 2f, p)

        // Блик по краю
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.045f
        p.color = 0xA0FFFFFF.toInt()
        rect.set(cx - ri * 0.82f, cy - ri * 0.82f, cx + ri * 0.82f, cy + ri * 0.82f)
        canvas.drawArc(rect, 200f, 60f, false, p)

        sparkle(canvas, cx + r * 0.66f, cy - r * 0.66f, s * 0.12f)
    }

    // ───────────── Мини-номер ─────────────
    private fun drawPlate(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val i = h * 0.04f
        val body = RectF(i, i, w - i, h - i * 3f)
        val corner = h * 0.2f

        p.reset(); p.isAntiAlias = true
        p.color = 0x55000000
        rect.set(body); rect.offset(0f, h * 0.06f)
        canvas.drawRoundRect(rect, corner, corner, p)

        p.shader = gradient(0f, body.top, 0f, body.bottom, "#FFFFFF", "#F1F1F6", "#D5D5DE")
        canvas.drawRoundRect(body, corner, corner, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.035f
        p.color = Color.parseColor("#9A98A8")
        canvas.drawRoundRect(body, corner, corner, p)

        // Рамка
        val f = RectF(body.left + h * 0.1f, body.top + h * 0.1f, body.right - h * 0.1f, body.bottom - h * 0.1f)
        p.strokeWidth = h * 0.065f
        p.color = ink
        canvas.drawRoundRect(f, h * 0.1f, h * 0.1f, p)
        val dx = body.left + body.width() * 0.76f
        canvas.drawLine(dx, f.top, dx, f.bottom, p)

        // Текст «А777»
        p.reset(); p.isAntiAlias = true
        p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        p.color = ink
        p.textAlign = Paint.Align.CENTER
        val txt = "А777"
        p.textSize = f.height() * 0.78f
        val maxW = dx - f.left - h * 0.14f
        val tw = p.measureText(txt)
        if (tw > maxW) p.textSize *= maxW / tw
        canvas.drawText(txt, (f.left + dx) / 2f, (f.top + f.bottom) / 2f - (p.ascent() + p.descent()) / 2f, p)

        // Флаг в зоне региона
        val fw = (f.right - dx) * 0.55f
        val fh = fw * 0.66f
        val fx = (dx + f.right) / 2f - fw / 2f
        val fy = (f.top + f.bottom) / 2f - fh / 2f
        p.reset(); p.isAntiAlias = true
        p.color = Color.WHITE
        canvas.drawRect(fx, fy, fx + fw, fy + fh / 3f, p)
        p.color = Color.parseColor("#0039A6")
        canvas.drawRect(fx, fy + fh / 3f, fx + fw, fy + 2f * fh / 3f, p)
        p.color = Color.parseColor("#D52B1E")
        canvas.drawRect(fx, fy + 2f * fh / 3f, fx + fw, fy + fh, p)
    }

    // ───────────── Золотая звезда ─────────────
    private fun drawStar(canvas: Canvas) {
        val s = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f + s * 0.03f
        val outer = s * 0.48f
        val inner = outer * 0.45f

        path.reset()
        for (k in 0 until 10) {
            val ang = Math.toRadians((-90 + k * 36).toDouble())
            val rad = if (k % 2 == 0) outer else inner
            val x = cx + (rad * cos(ang)).toFloat()
            val y = cy + (rad * sin(ang)).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        p.reset(); p.isAntiAlias = true
        p.shader = gradient(cx, cy - outer, cx, cy + outer, "#FFF3A0", "#FFC83D", "#E39100")
        canvas.drawPath(path, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = s * 0.05f
        p.color = Color.parseColor("#B36B00")
        canvas.drawPath(path, p)

        sparkle(canvas, cx - outer * 0.3f, cy - outer * 0.35f, s * 0.1f)
    }
}
