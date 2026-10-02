package ru.platesgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

/**
 * Российский автомобильный номер, нарисованный на Canvas:
 * белый корпус с тиснением, чёрная рамка, разделитель, регион, «RUS» и флаг.
 * Пропорции близки к настоящему номеру 520 × 112 мм.
 */
class PlateView(context: Context) : View(context) {

    var plate: Plate? = null
        set(value) { field = value; invalidate() }

    /** Положение блика 0..1. Значение вне диапазона — блика нет. */
    var shine: Float = -1f
        set(value) { field = value; invalidate() }

    companion object {
        const val ASPECT = 520f / 112f
        fun heightFor(width: Int) = (width / ASPECT).toInt()
    }

    private val inkColor = Color.parseColor("#0E0E12")

    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#9A98A8")
    }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = inkColor
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val emboss = Paint(ink).apply { color = Color.parseColor("#8E8C9C"); alpha = 150 }
    private val flag = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rect = RectF()
    private val bodyRect = RectF()
    private val bounds = Rect()
    private val path = Path()
    private var bodyShader: Shader? = null
    private var goldShader: Shader? = null

    private val edgeNormal = Color.parseColor("#9A98A8")
    private val edgeGold = Color.parseColor("#8A5E00")
    private val embossNormal = Color.parseColor("#8E8C9C")
    private val embossGold = Color.parseColor("#A47500")

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED)
            (300 * resources.displayMetrics.density).toInt()
        else MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(w, heightFor(w))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val v = h / 112f
        bodyShader = LinearGradient(
            0f, v, 0f, h - 3 * v,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFF4F4F7.toInt(), 0xFFDADAE2.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        goldShader = LinearGradient(
            0f, v, 0f, h - 3 * v,
            intArrayOf(0xFFFFF4B8.toInt(), 0xFFF7CF55.toInt(), 0xFFE4A921.toInt(), 0xFFB97F0E.toInt()),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val p = plate ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val u = h / 112f

        // ── Корпус с мягкой тенью ──
        bodyRect.set(2 * u, u, w - 2 * u, h - 3 * u)
        val v = bodyRect.height() / 112f
        val corner = 12 * v
        rect.set(bodyRect); rect.offset(0f, 2f * u)
        canvas.drawRoundRect(rect, corner, corner, shadow)
        body.shader = if (p.gold) goldShader else bodyShader
        canvas.drawRoundRect(bodyRect, corner, corner, body)
        edge.strokeWidth = 1.4f * v
        edge.color = if (p.gold) edgeGold else edgeNormal
        emboss.color = if (p.gold) embossGold else embossNormal
        emboss.alpha = 150
        canvas.drawRoundRect(bodyRect, corner, corner, edge)

        // ── Чёрная рамка ──
        val inset = 6 * v
        rect.set(bodyRect.left + inset, bodyRect.top + inset, bodyRect.right - inset, bodyRect.bottom - inset)
        frame.strokeWidth = 2.6f * v
        canvas.drawRoundRect(rect, 7 * v, 7 * v, frame)

        // ── Разделитель основной части и региона ──
        val dx = bodyRect.left + bodyRect.width() * 0.745f
        canvas.drawLine(dx, rect.top, dx, rect.bottom, frame)

        // ── Основная часть: буква, три цифры, две буквы ──
        val parts = listOf(p.num.substring(0, 1), p.num.substring(1, 4), p.num.substring(4))
        val areaLeft = rect.left + 5 * v
        val maxW = dx - areaLeft - 5 * v
        var size = bodyRect.height() * 0.74f
        fun totalWidth(s: Float): Float {
            ink.textSize = s
            return parts.sumOf { ink.measureText(it).toDouble() }.toFloat() + 0.34f * s
        }
        var total = totalWidth(size)
        if (total > maxW) { size *= maxW / total; total = totalWidth(size) }
        ink.textSize = size
        emboss.textSize = size
        ink.getTextBounds(p.num, 0, p.num.length, bounds)
        val baseline = bodyRect.centerY() - (bounds.top + bounds.bottom) / 2f
        var x = areaLeft + (maxW - total) / 2f
        for (part in parts) {
            canvas.drawText(part, x + 0.9f * v, baseline + 0.9f * v, emboss)
            canvas.drawText(part, x, baseline, ink)
            x += ink.measureText(part) + 0.17f * size
        }

        // ── Регион ──
        val regLeft = dx
        val regRight = rect.right
        val regCx = (regLeft + regRight) / 2f
        val regMaxW = regRight - regLeft - 8 * v
        var rs = bodyRect.height() * 0.46f
        ink.textSize = rs
        val rw = ink.measureText(p.region)
        if (rw > regMaxW) rs *= regMaxW / rw
        ink.textSize = rs
        emboss.textSize = rs
        ink.getTextBounds(p.region, 0, p.region.length, bounds)
        val regBase = bodyRect.top + bodyRect.height() * 0.41f - (bounds.top + bounds.bottom) / 2f
        val rx = regCx - ink.measureText(p.region) / 2f
        canvas.drawText(p.region, rx + 0.7f * v, regBase + 0.7f * v, emboss)
        canvas.drawText(p.region, rx, regBase, ink)

        // ── «RUS» и флаг ──
        val rus = "RUS"
        ink.textSize = bodyRect.height() * 0.19f
        ink.letterSpacing = 0.06f
        ink.getTextBounds(rus, 0, rus.length, bounds)
        val fw = 10 * v
        val fh = 6.8f * v
        val gap = 3 * v
        val rowW = ink.measureText(rus) + gap + fw
        val rowY = bodyRect.top + bodyRect.height() * 0.78f
        val startX = regCx - rowW / 2f
        canvas.drawText(rus, startX, rowY + bounds.height() / 2f, ink)
        ink.letterSpacing = 0f

        val fx = startX + ink.measureText(rus) + gap
        val fy = rowY - fh / 2f
        flag.style = Paint.Style.FILL
        flag.color = Color.WHITE
        canvas.drawRect(fx, fy, fx + fw, fy + fh / 3f, flag)
        flag.color = Color.parseColor("#0039A6")
        canvas.drawRect(fx, fy + fh / 3f, fx + fw, fy + 2 * fh / 3f, flag)
        flag.color = Color.parseColor("#D52B1E")
        canvas.drawRect(fx, fy + 2 * fh / 3f, fx + fw, fy + fh, flag)
        flag.style = Paint.Style.STROKE
        flag.strokeWidth = 0.6f * v
        flag.color = inkColor
        canvas.drawRect(fx, fy, fx + fw, fy + fh, flag)

        // ── Блик ──
        if (shine in 0f..1f) {
            path.reset()
            path.addRoundRect(bodyRect, corner, corner, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(path)
            val bw = h * 1.1f
            val sk = h * 0.5f
            val sx = -bw - sk + shine * (w + 2 * bw + sk)
            shinePaint.shader = LinearGradient(
                sx, 0f, sx + bw + sk, 0f,
                if (p.gold) intArrayOf(0x00FFFFFF, 0xD0FFFFFF.toInt(), 0x00FFFFFF)
                else intArrayOf(0x00C084FC, 0xB0C084FC.toInt(), 0x00C084FC),
                null, Shader.TileMode.CLAMP
            )
            path.reset()
            path.moveTo(sx + sk, 0f); path.lineTo(sx + sk + bw, 0f)
            path.lineTo(sx + bw, h); path.lineTo(sx, h); path.close()
            canvas.drawPath(path, shinePaint)
            canvas.restore()
        }
    }
}
