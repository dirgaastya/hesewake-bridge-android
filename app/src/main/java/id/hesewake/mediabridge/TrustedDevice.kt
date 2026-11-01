package id.hesewake.mediabridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class TrustedDevice(val name: String, val host: String, val port: Int, val fingerprint: String, val token: String)

/** The record is encrypted at rest with a non-exportable Android Keystore key. */
internal class TrustedStore(context: Context) {
    private val prefs = context.getSharedPreferences("trusted", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    fun save(d: TrustedDevice) {
        val json = JSONObject().put("name", d.name).put("host", d.host).put("port", d.port)
            .put("fingerprint", d.fingerprint).put("token", d.token).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.iv + cipher.doFinal(json)
        check(prefs.edit().putString("device", Base64.encodeToString(data, Base64.NO_WRAP)).commit())
    }
    fun load(): TrustedDevice? = runCatching {
        val raw = Base64.decode(prefs.getString("device", null) ?: return null, Base64.NO_WRAP)
        require(raw.size > 12 + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12))) }
        val obj = JSONObject(String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8))
        TrustedDevice(obj.getString("name"), obj.getString("host"), obj.getInt("port"), obj.getString("fingerprint"), obj.getString("token"))
    }.getOrNull()
    fun clear() { prefs.edit().remove("device").apply() }
    companion object { private const val ALIAS = "hesewake_media_bridge_v1" }
}
