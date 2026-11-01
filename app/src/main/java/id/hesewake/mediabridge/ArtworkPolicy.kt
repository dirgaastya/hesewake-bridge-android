package id.hesewake.mediabridge

/** A receiver must advertise artwork_delta_v1 before artwork may be omitted. */
internal class ArtworkPolicy {
    private var initialized = false
    private var previous: String? = null
    fun unchanged(enabled: Boolean, active: Boolean, artwork: String?): Boolean =
        enabled && active && initialized && artwork != null && artwork == previous
    fun sent(active: Boolean, artwork: String?) {
        initialized = active
        previous = if (active) artwork else null
    }
    fun reset() { initialized = false; previous = null }
}
