package app.vault.workspace.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    /** AES-GCM ciphertext of display name under VMK — never plaintext. */
    val nameCipher: ByteArray,
    val mimeType: String,
    val sizeBytes: Long,
    val category: String,
    val createdAt: Long,
    val deletedAt: Long? = null,
    /** Wrapped DEK (nonce||ct||tag) under VMK. */
    val dekWrap: ByteArray,
    val hasThumb: Boolean = false,
    val favorite: Boolean = false,
    /** Nullable folder membership; null = unfiled / root. */
    val folderId: String? = null,
    /**
     * Legacy flag from seek-prepare era. Kept for Room schema; UI ignores it
     * and imports always store true. Player uses normal ExoPlayer seek.
     */
    val seekReady: Boolean = true,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultItemEntity) return false
        return id == other.id &&
            nameCipher.contentEquals(other.nameCipher) &&
            mimeType == other.mimeType &&
            sizeBytes == other.sizeBytes &&
            category == other.category &&
            createdAt == other.createdAt &&
            deletedAt == other.deletedAt &&
            dekWrap.contentEquals(other.dekWrap) &&
            hasThumb == other.hasThumb &&
            favorite == other.favorite &&
            folderId == other.folderId &&
            seekReady == other.seekReady
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nameCipher.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        result = 31 * result + dekWrap.contentHashCode()
        result = 31 * result + hasThumb.hashCode()
        result = 31 * result + favorite.hashCode()
        result = 31 * result + (folderId?.hashCode() ?: 0)
        result = 31 * result + seekReady.hashCode()
        return result
    }
}

enum class VaultCategory {
    IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER;

    companion object {
        fun fromMime(mime: String): VaultCategory {
            val m = mime.lowercase()
            return when {
                m.startsWith("image/") -> IMAGE
                m.startsWith("video/") -> VIDEO
                m.startsWith("audio/") -> AUDIO
                m == "application/pdf" ||
                    m.startsWith("text/") ||
                    m == "application/json" ||
                    m == "application/xml" ||
                    m == "application/javascript" ||
                    m == "application/xhtml+xml" ||
                    m == "application/rtf" ||
                    m == "application/epub+zip" ||
                    m == "application/msword" ||
                    m == "application/vnd.ms-excel" ||
                    m == "application/vnd.ms-powerpoint" ||
                    m.contains("document") ||
                    m.contains("msword") ||
                    m.contains("ms-excel") ||
                    m.contains("ms-powerpoint") ||
                    m.contains("officedocument") ||
                    m.contains("opendocument") -> DOCUMENT
                else -> OTHER
            }
        }
    }
}
