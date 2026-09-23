package com.medtrack.app.data.db

import android.content.Context
import androidx.room.Room
import com.medtrack.app.security.DatabasePassphraseProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object AppDatabaseFactory {
    const val DATABASE_NAME = "medtrack_secure.db"
    private const val LEGACY_DATABASE_NAME = "medtrack_db"

    fun create(
        context: Context,
        passphraseProvider: DatabasePassphraseProvider = DatabasePassphraseProvider.from(context)
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        dropLegacyPlaintextIfAlreadyMigrated(context)

        val passphrase = passphraseProvider.getPassphrase()
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            .openHelperFactory(factory)
            .addMigrations(
                CareMigrations.MIGRATION_5_6,
                CareMigrations.MIGRATION_6_7,
                CareMigrations.MIGRATION_7_8,
                CareMigrations.MIGRATION_8_9,
                CareMigrations.MIGRATION_9_10,
                CareMigrations.MIGRATION_10_11,
                CareMigrations.MIGRATION_11_12,
                CareMigrations.MIGRATION_12_13
            )
            .build()
    }

    /**
     * Drop leftover plaintext only after the encrypted database already exists.
     * Never delete an unmigrated `medtrack_db` on a first encrypted launch.
     */
    internal fun dropLegacyPlaintextIfAlreadyMigrated(context: Context) {
        val secureExists = context.getDatabasePath(DATABASE_NAME).exists()
        val legacyExists = context.getDatabasePath(LEGACY_DATABASE_NAME).exists()
        if (secureExists && legacyExists) {
            context.deleteDatabase(LEGACY_DATABASE_NAME)
        }
    }
}
