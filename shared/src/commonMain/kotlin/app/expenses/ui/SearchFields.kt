package app.expenses.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import app.expenses.SearchKind
import app.expenses.SearchRange

/**
 * Everything the person has typed or chosen on the Search page that is not the results themselves: the words, the amounts, the filters, the entries ticked, whether the Filters panel is open and
 * where the list is scrolled to. It lives outside [SearchScreen] (in the app root) so that it outlasts the screen: when the window changes size under an open entry, the page is taken down and
 * built again (the entry goes beside the results, or over them), and comes back as it was.
 */
class SearchFields(initialAccountId: String? = null, initialTag: String? = null, initialFromDay: Long? = null, initialToDay: Long? = null) {
    val text = mutableStateOf("")
    val min = mutableStateOf("")
    val max = mutableStateOf("")
    val categoryId = mutableStateOf<String?>(null)
    val kind = mutableStateOf(SearchKind.ANY)
    val range = mutableStateOf(SearchRange.ANY)
    val tag = mutableStateOf(initialTag)
    val fromDay = mutableStateOf(initialFromDay)
    val toDay = mutableStateOf(initialToDay)
    val accountId = mutableStateOf(initialAccountId)
    val selected = mutableStateOf(listOf<String>())
    val filtersOpen = mutableStateOf(false)
    var listState = LazyListState()
        private set

    companion object {
        val Saver = listSaver<SearchFields, Any?>(
            save = { f ->
                listOf(
                    f.text.value, f.min.value, f.max.value, f.categoryId.value, f.kind.value.name, f.range.value.name, f.tag.value, f.fromDay.value, f.toDay.value, f.accountId.value,
                    ArrayList(f.selected.value), f.filtersOpen.value, f.listState.firstVisibleItemIndex, f.listState.firstVisibleItemScrollOffset,
                )
            },
            restore = { l ->
                SearchFields().also { f ->
                    f.text.value = l[0] as String; f.min.value = l[1] as String; f.max.value = l[2] as String; f.categoryId.value = l[3] as String?
                    f.kind.value = SearchKind.valueOf(l[4] as String); f.range.value = SearchRange.valueOf(l[5] as String); f.tag.value = l[6] as String?
                    f.fromDay.value = l[7] as Long?; f.toDay.value = l[8] as Long?; f.accountId.value = l[9] as String?
                    @Suppress("UNCHECKED_CAST") run { f.selected.value = (l[10] as List<String>).toList() }
                    f.filtersOpen.value = l[11] as Boolean
                    f.listState = LazyListState(l[12] as Int, l[13] as Int)
                }
            },
        )
    }
}
