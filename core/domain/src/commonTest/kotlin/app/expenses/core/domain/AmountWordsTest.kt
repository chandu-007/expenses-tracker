package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountWordsTest {
    private fun r(rupees: Long) = AmountWords.of(rupees * 100)

    @Test fun zero() = assertEquals("zero rupees", AmountWords.of(0))
    @Test fun one() = assertEquals("one rupees", r(1))
    @Test fun twentyOne() = assertEquals("twenty-one rupees", r(21))
    @Test fun hundred() = assertEquals("one hundred rupees", r(100))
    @Test fun hundredAndOne() = assertEquals("one hundred one rupees", r(101))
    @Test fun nineninenine() = assertEquals("nine hundred ninety-nine rupees", r(999))
    @Test fun thousand() = assertEquals("one thousand rupees", r(1000))
    @Test fun twelveThousand() = assertEquals("twelve thousand three hundred forty-five rupees", r(12_345))
    @Test fun lakh() = assertEquals("one lakh rupees", r(100_000))
    @Test fun lakhs() = assertEquals("one lakh twenty-four thousand five hundred rupees", r(124_500))
    @Test fun tenLakh() = assertEquals("ten lakh rupees", r(1_000_000))
    @Test fun crore() = assertEquals("one crore rupees", r(10_000_000))
    @Test fun arab() = assertEquals("one arab rupees", r(1_000_000_000))
    @Test fun mixedCrore() = assertEquals("twelve crore thirty-four lakh fifty-six thousand seven hundred eighty-nine rupees", r(123_456_789))
    @Test fun paise() = assertEquals("one thousand rupees and fifty paise", AmountWords.of(100_050))
    @Test fun fivepaise() = assertEquals("one thousand two rupees and five paise", AmountWords.of(100_205))
    @Test fun paiseOnly() = assertEquals("fifty paise", AmountWords.of(50))
    @Test fun negativeIsAbsolute() = assertEquals(r(124_500), AmountWords.of(-12_450_000))
    @Test fun customNames() = assertEquals("two dollars and three cents", AmountWords.of(203, "dollars", "cents"))
}
