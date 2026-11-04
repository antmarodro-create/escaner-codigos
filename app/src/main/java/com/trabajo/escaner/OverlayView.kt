package com.trabajo.escaner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val pintura = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinturaLinea = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        pintura.style = Paint.Style.FILL
        pintura.color = 0xCC1B1B1B.toInt()
        pinturaLinea.style = Paint.Style.STROKE
        pinturaLinea.color = Color.WHITE
        pinturaLinea.strokeWidth = 3f
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

        val w = width * 0.88f
        val h = height * 0.32f
        val izq = (width - w) / 2f
        val alt = height * 0.30f
        rect.set(izq, alt, izq + w, alt + h)

        // Oscurecer las 4 zonas fuera del marco
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top, pintura)
        canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), pintura)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, pintura)
        canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, pintura)

        // Borde blanco
        canvas.drawRoundRect(rect, 20f, 20f, pinturaLinea)

        // Línea roja central (nivel)
        pinturaLinea.color = Color.RED
        pinturaLinea.strokeWidth = 5f
        val cy = rect.centerY()
        canvas.drawLine(rect.left + 20f, cy, rect.right - 20f, cy, pinturaLinea)
    }
}
