package app.tenet.android.core.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Whole-database encryption with SQLCipher (App_Konzept.md 9.2).
 *
 * The passphrase is 32 random bytes (as hex), created once and stored
 * wrapped with an AES key from the Android Keystore in `no_backup` – so it
 * never leaves the device and is not part of Android's auto backup. Data
 * that should survive a new phone travels through the cloud sync instead.
 *
 * [prepare] runs before Room opens the file: an existing plaintext database
 * (installs before 1.0.0.6) is encrypted once in place via
 * `sqlcipher_export`; a database that can no longer be decrypted (key lost,
 * e.g. restored from a backup onto another phone) is moved aside, so the
 * app starts empty and the sync can fill it again instead of crashing.
 */
object DatabaseEncryption {

    private const val TAG = "DatabaseEncryption"
    private const val KEY_ALIAS = "tenet_db_key"
    private const val KEY_FILE = "db.key"
    private const val SQLITE_HEADER = "SQLite format 3\u0000"

    /** Key bytes for [net.zetetic.database.sqlcipher.SupportOpenHelperFactory]; prepares the file first. */
    fun prepare(context: Context, name: String): ByteArray {
        System.loadLibrary("sqlcipher")
        val file = context.getDatabasePath(name)
        val passphrase = loadOrCreatePassphrase(context, databaseExists = file.exists())
        val key = rawKey(passphrase)
        if (file.exists()) {
            when {
                isPlaintext(file) -> runCatching { encryptInPlace(file, key) }.onFailure {
                    // Keep working unencrypted (empty key = plain SQLite) rather than lock the user out; retried next start.
                    Log.e(TAG, "Encrypting the existing database failed, keeping it as is", it)
                    File(file.path + ".encrypting").delete()
                    return ByteArray(0)
                }
                canOpen(file, key) -> Unit
                // Test builds keyed the hex string through PBKDF2: switch to the raw key once.
                canOpen(file, passphrase) -> runCatching { rekey(file, passphrase, key) }
                    .onFailure { moveAside(file, "unreadable") }
                else -> moveAside(file, "unreadable")
            }
        }
        return key.toByteArray(Charsets.UTF_8)
    }

    /**
     * The passphrase already is 256 random bits, so it is used as SQLCipher's
     * raw key (`x'…'`): no PBKDF2 with 256 000 rounds on every open, which
     * took about a second on the main thread at each app start.
     */
    private fun rawKey(hex: String) = "x'$hex'"

    private fun rekey(file: File, oldKey: String, newKey: String) {
        SQLiteDatabase.openDatabase(file.path, oldKey.toByteArray(Charsets.UTF_8), null, SQLiteDatabase.OPEN_READWRITE, null, null).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", emptyArray<String>()).use { it.moveToFirst() }
            db.rawExecSQL("PRAGMA rekey = \"$newKey\"")
        }
        check(canOpen(file, newKey)) { "Rekeyed database cannot be opened" }
        Log.i(TAG, "Database switched to raw key")
    }

    /** True while the file is still a normal SQLite database (readable header). */
    fun isPlaintext(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(16)
            input.read(header) == 16 && String(header, Charsets.US_ASCII) == SQLITE_HEADER
        }
    }.getOrDefault(false)

    private fun encryptInPlace(file: File, key: String) {
        val tmp = File(file.parentFile, file.name + ".encrypting")
        tmp.delete()
        // CREATE flag: ATTACH below creates the encrypted copy with the main connection's flags.
        val plain = SQLiteDatabase.openDatabase(file.path, "", null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null, null)
        try {
            // Room uses WAL: fold pending pages into the main file before copying.
            plain.rawQuery("PRAGMA wal_checkpoint(FULL)", emptyArray<String>()).use { it.moveToFirst() }
            val version = plain.rawQuery("PRAGMA user_version", emptyArray<String>()).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            plain.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf<Any>(tmp.path, key))
            plain.rawQuery("SELECT sqlcipher_export('encrypted')", emptyArray<String>()).use { it.moveToFirst() }
            plain.execSQL("PRAGMA encrypted.user_version = $version")
            plain.execSQL("DETACH DATABASE encrypted")
        } finally {
            plain.close()
        }
        check(canOpen(tmp, key)) { "Encrypted copy cannot be opened" }
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
        check(tmp.renameTo(file)) { "Could not replace the database file" }
        Log.i(TAG, "Database encrypted")
    }

    private fun canOpen(file: File, key: String): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, key.toByteArray(Charsets.UTF_8), null, SQLiteDatabase.OPEN_READONLY, null, null)
            .use { db -> db.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray<String>()).use { it.moveToFirst() } }
    }.getOrDefault(false)

    /** Keeps an unreadable database (and its WAL) next to it instead of deleting it. */
    private fun moveAside(file: File, reason: String) {
        val stamp = System.currentTimeMillis()
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val f = File(file.path + suffix)
            if (f.exists()) f.renameTo(File(file.parentFile, "${file.name}.$reason-$stamp$suffix"))
        }
        Log.w(TAG, "Database could not be decrypted, moved aside ($reason)")
    }

    // ---- Passphrase ---------------------------------------------------------

    private fun loadOrCreatePassphrase(context: Context, databaseExists: Boolean): String {
        val keyFile = File(context.noBackupFilesDir, KEY_FILE)
        if (keyFile.exists()) {
            runCatching { return unwrap(keyFile.readText()) }
                .onFailure { Log.e(TAG, "Stored database key unusable, creating a new one", it) }
        } else if (databaseExists) {
            Log.i(TAG, "No database key yet, creating one")
        }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val passphrase = bytes.joinToString("") { "%02x".format(it) }
        keyFile.writeText(wrap(passphrase))
        return passphrase
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun wrap(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun unwrap(stored: String): String {
        val (iv, data) = stored.trim().split(':').map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        }
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }
}
