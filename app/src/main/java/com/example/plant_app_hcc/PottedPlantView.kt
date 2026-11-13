package com.example.plant_app_hcc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.LruCache
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min

/** Draws the chosen flower, pot, and optional space dome on the same coordinate grid. */
class PottedPlantView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null
) : View(context, attributes) {
    private var sprite: Sprite? = null
    private var pot: Drawable? = null
    private var potStyle = PotStyle.STARTER
    private var space = false
    private val paint = Paint()
    private val flowerBounds = RectF()
    private val dome = Path().apply {
        moveTo(22f, 58f)
        cubicTo(2f, 48f, 3f, 20f, 17f, 11f)
        cubicTo(31f, 1f, 57f, 1f, 71f, 11f)
        cubicTo(85f, 20f, 86f, 48f, 66f, 58f)
        close()
    }

    fun show(flowerId: String, pot: PotStyle, inSpace: Boolean) {
        val path = GardenFlowers.assetPath(flowerId)
        sprite = sprites.get(path) ?: loadSprite(path).also { sprites.put(path, it) }
        this.pot = ContextCompat.getDrawable(context, pot.icon)?.mutate()?.apply {
            setBounds(11, 44, 77, 90)
        }
        potStyle = pot
        space = inSpace
        contentDescription = context.getString(
            if (inSpace) R.string.garden_space_plant_description else R.string.garden_potted_plant_description,
            context.getString(pot.title)
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width, height) / 88f
        canvas.save()
        canvas.translate((width - 88f * scale) / 2f, (height - 88f * scale) / 2f)
        canvas.scale(scale, scale)

        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = 0x26334530
        canvas.drawOval(20f, 79f, 68f, 86f, paint)
        if (space) {
            paint.color = 0x3392DEFF
            canvas.drawPath(dome, paint)
        }

        sprite?.let { flower ->
            val widthFromStem = max(flower.stemX - flower.bounds.left, flower.bounds.right - flower.stemX)
            val size = min(44f / flower.bounds.height(), 34f / widthFromStem)
            val left = 44f - (flower.stemX - flower.bounds.left) * size
            val rim = potStyle.rimY
            flowerBounds.set(left, rim - flower.bounds.height() * size,
                left + flower.bounds.width() * size, rim)
            paint.color = Color.WHITE
            paint.isFilterBitmap = false
            canvas.drawBitmap(flower.bitmap, flower.bounds, flowerBounds, paint)
        }

        if (space) {
            paint.color = 0xFF67B5DC.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.8f
            canvas.drawPath(dome, paint)
            paint.color = 0xDDEAFBFF.toInt()
            paint.strokeWidth = 2.2f
            canvas.drawArc(13f, 9f, 68f, 59f, 202f, 40f, false, paint)
        }
        pot?.draw(canvas)
        canvas.restore()
    }

    private fun loadSprite(path: String): Sprite {
        val bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
        val bounds = Rect(bitmap.width, bitmap.height, 0, 0)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 64) continue
                bounds.left = min(bounds.left, x)
                bounds.top = min(bounds.top, y)
                bounds.right = max(bounds.right, x + 1)
                bounds.bottom = max(bounds.bottom, y + 1)
            }
        }
        var stemLeft = bounds.right
        var stemRight = bounds.left
        for (x in bounds.left until bounds.right) {
            if (Color.alpha(bitmap.getPixel(x, bounds.bottom - 1)) >= 64) {
                stemLeft = min(stemLeft, x)
                stemRight = max(stemRight, x + 1)
            }
        }
        return Sprite(bitmap, bounds, (stemLeft + stemRight) / 2f)
    }

    private data class Sprite(val bitmap: Bitmap, val bounds: Rect, val stemX: Float)

    private companion object {
        val sprites = LruCache<String, Sprite>(40)
    }
}
