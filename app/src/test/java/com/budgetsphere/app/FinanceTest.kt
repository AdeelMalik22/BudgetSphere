package com.budgetsphere.app
import com.budgetsphere.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class FinanceTest {
    @Test fun moneyUsesCurrencyPrecision() {
        assertEquals(12345L,Money.parse("123.45","USD"))
        assertEquals(123L,Money.parse("123","JPY"))
        assertEquals(1234L,Money.parse("1.234","KWD"))
        assertThrows(IllegalArgumentException::class.java) { Money.parse("1.001","USD") }
        assertThrows(IllegalArgumentException::class.java) { Money.parse("-1","USD") }
        assertThrows(IllegalArgumentException::class.java) { Money.parse("0","USD") }
        assertThrows(IllegalArgumentException::class.java) { Money.parse("999999999999999999999","USD") }
    }
    @Test fun checkedArithmeticAndBalance() {
        assertEquals(300L, Totals(500,200).balance)
        assertEquals(-100L, remaining(100,200))
        assertThrows(ArithmeticException::class.java) { Money.sum(listOf(Long.MAX_VALUE,1)) }
    }
    @Test fun rangesHandleLeapYearsAndWeekPreferences() {
        assertEquals(LocalDate.of(2024,2,29).toEpochDay(),DateRange.month(LocalDate.of(2024,2,1)).end)
        val sunday = LocalDate.of(2026,10,25)
        assertEquals(sunday.toEpochDay(),DateRange.named("Week",false,sunday).start)
        assertEquals(sunday.minusDays(6).toEpochDay(),DateRange.named("Week",true,sunday).start)
        assertThrows(IllegalArgumentException::class.java) { DateRange(2,1) }
    }
    @Test fun inputValidation() {
        assertEquals("user@example.com",Validation.identifier(" User@Example.com "))
        assertThrows(IllegalArgumentException::class.java) { Validation.name("  ") }
        assertThrows(IllegalArgumentException::class.java) { Validation.password("short") }
    }
}
