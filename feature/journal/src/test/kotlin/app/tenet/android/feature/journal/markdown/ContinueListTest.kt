package app.tenet.android.feature.journal.markdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueListTest {

    /** Simulates pressing Enter at the end of [before]. */
    private fun enter(before: String): TextFieldValue {
        val old = TextFieldValue(before, TextRange(before.length))
        val typed = TextFieldValue(before + "\n", TextRange(before.length + 1))
        return typed.continueList(old)
    }

    @Test
    fun `bullet continues`() {
        val r = enter("Einkauf\n- Milch")
        assertEquals("Einkauf\n- Milch\n- ", r.text)
        assertEquals(r.text.length, r.selection.start)
    }

    @Test
    fun `checklist continues unchecked`() {
        assertEquals("- [x] Hafer\n- [ ] ", enter("- [x] Hafer").text)
    }

    @Test
    fun `numbers increment and keep indent`() {
        assertEquals("  3. drei\n  4. ", enter("  3. drei").text)
        assertEquals("9) neun\n10) ", enter("9) neun").text)
    }

    @Test
    fun `quote continues`() {
        assertEquals("> Zitat\n> ", enter("> Zitat").text)
    }

    @Test
    fun `empty item ends the list`() {
        val r = enter("- Milch\n- ")
        assertEquals("- Milch\n", r.text)
        assertEquals(r.text.length, r.selection.start)
    }

    @Test
    fun `plain lines untouched`() {
        assertEquals("Hallo\n", enter("Hallo").text)
    }

    @Test
    fun `other edits untouched`() {
        val old = TextFieldValue("- a", TextRange(3))
        val typed = TextFieldValue("- ab", TextRange(4))
        assertEquals("- ab", typed.continueList(old).text)
    }
}
