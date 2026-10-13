package com.xiaoai.assistant.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterTest {
    @Test
    fun naturalMusicRequests() {
        for (text in listOf("想听周杰伦的晴天", "帮我播放一首周杰伦的晴天", "给我播放一首周杰伦的晴天")) {
            val command = CommandRouter.parse(text) as UserCommand.Play
            assertEquals("晴天 周杰伦", command.query.searchText)
        }
    }
    @Test
    fun wakeAndPlay() {
        val command = CommandRouter.parse("小爱同学，播放晴天。")
        assertTrue(command is UserCommand.Play)
        command as UserCommand.Play
        assertEquals("晴天", command.query.spokenTitle)
        assertEquals("晴天", command.query.searchText)
    }

    @Test
    fun artistTitle() {
        val command = CommandRouter.parse("我想放一首周杰伦的晴天")
        assertTrue(command is UserCommand.Play)
        command as UserCommand.Play
        assertEquals("晴天", command.query.spokenTitle)
        assertEquals("晴天 周杰伦", command.query.searchText)
    }

    @Test
    fun doNotSplitShortDe() {
        val command = CommandRouter.parse("播放爱的供养")
        assertTrue(command is UserCommand.Play)
        command as UserCommand.Play
        assertEquals("爱的供养", command.query.searchText)
    }

    @Test
    fun artistSongs() {
        val command = CommandRouter.parse("我想听周杰伦的歌")
        assertTrue(command is UserCommand.Play)
        command as UserCommand.Play
        assertEquals("周杰伦", command.query.searchText)
    }

    @Test
    fun greetStripsParticle() {
        assertTrue(CommandRouter.parse("你好呀") is UserCommand.Say)
    }

    @Test
    fun pause() {
        assertTrue(CommandRouter.parse("暂停播放") is UserCommand.PauseMusic)
    }

    @Test
    fun chatKeepsSentence() {
        assertTrue(CommandRouter.parse("今天心情怎么样") is UserCommand.Chat)
    }

    @Test
    fun wakeOnly() {
        assertEquals("", CommandRouter.extractAfterWake("小爱同学"))
        assertNull(CommandRouter.extractAfterWake("播放晴天"))
        assertTrue(CommandRouter.parse("小爱同学") is UserCommand.Empty)
    }
}
