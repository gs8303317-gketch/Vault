package app.vault.workspace.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import app.vault.workspace.auth.SessionManager
import app.vault.workspace.crypto.KeyHierarchy
import app.vault.workspace.crypto.VaultCrypto
import app.vault.workspace.media.EncryptedPdfHandle
import app.vault.workspace.media.EncryptedPdfOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class VaultFolder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val parentId: String? = null,
)

data class VaultItem(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: VaultCategory,
    val createdAt: Long,
    val hasThumb: Boolean,
    val favorite: Boolean = false,
    val deletedAt: Long? = null,
    val folderId: String? = null,
    /** Legacy column; unused by player (always treated ready). */
    val seekReady: Boolean = true,
)

class VaultRepository(
    private val context: Context,
    private val session: SessionManager,
    private val dao: VaultItemDao = VaultDatabase.get(context).vaultItemDao(),
    private val folderDao: VaultFolderDao = VaultDatabase.get(context).vaultFolderDao(),
) {
    private fun mapEntity(entity: VaultItemEntity, vmk: ByteArray): VaultItem? =
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
                favorite = entity.favorite,
                deletedAt = entity.deletedAt,
                folderId = entity.folderId,
                seekReady = entity.seekReady,
            )
        } catch (_: Exception) {
            null
        }

    /**
     * @param folderId null = all non-deleted items (default library).
     *                 Non-null = items in that folder only.
     */
    fun observeLibrary(folderId: String? = null): Flow<List<VaultItem>> {
        val source = if (folderId == null) dao.observeAll() else dao.observeItemsInFolder(folderId)
        return source.map { list ->
            val vmk = session.peekVmk() ?: return@map emptyList()
            list.mapNotNull { mapEntity(it, vmk) }
        }
    }

    fun observeFolders(): Flow<List<VaultFolder>> =
        folderDao.observeFolders().map { list ->
            val vmk = session.peekVmk() ?: return@map emptyList()
            list.mapNotNull { entity ->
                try {
                    VaultFolder(
                        id = entity.id,
                        name = NameCipher.decrypt(vmk, entity.nameCipher),
                        createdAt = entity.createdAt,
                        parentId = entity.parentId,
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }

    suspend fun createFolder(name: String, parentId: String? = null): Result<VaultFolder> =
        withContext(Dispatchers.IO) {
            try {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Folder name required"))
                }
                val vmk = session.requireVmk()
                val id = UUID.randomUUID().toString()
                val createdAt = System.currentTimeMillis()
                val entity = VaultFolderEntity(
                    id = id,
                    nameCipher = NameCipher.encrypt(vmk, trimmed),
                    createdAt = createdAt,
                    parentId = parentId,
                )
                folderDao.insertFolder(entity)
                Result.success(VaultFolder(id, trimmed, createdAt, parentId))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun renameFolder(id: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Folder name required"))
            }
            val vmk = session.requireVmk()
            folderDao.renameFolder(id, NameCipher.encrypt(vmk, trimmed))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Move folder items to root (unfiled), then delete the folder row. */
    suspend fun deleteFolder(id: String) = withContext(Dispatchers.IO) {
        dao.clearFolderFromItems(id)
        folderDao.deleteFolder(id)
    }

    suspend fun setItemFolder(itemId: String, folderId: String?) = withContext(Dispatchers.IO) {
        dao.setItemFolder(itemId, folderId)
    }

    suspend fun getFolder(id: String): VaultFolder? = withContext(Dispatchers.IO) {
        val entity = folderDao.getById(id) ?: return@withContext null
        val vmk = session.requireVmk()
        try {
            VaultFolder(
                id = entity.id,
                name = NameCipher.decrypt(vmk, entity.nameCipher),
                createdAt = entity.createdAt,
                parentId = entity.parentId,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Sum of item sizeBytes (library + trash) — offline storage meter. */
    fun observeTotalStorageBytes(): Flow<Long> = dao.observeTotalSizeBytes()

    fun observeTrashItems(): Flow<List<VaultItem>> =
        dao.observeTrash().map { list ->
            val vmk = session.peekVmk() ?: return@map emptyList()
            list.mapNotNull { mapEntity(it, vmk) }
        }

    suspend fun getItem(id: String): VaultItem? = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext null
        val vmk = session.requireVmk()
        mapEntity(entity, vmk)
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
                when {
                    mime.startsWith("image/") -> {
                        hasThumb = runCatching {
                            createThumb(uri, id, dek)
                        }.getOrDefault(false)
                    }
                    mime.startsWith("video/") -> {
                        hasThumb = runCatching {
                            createVideoThumb(uri, id, dek)
                        }.getOrDefault(false)
                    }
                    mime.equals("application/pdf", ignoreCase = true) -> {
                        hasThumb = runCatching {
                            createPdfThumb(uri, id, dek)
                        }.getOrDefault(false)
                    }
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
                    favorite = false,
                    seekReady = true,
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
                        favorite = false,
                        folderId = null,
                        seekReady = true,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun loadThumbBitmap(id: String, maxSide: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getById(id) ?: return@withContext null
            if (!entity.hasThumb) return@withContext null
            val file = thumbFile(id)
            if (!file.exists()) return@withContext null
            val dek = KeyHierarchy.unwrapDek(session.requireVmk(), entity.dekWrap)
            try {
                val bytes = VaultCrypto.decryptToBytes(file, dek)
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                scaleToMaxSide(decoded, maxSide)
            } finally {
                KeyHierarchy.wipe(dek)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createThumb(uri: Uri, id: String, dek: ByteArray): Boolean {
        val bitmap = decodeSampled(uri, 512) ?: return false
        return encryptThumbBitmap(bitmap, id, dek)
    }

    private fun createVideoThumb(uri: Uri, id: String, dek: ByteArray): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: retriever.frameAtTime ?: return false
            val scaled = scaleToMaxSide(frame, 512)
            encryptThumbBitmap(scaled, id, dek)
        } catch (_: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun createPdfThumb(uri: Uri, id: String, dek: ByteArray): Boolean {
        val staging = File(session.tmpDir(), "$id.pdfthumb")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(staging).use { out -> input.copyTo(out) }
            } ?: return false
            val pfd = ParcelFileDescriptor.open(staging, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount < 1) return false
                    renderer.openPage(0).use { page ->
                        val scale = 512f / maxOf(page.width, page.height).coerceAtLeast(1)
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        encryptThumbBitmap(bmp, id, dek)
                    }
                }
            }
        } catch (_: Exception) {
            false
        } finally {
            staging.delete()
        }
    }

    private fun encryptThumbBitmap(bitmap: Bitmap, id: String, dek: ByteArray): Boolean {
        val baos = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        if (!bitmap.isRecycled) bitmap.recycle()
        if (!ok) return false
        VaultCrypto.encryptBytes(baos.toByteArray(), dek, thumbFile(id), session.tmpDir())
        return true
    }

    private fun scaleToMaxSide(bitmap: Bitmap, maxSide: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = maxOf(w, h)
        if (longest <= maxSide || longest <= 0) return bitmap
        val scale = maxSide.toFloat() / longest
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
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

    /**
     * Open a seekable PDF without a durable plaintext temp when possible
     * (proxy FD / memfd). Caller must [EncryptedPdfHandle.releaseResources]
     * after PdfRenderer.close().
     */
    suspend fun openPdfHandle(id: String): EncryptedPdfHandle =
        withContext(Dispatchers.IO) {
            val dek = unwrapDek(id)
            try {
                EncryptedPdfOpener.open(
                    context = context,
                    vatFile = blobFile(id),
                    dek = dek,
                    tmpDir = session.tmpDir(),
                    tempFileName = "$id.pdf",
                )
            } finally {
                KeyHierarchy.wipe(dek)
            }
        }

    /** @deprecated Viewing uses [openPdfHandle]; kept only for emergency tooling. */
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

    suspend fun setFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavorite(id, favorite)
    }

    suspend fun moveToTrash(id: String) = withContext(Dispatchers.IO) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(id: String) = withContext(Dispatchers.IO) {
        dao.restore(id)
    }

    /** Permanent delete of one item (blob + thumb + row). */
    suspend fun hardDelete(id: String) = withContext(Dispatchers.IO) {
        dao.hardDelete(id)
        blobFile(id).delete()
        thumbFile(id).delete()
    }

    /** Permanently delete all soft-deleted items. */
    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val ids = dao.listTrashIds()
        for (id in ids) {
            dao.hardDelete(id)
            blobFile(id).delete()
            thumbFile(id).delete()
        }
    }

    @Deprecated("Use hardDelete or moveToTrash", ReplaceWith("hardDelete(id)"))
    suspend fun deleteItem(id: String) = hardDelete(id)

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
