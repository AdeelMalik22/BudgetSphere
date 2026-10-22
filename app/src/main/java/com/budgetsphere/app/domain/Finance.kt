package com.budgetsphere.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

object Money {
    fun parse(input: String, currency: String): Long {
        val scale = Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
        val amount = input.trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a valid amount")
        require(amount > BigDecimal.ZERO) { "Amount must be greater than zero" }
        return try { amount.setScale(scale, RoundingMode.UNNECESSARY).movePointRight(scale).longValueExact().also {
            require(it <= 100_000_000_000_000L) { "Amount is too large" }
        } } catch (_: ArithmeticException) { throw IllegalArgumentException("Use at most $scale decimal places and a smaller amount") }
    }
    fun decimal(minor: Long, currency: String): String = BigDecimal.valueOf(minor, Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)).toPlainString()
    fun format(minor: Long, currency: String): String = NumberFormat.getCurrencyInstance().apply { this.currency = Currency.getInstance(currency) }.format(BigDecimal(decimal(minor, currency)))
    fun sum(values: List<Long>): Long = values.fold(0L, Math::addExact)
}
data class Scope(val account: String, val profile: String)
data class DateRange(val start: Long, val end: Long) {
    init { require(start <= end) { "Start date must be before end date" } }
    operator fun contains(day: Long) = day in start..end
    companion object {
        fun month(date: LocalDate = LocalDate.now()): DateRange = YearMonth.from(date).let { DateRange(it.atDay(1).toEpochDay(), it.atEndOfMonth().toEpochDay()) }
        fun named(name: String, firstMonday: Boolean = true, today: LocalDate = LocalDate.now()): DateRange = when(name) {
            "Week" -> { val offset = if(firstMonday) today.dayOfWeek.value - 1 else today.dayOfWeek.value % 7; DateRange(today.minusDays(offset.toLong()).toEpochDay(), today.toEpochDay()) }
            "Last month" -> month(today.minusMonths(1))
            "Year" -> DateRange(today.withDayOfYear(1).toEpochDay(), today.withMonth(12).withDayOfMonth(31).toEpochDay())
            else -> month(today)
        }
    }
}
object Validation {
    fun name(value: String): String = value.trim().also { require(it.length in 1..100) { "Name must be 1–100 characters" } }
    fun identifier(value: String): String = value.trim().lowercase(java.util.Locale.ROOT).also { require(it.length in 3..150 && it.none(Char::isWhitespace)) { "Use a username or email with 3–150 characters and no spaces" } }
    fun password(value: String) { require(value.length in 12..256) { "Use a password with 12–256 characters" } }
}
data class Totals(val income: Long = 0, val expense: Long = 0) { val balance: Long get() = Math.subtractExact(income, expense) }
fun remaining(limit: Long, spent: Long) = Math.subtractExact(limit, spent)
