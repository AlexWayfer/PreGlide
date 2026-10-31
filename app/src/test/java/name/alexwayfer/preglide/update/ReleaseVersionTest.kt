package name.alexwayfer.preglide.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun missingPartsAreZeroAndNumbersAreComparedAsNumbers() {
        assertEquals(ReleaseVersion.parse("1.0"), ReleaseVersion.parse("1.0.0"))
        assertEquals(ReleaseVersion.parse("1.0")?.hashCode(), ReleaseVersion.parse("1.0.0")?.hashCode())
        assertTrue(checkNotNull(ReleaseVersion.parse("1.10")) > checkNotNull(ReleaseVersion.parse("1.9")))
        assertEquals("1.0.1", ReleaseVersion.parse("1.0.1").toString())
    }

    @Test
    fun malformedOrOverflowingVersionsAreNotReleases() {
        for (text in listOf("", "v1.0", "1..0", "1.0-beta", "-1.0", "999999999999.0")) {
            assertNull(text, ReleaseVersion.parse(text))
        }
    }
}
