package com.budgetsphere.app
import com.budgetsphere.app.domain.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.YearMonth
class LedgerTest {
    @Test fun summariesBudgetActualAndDateReportsShareExactRules() {
        val october=LocalDate.of(2026,10,5).toEpochDay();val september=LocalDate.of(2026,9,30).toEpochDay()
        val ledger=Ledger(listOf(LedgerEntry(10000,true,"salary",october),LedgerEntry(2500,false,"food",october),LedgerEntry(1000,false,"bus",october),LedgerEntry(500,false,"food",september)))
        assertEquals(6000L,ledger.total.balance)
        assertEquals(3500L,ledger.spending(YearMonth.of(2026,10)))
        assertEquals(2500L,ledger.spending(YearMonth.of(2026,10),"food"))
        val report=ledger.report(DateRange(october,october));assertEquals(10000L,report.totals.income);assertEquals(3500L,report.totals.expense);assertEquals(2500L,report.categories["food"])
        assertEquals(0L,Ledger(emptyList()).spending(YearMonth.of(2026,10)))
        val changed=Ledger(listOf(LedgerEntry(123,false,"food",october)))
        assertEquals(123L,changed.spending(YearMonth.of(2026,10)));assertEquals(3500L,ledger.spending(YearMonth.of(2026,10)))
    }
}
