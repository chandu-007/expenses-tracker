package app.expenses.core.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** UI S3: the glyph a person chose for a category and the "Show spending in red" switch travel in a backup. */
class CategoryLookBackupTest {
    private fun glyphOf(d: Device, id: String) = d.db.schemaQueries.settingValue("cat_glyph:$id").executeAsOneOrNull()

    @Test
    fun aChosenGlyphAndTheRedSwitchTravelAndComeBackOnlyForACategoryThatIsThereAndOnlyAGlyphTheAppHas() {
        val a = Device("a"); val b = Device("b")
        val pets = a.categories.create("Pets")
        a.templates.putSetting("cat_glyph:${pets.id}", "pets")
        a.templates.putSetting("cat_glyph:Food", "cafe")
        a.templates.putSetting("show_spending_red", "1")
        a.templates.putSetting("cat_glyph:gone-category", "bag")
        val data = a.backup.export()
        assertEquals("pets", data.settings["cat_glyph:${pets.id}"]); assertEquals("1", data.settings["show_spending_red"])
        b.backup.restore(data)
        assertEquals("1", b.db.schemaQueries.settingValue("show_spending_red").executeAsOneOrNull())
        assertEquals("cafe", glyphOf(b, "Food"), "Food is a starter, the same id on every phone")
        assertNull(glyphOf(b, "gone-category"), "a category that does not exist here keeps no glyph")
    }

    @Test
    fun aGlyphThisAppDoesNotKnowAndAGlyphAlreadyChosenOnThisPhoneAreNotRestored() {
        val a = Device("a"); val b = Device("b")
        a.templates.putSetting("cat_glyph:Food", "no-such-glyph"); a.templates.putSetting("cat_glyph:Fun", "movie")
        b.templates.putSetting("cat_glyph:Fun", "games")
        b.backup.restore(a.backup.export())
        assertNull(glyphOf(b, "Food"))
        assertEquals("games", glyphOf(b, "Fun"), "what was chosen here stays")
    }
}
