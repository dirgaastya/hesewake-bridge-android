package id.hesewake.mediabridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdTest {
    @Test fun theSameAndroidIdGivesTheSameDeviceId() {
        assertEquals(DeviceId.of("9774d56d682e549c"), DeviceId.of("9774d56d682e549c"))
        assertEquals(DeviceId.of("9774d56d682e549c"), DeviceId.of("  9774d56d682e549c "))
    }

    @Test fun anotherAndroidIdGivesAnotherDeviceId() {
        assertNotEquals(DeviceId.of("9774d56d682e549c"), DeviceId.of("9774d56d682e549d"))
    }

    @Test fun theIdIsWhatThePcTakesAndDoesNotShowTheAndroidId() {
        val id = DeviceId.of("9774d56d682e549c")!!
        assertEquals(32, id.length)
        assertTrue(id.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue("9774d56d682e549c" !in id)
    }

    @Test fun noAndroidIdMeansNoDeviceId() {
        assertNull(DeviceId.of(null))
        assertNull(DeviceId.of(""))
        assertNull(DeviceId.of("   "))
    }
}
