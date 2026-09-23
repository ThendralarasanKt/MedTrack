package com.medtrack.app.hybrid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.medtrack.app.hybrid.backup.EncryptedBackupService
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EncryptedBackupServiceTest {
    @Test
    fun wrongKeyAndWrongAccountDoNotOverwrite() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = EncryptedBackupService(context)
        val dbFile = File(context.cacheDir, "src.db").apply { writeText("synthetic-db") }
        val attachments = File(context.cacheDir, "atts").apply { mkdirs() }
        val backup = service.export(
            passphrase = "correct-horse".toCharArray(),
            ownerAccountId = "acct-a",
            schemaVersion = 13,
            databaseFile = dbFile,
            attachmentDir = attachments,
            wrappedDbKey = byteArrayOf(1, 2, 3)
        )
        val staging = File(context.cacheDir, "stage-${System.nanoTime()}")
        val wrongKey = service.restoreToStaging(backup, "wrong".toCharArray(), "acct-a", staging)
        assertFalse(wrongKey.ok)
        assertTrue(wrongKey.message.contains("not changed") || wrongKey.message.contains("Wrong"))
        val wrongAccount = service.restoreToStaging(
            backup,
            "correct-horse".toCharArray(),
            "acct-b",
            File(context.cacheDir, "stage-b")
        )
        assertFalse(wrongAccount.ok)
        assertTrue(wrongAccount.message.contains("different account"))
        val ok = service.restoreToStaging(
            backup,
            "correct-horse".toCharArray(),
            "acct-a",
            File(context.cacheDir, "stage-ok")
        )
        assertTrue(ok.ok)
    }
}
