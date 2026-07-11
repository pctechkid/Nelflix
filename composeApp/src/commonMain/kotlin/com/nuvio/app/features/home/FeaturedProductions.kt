package com.nuvio.app.features.home

import com.nuvio.app.features.tmdb.TmdbEntityKind

data class FeaturedProductionEntity(
    val id: Int,
    val name: String,
    val kind: TmdbEntityKind,
    val sourceType: String,
) {
    val key: String = "${kind.routeValue}:$id"
}

val featuredProductionEntities: List<FeaturedProductionEntity> = listOf(
    FeaturedProductionEntity(213, "Netflix", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(49, "HBO", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(2739, "Disney+", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(1024, "Prime Video", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(2552, "Apple TV+", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(453, "Hulu", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(3186, "HBO Max", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(3353, "Peacock", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(174, "AMC", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(88, "FX", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(71, "The CW", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(2, "ABC", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(6, "NBC", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(16, "CBS", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(19, "FOX", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(4, "BBC One", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(67, "Showtime", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(318, "Starz", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(54, "Disney Channel", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(56, "Cartoon Network", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(13, "Nickelodeon", TmdbEntityKind.NETWORK, "tv"),
    FeaturedProductionEntity(2, "Walt Disney Pictures", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(174, "Warner Bros. Pictures", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(33, "Universal Pictures", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(4, "Paramount Pictures", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(5, "Columbia Pictures", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(8411, "Metro-Goldwyn-Mayer", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(25, "20th Century Studios", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(34, "Sony Pictures", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(14, "Miramax", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(420, "Marvel Studios", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(1, "Lucasfilm", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(3, "Pixar", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(521, "DreamWorks Animation", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(41077, "A24", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(1632, "Lionsgate", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(3172, "Blumhouse Productions", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(12, "New Line Cinema", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(923, "Legendary Pictures", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(79, "Village Roadshow Pictures", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(508, "Regency Enterprises", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(10342, "Studio Ghibli", TmdbEntityKind.COMPANY, "movie"),
    FeaturedProductionEntity(882, "Toho", TmdbEntityKind.COMPANY, "movie"),
)
