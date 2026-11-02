package id.hesewake.mediabridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkUrlTest {
    @Test fun resizesKnownGoogleArtworkWithoutChangingQuery() {
        assertEquals("https://lh3.googleusercontent.com/image=w1200-h1200?x=w20-h20", ArtworkUrl.higherResolution("https://lh3.googleusercontent.com/image=w544-h544?x=w20-h20"))
        assertEquals("https://yt3.ggpht.com/art=s1200", ArtworkUrl.higherResolution("https://yt3.ggpht.com/art=s544"))
    }
    @Test fun rejectsUntrustedUrls() {
        listOf("http://lh3.googleusercontent.com/a", "https://googleusercontent.com.evil.test/a", "https://user@ggpht.com/a", "https://ggpht.com:444/a", "content://images/1").forEach { assertNull(ArtworkUrl.higherResolution(it)) }
    }
}
