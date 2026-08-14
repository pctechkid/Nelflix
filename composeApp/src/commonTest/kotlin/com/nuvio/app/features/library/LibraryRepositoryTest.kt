package com.nuvio.app.features.library

import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.trakt.TraktListTab
import com.nuvio.app.features.trakt.TraktListType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryRepositoryTest {

    @Test
    fun `display title uses exact type formatting`() {
        assertEquals("Movie", "movie".toLibraryDisplayTitle())
        assertEquals("Anime Series", "anime-series".toLibraryDisplayTitle())
        assertEquals("Tv", "tv".toLibraryDisplayTitle())
        assertEquals("Other", "".toLibraryDisplayTitle())
    }

    @Test
    fun `meta preview mapping preserves exact type and poster shape`() {
        val item = LibraryItem(
            id = "tt1",
            type = "anime-series",
            name = "Title",
            poster = "poster",
            banner = "banner",
            logo = "logo",
            description = "desc",
            releaseInfo = "2024",
            imdbRating = "8.4",
            genres = listOf("Drama"),
            posterShape = PosterShape.Poster,
            savedAtEpochMs = 1L,
        )

        val preview = item.toMetaPreview()

        assertEquals("anime-series", preview.type)
        assertEquals(PosterShape.Poster, preview.posterShape)
        assertEquals("banner", preview.banner)
    }

    @Test
    fun `library tabs include local Nuvio library before Trakt tabs`() {
        val traktTab = TraktListTab(
            key = "trakt:watchlist",
            title = "Watchlist",
            type = TraktListType.WATCHLIST,
        )

        val tabs = libraryTabsWithLocal(listOf(traktTab))

        assertEquals(listOf("local", "trakt:watchlist"), tabs.map { it.key })
        assertEquals("NELFLIX Library", tabs.first().title)
    }

    @Test
    fun `library membership always includes local state before Trakt membership`() {
        val membership = libraryMembershipWithLocal(
            inLocal = true,
            traktMembership = mapOf("trakt:watchlist" to false),
        )

        assertEquals(
            mapOf(
                "local" to true,
                "trakt:watchlist" to false,
            ),
            membership,
        )
    }

    @Test
    fun `remote pull applies only to the active unchanged profile without pending writes`() {
        assertTrue(
            shouldApplyLibraryRemoteSnapshot(
                requestedProfileId = 2,
                currentProfileId = 2,
                mutationVersionAtStart = 10L,
                currentMutationVersion = 10L,
                hasPendingLocalPush = false,
            ),
        )
        assertFalse(
            shouldApplyLibraryRemoteSnapshot(
                requestedProfileId = 2,
                currentProfileId = 2,
                mutationVersionAtStart = 10L,
                currentMutationVersion = 11L,
                hasPendingLocalPush = false,
            ),
        )
        assertFalse(
            shouldApplyLibraryRemoteSnapshot(
                requestedProfileId = 2,
                currentProfileId = 2,
                mutationVersionAtStart = 10L,
                currentMutationVersion = 10L,
                hasPendingLocalPush = true,
            ),
        )
    }

    @Test
    fun `unchanged remote snapshot does not publish duplicate state`() {
        val item = LibraryItem(
            id = "tt1",
            type = "movie",
            name = "Movie",
            savedAtEpochMs = 1L,
        )

        assertFalse(librarySnapshotChanged(mapOf(item.id to item), mapOf(item.id to item)))
        assertTrue(
            librarySnapshotChanged(
                current = mapOf(item.id to item),
                remote = emptyMap(),
            ),
        )
    }

    @Test
    fun `visible refresh backs off after failures and resets after success`() {
        val firstFailure = nextLibraryVisibleRefreshDelayMs(
            previousDelayMs = LIBRARY_VISIBLE_REFRESH_INTERVAL_MS,
            succeeded = false,
        )
        val secondFailure = nextLibraryVisibleRefreshDelayMs(
            previousDelayMs = firstFailure,
            succeeded = false,
        )

        assertEquals(60_000L, firstFailure)
        assertEquals(120_000L, secondFailure)
        assertEquals(
            LIBRARY_VISIBLE_REFRESH_INTERVAL_MS,
            nextLibraryVisibleRefreshDelayMs(previousDelayMs = secondFailure, succeeded = true),
        )
    }
}
