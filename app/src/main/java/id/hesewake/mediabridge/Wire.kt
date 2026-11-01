package id.hesewake.mediabridge

import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal object Wire {
    const val MAX_FRAME = 1_048_576
    private val random = SecureRandom()
    fun bytes(n: Int) = ByteArray(n).also(random::nextBytes)
    fun b64(data: ByteArray) = Base64.encodeToString(data, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    fun unb64(value: String) = Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)
    fun hex(data: ByteArray) = data.joinToString("") { "%02x".format(it.toInt() and 255) }
    fun fingerprint(der: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(der))
    fun pairingCode(fingerprint: ByteArray, client: ByteArray, server: ByteArray): String {
        require(fingerprint.size == 32 && client.size == 32 && server.size == 32)
        val hash = MessageDigest.getInstance("SHA-256").apply {
            update("hesewake-media-pair-v1".toByteArray(Charsets.UTF_8)); update(fingerprint); update(client); update(server)
        }.digest()
        val first = ((hash[0].toLong() and 255) shl 24) or ((hash[1].toLong() and 255) shl 16) or ((hash[2].toLong() and 255) shl 8) or (hash[3].toLong() and 255)
        return (first % 1_000_000).toString().padStart(6, '0')
    }
    fun message(type: String) = JSONObject().put("v", 1).put("type", type)
    fun send(out: DataOutputStream, frame: JSONObject) {
        val bytes = frame.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_FRAME)
        synchronized(out) { out.writeInt(bytes.size); out.write(bytes); out.flush() }
    }
    fun read(input: DataInputStream): JSONObject {
        val n = input.readInt(); require(n in 1..MAX_FRAME) { "Invalid frame length" }
        val bytes = ByteArray(n); input.readFully(bytes)
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return JSONObject(decoder.decode(ByteBuffer.wrap(bytes)).toString()).also {
            validate(it, 0)
            require(it.getInt("v") == 1 && it.getString("type").length <= 64)
        }
    }
    private fun validate(value: Any?, depth: Int) {
        require(depth <= 16)
        when (value) {
            is String -> require(value.toByteArray(Charsets.UTF_8).size <= 4096)
            is JSONObject -> { require(value.length() <= 128); val keys = value.keys(); while (keys.hasNext()) { val key = keys.next(); validate(key, depth + 1); validate(value.opt(key), depth + 1) } }
            is JSONArray -> { require(value.length() <= 1024); for (i in 0 until value.length()) validate(value.opt(i), depth + 1) }
        }
    }
}
