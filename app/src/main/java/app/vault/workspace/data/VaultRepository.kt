package app.vault.workspace.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import app.vault.workspace.auth.SessionManager
import app.vault.workspace.crypto.KeyHierarchy
import app.vault.workspace.crypto.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class VaultItem(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: VaultCategory,
    val createdAt: Long,
    val hasThumb: Boolean,
)

class VaultRepository(
    private val context: Context,
    private val session: SessionManager,
    private val dao: VaultItemDao = VaultDatabase.get(context).vaultItemDao(),
) {
    fun observeLibrary(): Flow<List<VaultItem>> =
        dao.observeAll().map { list ->
            val vmk = session.peekVmk() ?: return@map emptyList()
            list.mapNotNull { entity ->
                try {
                    VaultItem(
                        id = entity.id,
                        displayName = NameCipher.decrypt(vmk, entity.nameCipher),
                        mimeType = entity.mimeType,
                        sizeBytes = entity.sizeBytes,
                        category = runCatching { VaultCategory.valueOf(entity.category) }
                            .getOrDefault(VaultCategory.OTHER),
                        createdAt = entity.createdAt,
                        hasThumb = entity.hasThumb,
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }

    suspend fun getItem(id: String): VaultItem? = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext null
        val vmk = session.requireVmk()
        VaultItem(
            id = entity.id,
            displayName = NameCipher.decrypt(vmk, entity.nameCipher),
            mimeType = entity.mimeType,
            sizeBytes = entity.sizeBytes,
            category = runCatching { VaultCategory.valueOf(entity.category) }
                .getOrDefault(VaultCategory.OTHER),
            createdAt = entity.createdAt,
            hasThumb = entity.hasThumb,
        )
    }

    fun blobFile(id: String): File = File(session.blobsDir(), "$id.vat")
    fun thumbFile(id: String): File = File(session.thumbsDir(), "$id.vat")

    suspend fun unwrapDek(id: String): ByteArray = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: error("Missing item")
        KeyHierarchy.unwrapDek(session.requireVmk(), entity.dekWrap)
    }

    suspend fun importUri(uri: Uri, onProgress: ((Float) -> Unit)? = null): Result<VaultItem> =
        withContext(Dispatchers.IO) {
            try {
                val vmk = session.requireVmk()
                val cr = context.contentResolver
                val name = queryDisplayName(uri) ?: "import-${System.currentTimeMillis()}"
                val mime = cr.getType(uri) ?: "application/octet-stream"
                val sizeHint = querySize(uri)

                val id = UUID.randomUUID().toString()
                val dek = KeyHierarchy.generateDek()
                val dest = blobFile(id)
                val tmp = session.tmpDir()

                val (size, header) = if (sizeHint != null && sizeHint >= 0L) {
                    cr.openInputStream(uri)?.use { input ->
                        val h = VaultCrypto.encryptStream(input, sizeHint, dek, dest, tmp)
                        sizeHint to h
                    } ?: return@withContext Result.failure(IllegalStateException("Cannot open $uri"))
                } else {
                    // Fallback: copy to private temp, then encrypt
                    val staging = File(tmp, "$id.staging")
                    cr.openInputStream(uri)?.use { input ->
                        FileOutputStream(staging).use { output -> input.copyTo(output) }
                    } ?: return@withContext Result.failure(IllegalStateException("Cannot open $uri"))
                    val sz = staging.length()
                    staging.inputStream().use { input ->
                        val h = VaultCrypto.encryptStream(input, sz, dek, dest, tmp)
                        staging.delete()
                        sz to h
                    }
                }
                onProgress?.invoke(1f)

                val wrappedDek = KeyHierarchy.wrapDek(vmk, dek)
                var hasThumb = false
                if (mime.startsWith("image/")) {
                    hasThumb = runCatching {
                        createThumb(uri, id, dek, vmk)
                    }.getOrDefault(false)
                }
                KeyHierarchy.wipe(dek)

                val entity = VaultItemEntity(
                    id = id,
                    nameCipher = NameCipher.encrypt(vmk, name),
                    mimeType = mime,
                    sizeBytes = size,
                    category = VaultCategory.fromMime(mime).name,
                    createdAt = System.currentTimeMillis(),
                    dekWrap = wrappedDek,
                    hasThumb = hasThumb,
                )
                dao.insert(entity)
                Result.success(
                    VaultItem(
                        id = id,
                        displayName = name,
                        mimeType = mime,
                        sizeBytes = size,
                        category = VaultCategory.fromMime(mime),
                        createdAt = entity.createdAt,
                        hasThumb = hasThumb,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun createThumb(uri: Uri, id: String, dek: ByteArray, vmk: ByteArray): Boolean {
        val bitmap = decodeSampled(uri, 512) ?: return false
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        bitmap.recycle()
        val bytes = baos.toByteArray()
        VaultCrypto.encryptBytes(bytes, dek, thumbFile(id), session.tmpDir())
        return true
    }

    private fun decodeSampled(uri: Uri, maxSide: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = maxOf(w, h).toFloat() / maxSide
                    if (scale > 1f) {
                        decoder.setTargetSampleSize(scale.toInt().coerceAtLeast(1))
                    }
                    decoder.isMutableRequired = false
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                var halfW = bounds.outWidth / 2
                var halfH = bounds.outHeight / 2
                while (halfW / sample >= maxSide && halfH / sample >= maxSide) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun decryptToTempPdf(id: String): File = withContext(Dispatchers.IO) {
        val dek = unwrapDek(id)
        try {
            val out = File(session.tmpDir(), "$id.pdf")
            FileOutputStream(out).use { fos ->
                VaultCrypto.decryptToStream(blobFile(id), dek, fos)
                fos.fd.sync()
            }
            out
        } finally {
            KeyHierarchy.wipe(dek)
        }
    }

    suspend fun decryptFully(id: String): ByteArray = withContext(Dispatchers.IO) {
        val dek = unwrapDek(id)
        try {
            VaultCrypto.decryptToBytes(blobFile(id), dek)
        } finally {
            KeyHierarchy.wipe(dek)
        }
    }

    suspend fun exportToUri(id: String, dest: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dek = unwrapDek(id)
            try {
                context.contentResolver.openOutputStream(dest)?.use { out ->
                    VaultCrypto.decryptToStream(blobFile(id), dek, out)
                } ?: return@withContext Result.failure(IllegalStateException("Cannot open destination"))
                Result.success(Unit)
            } finally {
                KeyHierarchy.wipe(dek)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteItem(id: String) = withContext(Dispatchers.IO) {
        dao.hardDelete(id)
        blobFile(id).delete()
        thumbFile(id).delete()
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx)
                }
            }
        return uri.lastPathSegment
    }

    private fun querySize(uri: Uri): Long? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !c.isNull(idx)) {
                        val v = c.getLong(idx)
                        if (v >= 0) return v
                    }
                }
            }
        return null
    }
}
