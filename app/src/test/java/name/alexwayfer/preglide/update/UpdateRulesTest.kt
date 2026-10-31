package name.alexwayfer.preglide.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class UpdateRulesTest {
    private val installed = UpdateInstallation(checkNotNull(ReleaseVersion.parse("1.0")), 5, "free")
    private val release = UpdateRelease(1, checkNotNull(ReleaseVersion.parse("1.1")), 10, null)

    @Test
    fun githubComparesVersionNamesAndMecenaComparesVersionCodes() {
        assertTrue(updateIsNewer(release, installed))
        assertFalse(updateIsNewer(release.copy(version = installed.version), installed))
        assertTrue(updateIsNewer(release.copy(version = installed.version, versionCode = 6), installed))
        assertFalse(updateIsNewer(release.copy(versionCode = 5), installed))
    }

    @Test
    fun aCheckIsRepeatedAfterHalfAnHourOrAClockReset() {
        assertTrue(updateCheckDue(null, now = 1))
        assertFalse(updateCheckDue(1, now = 30.minutes.inWholeMilliseconds))
        assertTrue(updateCheckDue(1, now = 1 + 30.minutes.inWholeMilliseconds))
        assertTrue(updateCheckDue(2, now = 1))
        assertTrue(checksUpdateAfterProblem("main"))
        assertFalse(checksUpdateAfterProblem("AppUpdate"))
    }

    @Test
    fun anInstallerAcceptsOnlyANewerPackageWithTheSameIdAndSigners() {
        val own = UpdatePackage("app", 5, setOf("certificate"))
        val next = own.copy(versionCode = 6)
        assertTrue(updatePackageMatches(own, next))
        assertFalse(updatePackageMatches(own, own))
        assertFalse(updatePackageMatches(own, next.copy(name = "app.debug")))
        assertFalse(updatePackageMatches(own, next.copy(certificates = setOf("stranger"))))
        assertFalse(updatePackageMatches(own, next.copy(certificates = emptySet())))
    }
}
