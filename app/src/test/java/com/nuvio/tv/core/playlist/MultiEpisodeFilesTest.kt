package com.nuvio.tv.core.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiEpisodeFilesTest {

    @Test
    fun `range names cover the later episodes`() {
        assertEquals(2..2, extraEpisodesInFile("Star.Trek.Voyager.S01E01-E02.Caretaker.1080p.mkv", 1, 1))
        assertEquals(2..2, extraEpisodesInFile("Star Trek Voyager - S01E01E02 - Caretaker.mkv", 1, 1))
        assertEquals(16..16, extraEpisodesInFile("star.trek.voyager.s05e15-16.dark.frontier.mkv", 5, 15))
        assertEquals(26..26, extraEpisodesInFile("Voyager 7x25-26 Endgame.avi", 7, 25))
        assertEquals(2..3, extraEpisodesInFile("Show.S02E01E02E03.mkv", 2, 1))
    }

    @Test
    fun `single episodes and unrelated numbers cover nothing else`() {
        assertEquals(IntRange.EMPTY, extraEpisodesInFile("Star.Trek.Voyager.S01E01.Caretaker.Part.1.mkv", 1, 1))
        assertEquals(IntRange.EMPTY, extraEpisodesInFile("Star.Trek.Voyager.S01E01-720p.mkv", 1, 1))
        assertEquals(IntRange.EMPTY, extraEpisodesInFile("Star Trek Voyager S01E01 - 1080p WEB.mkv", 1, 1))
        assertEquals(IntRange.EMPTY, extraEpisodesInFile("Voyager.1920x1080.mkv", 19, 20))
        assertEquals(IntRange.EMPTY, extraEpisodesInFile(null, 1, 1))
    }

    @Test
    fun `ranges must start at the playing episode`() {
        assertEquals(IntRange.EMPTY, extraEpisodesInFile("Show.S01E01-E02.mkv", 1, 2))
        assertEquals(IntRange.EMPTY, extraEpisodesInFile("Show.S01E01-E02.mkv", 2, 1))
        assertEquals(IntRange.EMPTY, extraEpisodesInFile("Show.S01E01-E26.Complete.mkv", 1, 1))
    }
}
