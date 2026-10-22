package com.budgetsphere.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.budgetsphere.app.domain.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*

@Singleton
class ReceiptStore
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val finance: FinanceRepository,
    private val paths: ReceiptPaths,
) {
    fun directory(scope: Scope): File = paths.directory(scope)

    fun file(scope: Scope, name: String): File = paths.file(scope, name)

    suspend fun import(scope: Scope, tx: String, uri: Uri) =
        withContext(Dispatchers.IO) {
            finance.validate(scope)
            require(finance.dao.transaction(scope.account, scope.profile, tx) != null) {
                "Transaction no longer exists"
            }
            val directory =
                directory(scope).apply {
                    check(exists() || mkdirs()) { "Cannot create receipt storage" }
                }
            val staging = File.createTempFile("import-", ".tmp", context.cacheDir)
            val id = newId()
            val target = file(scope, "$id.jpg")
            val thumbnail = file(scope, "$id-thumb.jpg")
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Image is unavailable" }
                    staging.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= 20 * 1024 * 1024) {
                                "Choose an image smaller than 20 MB"
                            }
                            output.write(buffer, 0, n)
                        }
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(staging.path, bounds)
                require(
                    bounds.outWidth > 0 &&
                        bounds.outHeight > 0 &&
                        bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000L
                ) {
                    "Unsupported or oversized image"
                }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                val decoded =
                    BitmapFactory.decodeFile(
                        staging.path,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    ) ?: error("Corrupt image")
                val orientation = runCatching { ExifInterface(staging) }.getOrNull()
                val matrix =
                    Matrix().apply {
                        if (orientation?.isFlipped == true) postScale(-1f, 1f)
                        postRotate((orientation?.rotationDegrees ?: 0).toFloat())
                    }
                val bitmap =
                    if (matrix.isIdentity) decoded
                    else
                        try {
                            Bitmap.createBitmap(
                                decoded,
                                0,
                                0,
                                decoded.width,
                                decoded.height,
                                matrix,
                                true,
                            )
                        } finally {
                            decoded.recycle()
                        }
                try {
                    target.outputStream().use {
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it))
                    }
                    val ratio = 320f / maxOf(bitmap.width, bitmap.height).coerceAtLeast(320)
                    val small =
                        Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * ratio).toInt().coerceAtLeast(1),
                            (bitmap.height * ratio).toInt().coerceAtLeast(1),
                            true,
                        )
                    try {
                        thumbnail.outputStream().use {
                            check(small.compress(Bitmap.CompressFormat.JPEG, 75, it))
                        }
                    } finally {
                        if (small !== bitmap) small.recycle()
                    }
                } finally {
                    bitmap.recycle()
                }
                finance.validate(scope)
                require(finance.dao.transaction(scope.account, scope.profile, tx) != null)
                finance.dao.insertAttachment(
                    AttachmentEntity(
                        scope.account,
                        scope.profile,
                        id,
                        tx,
                        target.name,
                        thumbnail.name,
                    )
                )
            } catch (e: Exception) {
                target.delete()
                thumbnail.delete()
                throw e
            } finally {
                staging.delete()
            }
        }

    suspend fun remove(scope: Scope, id: String) =
        withContext(Dispatchers.IO) {
            finance.validate(scope)
            val attachment =
                finance.dao.attachmentList(scope.account, scope.profile).find { it.id == id }
                    ?: error("Receipt no longer exists")
            finance.dao.deleteAttachment(scope.account, scope.profile, id)
            file(scope, attachment.filename).delete()
            file(scope, attachment.thumbnail).delete()
        }

    suspend fun collectOrphans(scope: Scope) =
        withContext(Dispatchers.IO) {
            finance.validate(scope)
            val referenced =
                finance.dao
                    .attachmentList(scope.account, scope.profile)
                    .flatMap { listOf(it.filename, it.thumbnail) }
                    .toSet()
            // Leave fresh files alone: an import may be between writing a file and committing
            // metadata.
            context.cacheDir
                .listFiles()
                ?.filter {
                    it.name.startsWith("import-") &&
                        it.extension == "tmp" &&
                        System.currentTimeMillis() - it.lastModified() > 86400000L
                }
                ?.forEach { it.delete() }
            File(context.cacheDir, "camera")
                .listFiles()
                ?.filter { System.currentTimeMillis() - it.lastModified() > 86400000L }
                ?.forEach { it.delete() }
            directory(scope)
                .listFiles()
                ?.filter {
                    it.name !in referenced &&
                        System.currentTimeMillis() - it.lastModified() > 86400000L
                }
                ?.forEach { it.delete() }
        }
}
