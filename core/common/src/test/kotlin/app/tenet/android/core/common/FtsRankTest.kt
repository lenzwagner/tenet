package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FtsRankTest {
    @Test
    fun matchExpression_prefixesWords_dropsSyntax() {
        assertEquals("lauf* morgen*", FtsRank.matchExpression("Lauf \"morgen\" -"))
        assertEquals("über*", FtsRank.matchExpression("über"))
        assertNull(FtsRank.matchExpression("  *- "))
    }

    @Test
    fun score_titleHitOutweighsBodyHit() {
        // p=1, c=2, n=10; title: 1 hit (in 2 docs), body: 0
        val titleHit = intArrayOf(1, 2, 10, 1, 2, 2, 0, 5, 3)
        // title: 0, body: 1 hit
        val bodyHit = intArrayOf(1, 2, 10, 0, 2, 2, 1, 5, 3)
        val weights = doubleArrayOf(3.0, 1.0)
        assertTrue(FtsRank.score(titleHit, weights) > FtsRank.score(bodyHit, weights))
    }

    @Test
    fun score_rareTermsScoreHigher() {
        val rare = intArrayOf(1, 1, 100, 1, 1, 1)
        val common = intArrayOf(1, 1, 100, 1, 90, 90)
        val w = doubleArrayOf(1.0)
        assertTrue(FtsRank.score(rare, w) > FtsRank.score(common, w))
    }

    @Test
    fun decode_littleEndian() {
        val blob = byteArrayOf(1, 0, 0, 0, 2, 1, 0, 0)
        assertEquals(listOf(1, 258), FtsRank.decode(blob).toList())
    }
}
