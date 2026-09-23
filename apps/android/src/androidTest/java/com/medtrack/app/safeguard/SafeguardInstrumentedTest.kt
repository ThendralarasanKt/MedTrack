package com.medtrack.app.safeguard

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.medtrack.app.BuildConfig
import com.medtrack.app.data.care.CareCatalogBootstrap
import com.medtrack.app.data.care.CareLocalSession
import com.medtrack.app.data.care.command.CareClinicalAssessmentService
import com.medtrack.app.data.care.command.CareCommandService
import com.medtrack.app.data.care.command.CareTherapyCommandService
import com.medtrack.app.data.care.command.CareWorkCommandService
import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.data.care.entity.CarePersonEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.mcp.transport.LocalMcpHttpServer
import com.medtrack.app.startup.StartupRecordPolicy
import com.medtrack.app.testdata.SyntheticDataSeeder
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.medtrack.app.data.db.AppDatabase

@RunWith(AndroidJUnit4::class)
class SafeguardInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val dbName = "safeguard_regression_test.db"
    private val startupPolicy = StartupRecordPolicy()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun dischargedAdmissionSurvivesApplicationStartupPolicy() = runBlocking {
        val now = System.currentTimeMillis()
        database.careDao().insertPerson(
            CarePersonEntity(
                id = "person-keep",
                ownerAccountId = CareLocalSession.OWNER_ACCOUNT_ID,
                displayName = "Kept Discharged",
                identityState = CareEnums.IdentityState.CONFIRMED.name,
                createdAt = now,
                createdBy = CareLocalSession.ACTOR_PERSON_ID
            )
        )
        startupPolicy.onApplicationStart(database)
        assertNotNull(database.careDao().getPerson("person-keep"))
    }

    @Test
    fun unsupportedSchemaFailsWithoutDestroyingExistingRows() {
        runBlocking {
            database.careDao().insertPerson(
                CarePersonEntity(
                    id = "person-guard",
                    ownerAccountId = CareLocalSession.OWNER_ACCOUNT_ID,
                    displayName = "Schema Guard Patient",
                    identityState = CareEnums.IdentityState.CONFIRMED.name,
                    createdAt = System.currentTimeMillis(),
                    createdBy = CareLocalSession.ACTOR_PERSON_ID
                )
            )
        }
        database.close()

        val dbFile = context.getDatabasePath(dbName)
        android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        ).use { sqlite ->
            sqlite.execSQL("PRAGMA user_version = 999")
        }

        assertThrows(IllegalStateException::class.java) {
            val invalidDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
            try {
                invalidDb.openHelper.writableDatabase
            } finally {
                invalidDb.close()
            }
        }

        android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        ).use { sqlite ->
            sqlite.rawQuery("SELECT displayName FROM care_persons WHERE id = 'person-guard'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Schema Guard Patient", cursor.getString(0))
            }
            sqlite.rawQuery("PRAGMA user_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(999, cursor.getInt(0))
            }
        }
    }

    @Test
    fun localMcpServerIsNotListeningByDefault() {
        assertFalse(BuildConfig.ENABLE_LOCAL_MCP_SERVER)
        assertFalse(isPortOpen(LocalMcpHttpServer.HOST, LocalMcpHttpServer.PORT))
    }

    @Test
    fun syntheticSeederCreatesCareAdmissions() = runBlocking {
        val writePath = CareWritePath(
            CareCommandService(database, database.careDao()),
            CareClinicalAssessmentService(database, database.careDao()),
            CareTherapyCommandService(database, database.careDao(), database.careTherapyDao()),
            CareWorkCommandService(database, database.careDao(), database.careWorkDao())
        )
        val seeder = SyntheticDataSeeder(
            CareCatalogBootstrap(database.careDao()),
            writePath,
            database.careWorkDao()
        )
        assertTrue(seeder.seedIfEmpty())
        assertFalse(seeder.seedIfEmpty())
        val active = database.careWorkDao().activeEpisodes(CareLocalSession.OWNER_ACCOUNT_ID)
        assertTrue(active.isNotEmpty())
        val discharged = database.careDao().episodesByStatus(
            CareLocalSession.OWNER_ACCOUNT_ID,
            CareEnums.EpisodeStatus.DISCHARGED.name
        )
        assertTrue(discharged.isNotEmpty())
    }

    private fun isPortOpen(host: String, port: Int): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 500)
                true
            }
        }.getOrDefault(false)
}
