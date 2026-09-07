package app.vault.workspace.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VaultItemEntity>>

    @Query(
        """
        SELECT * FROM vault_items
        WHERE deletedAt IS NULL AND (
            (:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId
        )
        ORDER BY createdAt DESC
        """,
    )
    fun observeItemsInFolder(folderId: String?): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: VaultItemEntity)

    @Query("UPDATE vault_items SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE vault_items SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("UPDATE vault_items SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE vault_items SET sizeBytes = :sizeBytes WHERE id = :id")
    suspend fun setSizeBytes(id: String, sizeBytes: Long)

    @Query("UPDATE vault_items SET mimeType = :mimeType WHERE id = :id")
    suspend fun setMimeType(id: String, mimeType: String)

    @Query("UPDATE vault_items SET hasThumb = :hasThumb WHERE id = :id")
    suspend fun setHasThumb(id: String, hasThumb: Boolean)

    @Query("UPDATE vault_items SET folderId = :folderId WHERE id = :id")
    suspend fun setItemFolder(id: String, folderId: String?)

    @Query("UPDATE vault_items SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolderFromItems(folderId: String)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT id FROM vault_items WHERE deletedAt IS NOT NULL")
    suspend fun listTrashIds(): List<String>

    /** Total plaintext size of all vault items (library + trash). No schema change. */
    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM vault_items")
    fun observeTotalSizeBytes(): Flow<Long>
}
