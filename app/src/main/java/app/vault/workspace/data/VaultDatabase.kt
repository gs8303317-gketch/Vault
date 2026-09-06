package app.vault.workspace.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VaultItemEntity::class, VaultFolderEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun vaultFolderDao(): VaultFolderDao

    companion object {
        @Volatile private var instance: VaultDatabase? = null

        fun get(context: Context): VaultDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "vault.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
