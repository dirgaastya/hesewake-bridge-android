package id.hesewake.mediabridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkPolicyTest {
    @Test fun sendsOnceOnlyWhenReceiverSupportsIt() {
        val policy = ArtworkPolicy()
        assertFalse(policy.unchanged(true, true, "A"))
        policy.sent(true, "A")
        assertTrue(policy.unchanged(true, true, "A"))
        assertFalse(policy.unchanged(false, true, "A"))
        assertFalse(policy.unchanged(true, true, "B"))
        assertFalse(policy.unchanged(true, true, null))
        policy.sent(true, null)
        assertFalse(policy.unchanged(true, true, "A"))
    }
    @Test fun inactiveOrNewConnectionRequiresFreshArtwork() {
        val policy = ArtworkPolicy()
        policy.sent(true, "A"); policy.sent(false, null)
        assertFalse(policy.unchanged(true, true, "A"))
        policy.sent(true, "A"); policy.reset()
        assertFalse(policy.unchanged(true, true, "A"))
    }
}
