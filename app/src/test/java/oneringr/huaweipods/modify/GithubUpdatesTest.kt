package oneringr.huaweipods.modify

import oneringr.huaweipods.modify.update.GithubUpdates
import org.junit.Assert.*
import org.junit.Test

class GithubUpdatesTest {
    @Test fun comparesNumericReleaseVersions() {
        assertTrue(GithubUpdates.isNewer("v1.10.0", "1.2.0"))
        assertTrue(GithubUpdates.isNewer("2.0", "1.99.99"))
        assertFalse(GithubUpdates.isNewer("v1.2.0", "1.2.0"))
        assertFalse(GithubUpdates.isNewer("1.2", "1.2.0"))
        assertFalse(GithubUpdates.isNewer("1.1.9", "1.2.0"))
    }
}
