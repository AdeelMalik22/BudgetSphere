package com.budgetsphere.app.domain

import java.time.LocalDate
import java.time.YearMonth

data class LedgerEntry(val amount: Long,val income: Boolean,val category: String,val day: Long)
data class FinancialReport(val totals: Totals=Totals(),val categories: Map<String,Long> = emptyMap())
/** Immutable, scope-owned derived state, rebuilt from Room emissions on a background dispatcher. */
class Ledger(private val entries: List<LedgerEntry>) {
    val total: Totals
    private val monthlyExpense=mutableMapOf<YearMonth,Long>()
    private val monthlyCategoryExpense=mutableMapOf<Pair<YearMonth,String>,Long>()
    init {
        var income=0L;var expense=0L
        entries.forEach {entry->
            require(entry.amount>0)
            if(entry.income)income=Math.addExact(income,entry.amount)else{
                expense=Math.addExact(expense,entry.amount)
                val month=YearMonth.from(LocalDate.ofEpochDay(entry.day))
                monthlyExpense[month]=Math.addExact(monthlyExpense[month]?:0,entry.amount)
                val key=month to entry.category
                monthlyCategoryExpense[key]=Math.addExact(monthlyCategoryExpense[key]?:0,entry.amount)
            }
        }
        total=Totals(income,expense)
    }
    fun spending(month: YearMonth,category: String?=null): Long = if(category==null)monthlyExpense[month]?:0 else monthlyCategoryExpense[month to category]?:0
    fun report(range: DateRange): FinancialReport {
        var income=0L;var expense=0L;val categories=mutableMapOf<String,Long>()
        entries.forEach {entry->if(entry.day in range){if(entry.income)income=Math.addExact(income,entry.amount)else{expense=Math.addExact(expense,entry.amount);categories[entry.category]=Math.addExact(categories[entry.category]?:0,entry.amount)}}}
        return FinancialReport(Totals(income,expense),categories)
    }
}
