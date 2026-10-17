package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimilarEntriesTest {
    private fun c(id: String, label: String, cat: String, at: Long = 1) = SimilarCandidate(id, label, cat, 100, at)

    @Test fun theSamePayeeWrittenDifferentlyMatchesAndAShortNameInsideALongOneDoesNot() {
        assertTrue(SimilarEntries.matches("iCloud", "ICLOUD"))
        assertTrue(SimilarEntries.matches("iCloud", "Apple iCloud+ 50GB"), "a name held inside a longer one")
        assertTrue(SimilarEntries.matches("swiggy@icici", "Swiggy"))
        assertFalse(SimilarEntries.matches("Ola", "Coca Cola Stall"), "three letters is too weak to be found inside something")
        assertFalse(SimilarEntries.matches("Netflix", "Hotstar"))
        assertFalse(SimilarEntries.matches("", "iCloud"))
        assertFalse(SimilarEntries.matches("ab", "ab"), "two characters say nothing")
    }

    @Test fun onlyEntriesNotAlreadyInTheCategoryAreOfferedNewestFirstAndNeverTheOneJustFiled() {
        val all = listOf(c("a", "iCloud", "other", 1), c("b", "iCloud", "subs", 2), c("c", "icloud storage", "other", 3), c("self", "iCloud", "other", 9), c("d", "Netflix", "other", 4))
        assertEquals(listOf("c", "a"), SimilarEntries.similar("iCloud", "subs", "self", all).map { it.id })
    }

    @Test fun anEntryWithNoSingleCategoryIsNeverOffered() {
        assertEquals(emptyList(), SimilarEntries.similar("iCloud", "subs", null, listOf(c("split", "iCloud", ""))))
    }

    @Test fun theTopCategoryIsTheCommonestAndATieGoesToTheNewer() {
        val all = listOf(c("1", "Netflix", "fun", 1), c("2", "Netflix", "fun", 2), c("3", "Netflix", "subs", 3), c("4", "Hotstar", "subs", 4))
        assertEquals("fun", SimilarEntries.topCategory("netflix", all))
        assertEquals("subs", SimilarEntries.topCategory("netflix", all + c("5", "NETFLIX", "subs", 5)), "now two each, and the newest is in subs")
        assertNull(SimilarEntries.topCategory("Spotify", all))
    }

    @Test fun searchWordsDropShortWordsAndAreCapped() {
        assertEquals(listOf("apple", "icloud", "50gb"), SimilarEntries.searchWords("Apple iCloud+ 50GB a"))
        assertEquals(4, SimilarEntries.searchWords("alpha bravo charlie delta echo foxtrot").size)
        assertEquals(listOf("swiggy"), SimilarEntries.searchWords("swiggy@icici"))
    }

    @Test fun anEarlierEntryOfThePayeeBecomesTheHistorySuggestionBelowRulesAndWhatWasChosenLastTime() {
        val usable = setOf("fun", "subs", "food")
        assertEquals(CategorySuggestion("subs", SuggestionSource.HISTORY), CategorySuggest.suggest("Netflix", "x", emptyList(), emptyMap(), usable, history = "subs"))
        assertEquals(CategorySuggestion("fun", SuggestionSource.LEARNED), CategorySuggest.suggest("Netflix", "x", emptyList(), mapOf("netflix" to "fun"), usable, history = "subs"))
        assertNull(CategorySuggest.suggest("Netflix", "x", emptyList(), emptyMap(), usable, history = "deleted"), "a category that is gone is never suggested")
    }
}
