package app.vault.workspace.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultFolderDao {
    @Query("SELECT * FROM vault_folders ORDER BY createdAt ASC")
    fun observeFolders(): Flow<List<VaultFolderEntity>>

    @Query("SELECT * FROM vault_folders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VaultFolderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolder(folder: VaultFolderEntity)

    @Query("UPDATE vault_folders SET nameCipher = :nameCipher WHERE id = :id")
    suspend fun renameFolder(id: String, nameCipher: ByteArray)

    @Query("DELETE FROM vault_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)
}
