package com.trabajo.escaner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val pintura = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinturaLinea = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    init {
        pintura.style = Paint.Style.FILL
        pinturaLinea.style = Paint.Style.STROKE
        pinturaLinea.color = Color.RED
        pinturaLinea.strokeWidth = 5f
        pintura.color = 0xCC1B1B1B.toInt()
    }

    fun setColorHex(hex: String) {
        pintura.color = try {
            Color.parseColor(hex)
        } catch (e: Exception) {
            0xCC1B1B1B.toInt()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        path.reset()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)

        val w = width * 0.88f
        val h = height * 0.32f
        val izq = (width - w) / 2f
        val alt = height * 0.30f
        rect.set(izq, alt, izq + w, alt + h)
        path.addRoundRect(rect, 20f, 20f, Path.Direction.CCW)

        pintura.fillType = Paint.FillType.EVEN_ODD
        canvas.drawPath(path, pintura)

        // Borde blanco pinturaLinea.color = Color.WHITE
        pinturaLinea.strokeWidth = 3f
        canvas.drawRoundRect(rect, 20f, 20f, pinturaLinea)

        // Línea roja central (nivel)
        pinturaLinea.color = Color.RED
        pinturaLinea.strokeWidth = 5f
        val cy = rect.centerY()
        canvas.drawLine(rect.left + 20f, cy, rect.right - 20f, cy, pinturaLinea)
    }
}
