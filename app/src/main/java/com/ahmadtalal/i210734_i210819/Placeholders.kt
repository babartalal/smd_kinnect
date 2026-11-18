package com.ahmadtalal.i210734_i210819

/**
 * Sample stories and posts shown after the real ones on the home feed, so
 * both lists always have enough content to scroll (as in Kinnect_UI.pdf).
 * They exist only on screen: their ids start with [PREFIX], nothing is sent
 * to the server for them, and their pictures are drawables bundled in the app
 * (an image path of "drawable:<name>", see ImageView.loadRemote).
 */
object Placeholders {

    const val PREFIX = "sample-"

    fun isSample(id: String): Boolean = id.startsWith(PREFIX)

    private fun person(id: String, name: String) = User(userId = PREFIX + id, name = name)

    fun stories(): List<StoryModel> {
        val now = System.currentTimeMillis()
        val samples = listOf(
            Triple("Omar Farooq", "drawable:feed_landscape_night", "Karachi nights"),
            Triple("Sara Iqbal", "drawable:hub_landscape_rose", "Sunday brunch"),
            Triple("Hamza Ali", "drawable:hub_landscape_green", "Morning hike"),
            Triple("Aisha Khan", "drawable:hub_landscape_blue", "By the sea"),
            Triple("Zain Raza", "drawable:hub_landscape_sand", "Road trip"),
            Triple("Maya Chen", "drawable:people_scene_tile_lilac", "Studio day")
        )
        return samples.mapIndexed { index, (name, image, label) ->
            val author = person("user-s$index", name)
            StoryModel(
                story = Story(
                    storyId = "${PREFIX}story-$index",
                    userId = author.userId,
                    timestamp = now - (index + 1) * HOUR,
                    image = image,
                    textLabel = label
                ),
                author = author
            )
        }
    }

    fun posts(): List<Post> {
        val now = System.currentTimeMillis()
        return listOf(
            Post(
                postId = "${PREFIX}post-1",
                author = person("user-p1", "Lina Marsh"),
                text = "Sunset walk with the crew. Worth every grain of sand in my shoes.",
                timestamp = now - 2 * HOUR,
                images = listOf(
                    "drawable:hub_landscape_sand", "drawable:hub_landscape_blue", "drawable:people_scene_tile_lilac",
                    "drawable:hub_landscape_green", "drawable:hub_landscape_rose", "drawable:feed_landscape_night",
                    "drawable:people_scene_tile_blue"
                ),
                reactionCount = 1200
            ),
            Post(
                postId = "${PREFIX}post-2",
                author = person("user-p2", "Omar Farooq"),
                text = "Night shoot at the old pier. The city never looked this quiet.",
                timestamp = now - 5 * HOUR,
                images = listOf("drawable:feed_landscape_night"),
                reactionCount = 342
            ),
            Post(
                postId = "${PREFIX}post-3",
                author = person("user-p3", "Aisha Khan"),
                text = "Weekend plan: coffee, sketching, and absolutely no email.",
                timestamp = now - 9 * HOUR,
                reactionCount = 87
            ),
            Post(
                postId = "${PREFIX}post-4",
                author = person("user-p4", "Hamza Ali"),
                text = "Same spot, one year apart.",
                timestamp = now - 26 * HOUR,
                images = listOf("drawable:hub_landscape_green", "drawable:people_scene_tile_blue"),
                reactionCount = 56
            ),
            Post(
                postId = "${PREFIX}post-5",
                author = person("user-p5", "Sara Iqbal"),
                text = "New desk setup is finally done. Solid wood, zero cable mess.",
                timestamp = now - 50 * HOUR,
                images = listOf(
                    "drawable:hub_landscape_rose", "drawable:hub_landscape_sand", "drawable:people_scene_tile_night"
                ),
                reactionCount = 19
            )
        )
    }

    private const val HOUR = 60L * 60 * 1000
}
