package com.ziegler.kighelper.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.gson.Gson
import com.ziegler.kighelper.data.InfoCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object InfoCardTransferUtils {
    const val SHARE_MIME_TYPE = "application/vnd.kighelper.infocard"

    private const val PACKAGE_VERSION = 1
    private const val INFO_CARD_FILE = "info-card.json"
    private const val BACKGROUND_FILE = "background.webp"
    private val gson = Gson()

    suspend fun processBackgroundImageToWebp(context: Context, sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val input = context.contentResolver.openInputStream(sourceUri) ?: return@runCatching null
                input.use { stream ->
                    val original = BitmapFactory.decodeStream(stream) ?: return@runCatching null
                    val oriented = context.contentResolver.openInputStream(sourceUri)?.use { orientationStream ->
                        applyExifOrientation(original, ExifInterface(orientationStream))
                    } ?: original
                    val targetWidth = 900
                    val targetHeight = 1200

                    // Object-fit: cover (crop to fill target aspect ratio)
                    val scale = maxOf(
                        targetWidth / oriented.width.toFloat(),
                        targetHeight / oriented.height.toFloat()
                    )
                    val scaledWidth = (oriented.width * scale).toInt()
                    val scaledHeight = (oriented.height * scale).toInt()

                    val scaled = Bitmap.createScaledBitmap(oriented, scaledWidth, scaledHeight, true)
                    
                    // Center-crop to 900x1200
                    val xOffset = ((scaledWidth - targetWidth) / 2)
                    val yOffset = ((scaledHeight - targetHeight) / 2)
                    
                    val cropped = Bitmap.createBitmap(
                        scaled,
                        xOffset,
                        yOffset,
                        targetWidth,
                        targetHeight
                    )

                    val outputDir = File(context.filesDir, "info_cards")
                    outputDir.mkdirs()
                    val outputFile = File(outputDir, "bg-${System.currentTimeMillis()}.webp")

                    FileOutputStream(outputFile).use { fos ->
                        cropped.compress(Bitmap.CompressFormat.WEBP, 85, fos)
                    }

                    if (scaled !== oriented) scaled.recycle()
                    if (cropped !== scaled) cropped.recycle()
                    if (oriented !== original) oriented.recycle()
                    original.recycle()

                    outputFile.absolutePath
                }
            }.getOrNull()
        }

    suspend fun exportSharePackageBytes(context: Context, card: InfoCard): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val packageMeta = SharePackage(
                    version = PACKAGE_VERSION,
                    card = card.copy(backgroundImagePath = null).normalized()
                )

                ByteArrayOutputStream().use { output ->
                    ZipOutputStream(output).use { zip ->
                        zip.putNextEntry(ZipEntry(INFO_CARD_FILE))
                        zip.write(gson.toJson(packageMeta).toByteArray())
                        zip.closeEntry()

                        val imagePath = card.backgroundImagePath
                        if (!imagePath.isNullOrBlank()) {
                            val imageFile = File(imagePath)
                            if (imageFile.exists()) {
                                zip.putNextEntry(ZipEntry(BACKGROUND_FILE))
                                FileInputStream(imageFile).use { fis ->
                                    fis.copyTo(zip)
                                }
                                zip.closeEntry()
                            }
                        }
                    }
                    output.toByteArray()
                }
            }.getOrNull()
        }

    suspend fun importSharePackageBytes(context: Context, packageBytes: ByteArray): InfoCard? =
        withContext(Dispatchers.IO) {
            runCatching {
                var importedCard: InfoCard? = null
                var importedImagePath: String? = null

                ByteArrayInputStream(packageBytes).use { stream ->
                    ZipInputStream(stream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            when (entry.name) {
                                INFO_CARD_FILE -> {
                                    val json = zip.readBytes().toString(Charsets.UTF_8)
                                    importedCard = gson.fromJson(json, SharePackage::class.java)?.card?.normalized()
                                }

                                BACKGROUND_FILE -> {
                                    val outputDir = File(context.filesDir, "info_cards")
                                    outputDir.mkdirs()
                                    val outputFile = File(
                                        outputDir,
                                        "bg-imported-${System.currentTimeMillis()}.webp"
                                    )
                                    FileOutputStream(outputFile).use { fos ->
                                        zip.copyTo(fos)
                                    }
                                    importedImagePath = outputFile.absolutePath
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }

                importedCard?.copy(backgroundImagePath = importedImagePath ?: importedCard.backgroundImagePath)
            }.getOrNull()
        }

    suspend fun exportSharePackage(context: Context, card: InfoCard): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val packageBytes = exportSharePackageBytes(context, card) ?: return@runCatching null
            val packageDir = File(context.cacheDir, "info_cards")
            packageDir.mkdirs()
            val outputFile = File(packageDir, "info-card-${System.currentTimeMillis()}.kcard")
            FileOutputStream(outputFile).use { it.write(packageBytes) }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
        }.getOrNull()
    }

    suspend fun importSharePackage(context: Context, packageUri: Uri): InfoCard? = withContext(Dispatchers.IO) {
        runCatching {
            val packageBytes = context.contentResolver.openInputStream(packageUri)?.use { it.readBytes() }
                ?: return@runCatching null
            importSharePackageBytes(context, packageBytes)
        }.getOrNull()
    }

    fun buildShareIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = SHARE_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private data class SharePackage(
        val version: Int,
        val card: InfoCard
    )

    private fun applyExifOrientation(bitmap: Bitmap, exif: ExifInterface): Bitmap {
        val matrix = Matrix()
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}


