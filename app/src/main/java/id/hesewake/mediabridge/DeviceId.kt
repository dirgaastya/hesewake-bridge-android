package id.hesewake.mediabridge

import java.security.MessageDigest

/**
 * The id this phone gives the PC for itself (`device_id`): the same every time, so that pairing again is not a second phone.
 * It is a hash of the Android ID, not the Android ID itself, and is only good for telling this phone from another one on the PC.
 * The Android ID stays the same after a reinstall of this app (with the same signing key) and after the data is cleared.
 */
internal object DeviceId {
    private const val SALT = "hesewake-bridge-device-v1:"

    /** 32 hex characters (within the 8 to 64 letters, digits, `-` and `_` the PC takes), or null if there is no Android ID. */
    fun of(androidId: String?): String? {
        val id = androidId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val hash = MessageDigest.getInstance("SHA-256").digest((SALT + id).toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it.toInt() and 255) }.take(32)
    }
}
