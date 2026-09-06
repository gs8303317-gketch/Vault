package app.vault.workspace.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_folders")
data class VaultFolderEntity(
    @PrimaryKey val id: String,
    /** AES-GCM ciphertext of folder name under VMK — never plaintext. */
    val nameCipher: ByteArray,
    val createdAt: Long,
    val parentId: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultFolderEntity) return false
        return id == other.id &&
            nameCipher.contentEquals(other.nameCipher) &&
            createdAt == other.createdAt &&
            parentId == other.parentId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nameCipher.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (parentId?.hashCode() ?: 0)
        return result
    }
}
