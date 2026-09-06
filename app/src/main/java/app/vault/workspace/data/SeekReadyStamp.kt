package app.vault.workspace.data

/**
 * Pure check: DB [sizeBytes] matches VAULT1 header [plaintextSize].
 * Used to detect a stale seekReady flag after rewrite / partial prepare.
 */
object SeekReadyStamp {
    fun matches(sizeBytes: Long, plaintextSize: Long): Boolean =
        sizeBytes >= 0L && sizeBytes == plaintextSize
}
