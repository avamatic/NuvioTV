package com.nuvio.tv.data.mapper

import com.nuvio.tv.data.remote.dto.MetaResponseDto
import com.nuvio.tv.domain.model.VideoPlaybackIdentity
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoPlaybackIdentityMapperTest {

    private val adapter = Moshi.Builder().build().adapter(MetaResponseDto::class.java)

    @Test
    fun `episode identity is mapped with real coordinates`() {
        val identity = parseIdentity("""{"type":"series","id":"tt0092455","name":"Star Trek: The Next Generation","season":3,"episode":15}""")

        assertEquals(
            VideoPlaybackIdentity(type = "series", id = "tt0092455", name = "Star Trek: The Next Generation", season = 3, episode = 15),
            identity
        )
        assertEquals("tt0092455:3:15", identity?.videoId)
    }

    @Test
    fun `movie identity drops episode coordinates`() {
        val identity = parseIdentity("""{"type":"movie","id":"tt0079945","season":1,"episode":1}""")

        assertEquals(VideoPlaybackIdentity(type = "movie", id = "tt0079945"), identity)
        assertEquals("tt0079945", identity?.videoId)
    }

    @Test
    fun `invalid identities are ignored`() {
        assertNull(parseIdentity("""{"type":"series","id":"tt0092455"}"""))
        assertNull(parseIdentity("""{"type":"series","id":"chronio:star-trek","season":1,"episode":1}"""))
        assertNull(parseIdentity("""{"type":"channel","id":"tt0092455"}"""))
    }

    @Test
    fun `videos without identity are unchanged`() {
        val meta = parse("""{"meta":{"id":"tt0092455","type":"series","name":"Show","videos":[{"id":"tt0092455:1:1","season":1,"episode":1}]}}""")

        assertNull(meta.videos.single().playbackIdentity)
    }

    private fun parseIdentity(identityJson: String): VideoPlaybackIdentity? =
        parse("""{"meta":{"id":"chronio:star-trek","type":"series","name":"Star Trek","videos":[{"id":"chronio:star-trek:x:1:1","season":1,"episode":1,"playbackIdentity":$identityJson}]}}""")
            .videos.single().playbackIdentity

    private fun parse(json: String) = requireNotNull(adapter.fromJson(json)?.meta).toDomain()
}
