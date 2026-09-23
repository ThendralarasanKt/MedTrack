package com.medtrack.app.hybrid.backup

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

data class RestoreOutcome(
    val ok: Boolean,
    val message: String
)

@Singleton
class EncryptedBackupService @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun export(
        passphrase: CharArray,
        ownerAccountId: String,
        schemaVersion: Int,
        databaseFile: File,
        attachmentDir: File,
        wrappedDbKey: ByteArray
    ): File {
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", schemaVersion)
            .put("ownerAccountId", ownerAccountId)
            .put("createdAt", System.currentTimeMillis())
            .put("note", "Sign-in alone does not restore records. Cloud AI jobs are not a backup.")
            .toString()
            .toByteArray(Charsets.UTF_8)
        val zipBytes = ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("db/medtrack_secure.db"))
                if (databaseFile.exists()) zip.write(databaseFile.readBytes())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("db-key.bin"))
                zip.write(wrappedDbKey)
                zip.closeEntry()
                if (attachmentDir.exists()) {
                    attachmentDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        zip.putNextEntry(ZipEntry("attachments/${file.name}"))
                        zip.write(file.readBytes())
                        zip.closeEntry()
                    }
                }
            }
            buffer.toByteArray()
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(zipBytes)
        val out = File(context.cacheDir, "medtrack-backup.mtb")
        out.writeBytes(FORMAT_BYTES + salt + iv + encrypted)
        return out
    }

    fun restoreToStaging(
        backup: File,
        passphrase: CharArray,
        expectedOwnerAccountId: String,
        stagingDir: File
    ): RestoreOutcome {
        if (!backup.exists()) return RestoreOutcome(false, "Backup file not found.")
        val bytes = backup.readBytes()
        if (bytes.size < 32 || !bytes.copyOfRange(0, FORMAT_BYTES.size).contentEquals(FORMAT_BYTES)) {
            return RestoreOutcome(false, "Unsupported or tampered backup.")
        }
        val salt = bytes.copyOfRange(FORMAT_BYTES.size, FORMAT_BYTES.size + 16)
        val iv = bytes.copyOfRange(FORMAT_BYTES.size + 16, FORMAT_BYTES.size + 28)
        val payload = bytes.copyOfRange(FORMAT_BYTES.size + 28, bytes.size)
        val zipBytes = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
            cipher.doFinal(payload)
        } catch (_: Exception) {
            return RestoreOutcome(false, "Wrong recovery key. The active database was not changed.")
        }
        stagingDir.mkdirs()
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(stagingDir, entry.name)
                target.parentFile?.mkdirs()
                if (!entry.isDirectory) {
                    target.writeBytes(zip.readBytes())
                }
                entry = zip.nextEntry
            }
        }
        val manifestFile = File(stagingDir, "manifest.json")
        if (!manifestFile.exists()) {
            stagingDir.deleteRecursively()
            return RestoreOutcome(false, "Backup missing manifest. The active database was not changed.")
        }
        val manifest = JSONObject(manifestFile.readText())
        if (manifest.optString("format") != FORMAT) {
            stagingDir.deleteRecursively()
            return RestoreOutcome(false, "Unsupported backup format. The active database was not changed.")
        }
        if (manifest.optString("ownerAccountId") != expectedOwnerAccountId) {
            stagingDir.deleteRecursively()
            return RestoreOutcome(false, "Backup belongs to a different account. The active database was not changed.")
        }
        val schema = manifest.optInt("schemaVersion")
        if (schema > SUPPORTED_SCHEMA) {
            stagingDir.deleteRecursively()
            return RestoreOutcome(false, "Backup schema is newer than this app. The active database was not changed.")
        }
        return RestoreOutcome(true, "Staged restore is valid. Reminders must be reconciled on this device.")
    }

    fun fingerprint(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, 120_000, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    companion object {
        const val FORMAT = "medtrack-backup-v1"
        val FORMAT_BYTES: ByteArray = FORMAT.toByteArray(Charsets.UTF_8)
        const val SUPPORTED_SCHEMA = 13
    }
}
