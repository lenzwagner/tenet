package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CaliProgressionTest {
    @Test fun firstTime() = assertEquals(CaliProgression.Kind.FIRST, CaliProgression.next(8, 3, emptyList(), false).kind)

    @Test fun missedSetRepeats() {
        val s = CaliProgression.next(8, 3, listOf(8, 8, 6), false)
        assertEquals(CaliProgression.Kind.REPEAT, s.kind)
        assertEquals(8, s.target)
    }

    @Test fun allSetsAddRep() = assertEquals(9, CaliProgression.next(8, 3, listOf(9, 8, 8), false).target)

    @Test fun holdAddsFiveSeconds() = assertEquals(25, CaliProgression.next(20, 3, listOf(20, 22, 21), true).target)

    @Test fun capMeansHarderVariation() =
        assertEquals(CaliProgression.Kind.HARDER, CaliProgression.next(15, 3, listOf(15, 15, 16), false).kind)
}
