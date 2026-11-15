package dev.anaekran

import dev.anaekran.MenuAction.FAV_ADD
import dev.anaekran.MenuAction.FAV_REMOVE
import dev.anaekran.MenuAction.HIDE
import dev.anaekran.MenuAction.INFO
import dev.anaekran.MenuAction.MOVE_LEFT
import dev.anaekran.MenuAction.MOVE_RIGHT
import dev.anaekran.MenuAction.OPEN
import dev.anaekran.MenuAction.UNINSTALL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MenuTest {
    @Test fun moveFavoriteLeft() = assertEquals(listOf("b", "a", "c"), moveFavorite(listOf("a", "b", "c"), "b", -1))
    @Test fun moveFavoriteRight() = assertEquals(listOf("a", "c", "b"), moveFavorite(listOf("a", "b", "c"), "b", 1))
    @Test fun moveFavoriteAtEdgeUnchanged() {
        assertEquals(listOf("a", "b"), moveFavorite(listOf("a", "b"), "a", -1))
        assertEquals(listOf("a", "b"), moveFavorite(listOf("a", "b"), "b", 1))
        assertEquals(listOf("a", "b"), moveFavorite(listOf("a", "b"), "zz", 1))
    }

    @Test fun menuForNonFavoriteSystemApp() =
        assertEquals(listOf(OPEN, FAV_ADD, HIDE, INFO), menuActions(-1, 4, removable = false))

    @Test fun menuForMiddleFavoriteRemovable() =
        assertEquals(listOf(OPEN, FAV_REMOVE, MOVE_LEFT, MOVE_RIGHT, HIDE, INFO, UNINSTALL), menuActions(1, 4, removable = true))

    @Test fun menuForFirstFavoriteHasNoMoveLeft() {
        val m = menuActions(0, 4, removable = false)
        assertFalse(MOVE_LEFT in m)
        assertEquals(listOf(OPEN, FAV_REMOVE, MOVE_RIGHT, HIDE, INFO), m)
    }

    @Test fun menuForLastFavoriteHasNoMoveRight() = assertFalse(MOVE_RIGHT in menuActions(3, 4, removable = false))
}
