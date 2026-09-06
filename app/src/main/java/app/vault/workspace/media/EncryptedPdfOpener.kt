package app.vault.workspace.media

import android.content.Context
import java.io.File

/** PDF-facing alias for [EncryptedSeekableHandle] (proxy/memfd/tmp). */
typealias EncryptedPdfHandle = EncryptedSeekableHandle

/**
 * Seekable read-only [android.os.ParcelFileDescriptor] over a VAULT1 PDF without a durable
 * plaintext cache when possible (proxy FD / memfd). Last resort: session tmp file
 * deleted+wiped on [EncryptedPdfHandle.releaseResources].
 */
object EncryptedPdfOpener {
    /**
     * @param dek caller-owned; this method copies what it needs and does not wipe [dek].
     */
    fun open(
        context: Context,
        vatFile: File,
        dek: ByteArray,
        tmpDir: File,
        tempFileName: String,
    ): EncryptedPdfHandle =
        EncryptedSeekableOpener.open(
            context = context,
            vatFile = vatFile,
            dek = dek,
            tmpDir = tmpDir,
            tempFileName = tempFileName,
            threadName = "vault-pdf-proxy",
            allowTempFile = true,
            memfdMaxBytes = Long.MAX_VALUE,
        )
}
