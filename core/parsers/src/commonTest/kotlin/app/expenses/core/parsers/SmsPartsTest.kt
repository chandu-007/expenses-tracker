package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals

class SmsPartsTest {
    private val a = SmsPart("Rs.100.00 debited from A/c XX1234 on 05-10-26 to RAVI via UPI. Ref 123456789012", 1_000)
    private val b = SmsPart("Rs.100.00 debited from A/c XX1234 on 05-10-26 to ANIL via UPI. Ref 123456789099", 2_000)

    @Test
    fun twoAlertsDeliveredTogetherStayTwoMessages() {
        assertEquals(listOf(a, b), SmsParts.messages(listOf(a, b)))
    }

    @Test
    fun aTailThatIsNotATransactionIsJoinedOntoItsHead() {
        val head = SmsPart("Rs.100.00 debited from A/c XX1234 on 05-10-26 to RAVI via UPI. Ref 12345", 1_000)
        val tail = SmsPart("6789012. Not you? Call 1800 123 456 to block", 1_000)
        assertEquals(listOf(SmsPart(head.body + tail.body, 1_000)), SmsParts.messages(listOf(head, tail)))
    }

    @Test
    fun aSingleMessageAndAnEmptyDeliveryAreLeftAlone() {
        assertEquals(listOf(a), SmsParts.messages(listOf(a)))
        assertEquals(emptyList(), SmsParts.messages(emptyList()))
    }

    @Test
    fun aTailBetweenTwoAlertsGoesToTheOneBeforeIt() {
        val tail = SmsPart(" Avl bal Rs 5,000. Not you? Call 1800 123 456", 1_500)
        assertEquals(listOf(SmsPart(a.body + tail.body, 1_000), b), SmsParts.messages(listOf(a, tail, b)))
    }

    @Test
    fun aTailAfterTheSecondAlertGoesToTheSecond() {
        val tail = SmsPart(" Avl bal Rs 5,000. Not you? Call 1800 123 456", 2_500)
        assertEquals(listOf(a, SmsPart(b.body + tail.body, 2_000)), SmsParts.messages(listOf(a, b, tail)))
    }
}
