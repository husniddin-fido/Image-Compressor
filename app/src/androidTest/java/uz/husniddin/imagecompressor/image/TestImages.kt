package uz.husniddin.imagecompressor.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/** Small deterministic fixtures generated at test time. */
object TestImages {

    /** Photo-like content (gradient + shapes + noise) so encoders behave realistically. */
    fun photo(width: Int, height: Int, seed: Int = 7): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (y in 0 until height step 4) {
            val t = y.toFloat() / height
            paint.color = Color.rgb((40 + 150 * t).toInt(), (120 + 60 * t).toInt(), (210 - 90 * t).toInt())
            canvas.drawRect(0f, y.toFloat(), width.toFloat(), y + 4f, paint)
        }
        val rnd = Random(seed)
        repeat(120) {
            paint.color = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            canvas.drawCircle(rnd.nextFloat() * width, rnd.nextFloat() * height, rnd.nextFloat() * width / 10, paint)
        }
        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val n = rnd.nextInt(-18, 19)
            val c = pixels[i]
            pixels[i] = Color.rgb(
                (Color.red(c) + n).coerceIn(0, 255),
                (Color.green(c) + n).coerceIn(0, 255),
                (Color.blue(c) + n).coerceIn(0, 255),
            )
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    /** Transparent canvas with an opaque blue square in the centre. */
    fun transparent(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        Canvas(bmp).drawRect(size / 4f, size / 4f, size * 3 / 4f, size * 3 / 4f, Paint().apply { color = Color.BLUE })
        return bmp
    }

    fun write(bitmap: Bitmap, file: File, format: Bitmap.CompressFormat, quality: Int = 95): File {
        FileOutputStream(file).use { bitmap.compress(format, quality, it) }
        return file
    }

    /** Landscape JPEG with a red marker at the top-left, tagged with an EXIF orientation. */
    fun jpegWithOrientation(file: File, width: Int, height: Int, orientation: Int): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        Canvas(bmp).drawRect(0f, 0f, width / 4f, height / 4f, Paint().apply { color = Color.RED })
        write(bmp, file, Bitmap.CompressFormat.JPEG, 95)
        bmp.recycle()
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }
}
