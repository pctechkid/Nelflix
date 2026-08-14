package com.nuvio.app.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountStatisticsRepositoryTest {
    @Test
    fun `cached statistics round trip and clamp malformed negative counts`() {
        val encoded = AccountStatisticsCodec.encode(
            AccountStatistics(progress = 1_501L, library = 23L, watched = 987L),
        )

        assertEquals(
            AccountStatistics(progress = 1_501L, library = 23L, watched = 987L),
            AccountStatisticsCodec.decode(encoded),
        )
        assertEquals(
            AccountStatistics(),
            AccountStatisticsCodec.decode("""{"progress":-1,"library":-2,"watched":-3}"""),
        )
        assertNull(AccountStatisticsCodec.decode("not-json"))
    }

    @Test
    fun `server statistics win while offline fallback is used before first refresh`() {
        val fallback = AccountStatistics(progress = 2L, library = 3L, watched = 4L)
        val server = AccountStatistics(progress = 20L, library = 30L, watched = 40L)

        assertEquals(fallback, accountStatisticsForDisplay(null, fallback))
        assertEquals(server, accountStatisticsForDisplay(server, fallback))
    }

    @Test
    fun `late response from another profile or account is rejected`() {
        assertTrue(
            accountStatisticsResponseMatches(
                activeUserId = "user-a",
                activeProfileId = 2,
                isAnonymous = false,
                responseUserId = "user-a",
                responseProfileId = 2,
            ),
        )
        assertFalse(
            accountStatisticsResponseMatches(
                activeUserId = "user-a",
                activeProfileId = 2,
                isAnonymous = false,
                responseUserId = "user-a",
                responseProfileId = 1,
            ),
        )
        assertFalse(
            accountStatisticsResponseMatches(
                activeUserId = "user-b",
                activeProfileId = 2,
                isAnonymous = false,
                responseUserId = "user-a",
                responseProfileId = 2,
            ),
        )
        assertFalse(
            accountStatisticsResponseMatches(
                activeUserId = "user-a",
                activeProfileId = 2,
                isAnonymous = true,
                responseUserId = "user-a",
                responseProfileId = 2,
            ),
        )
    }
}
