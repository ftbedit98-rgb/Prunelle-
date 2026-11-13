package com.example.core.birthday

import com.example.data.local.ContactEntity
import com.example.data.local.PrivacyAudience
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

data class BirthdayContactMatch(
    val contact: ContactEntity,
    val year: Int,
    val alreadyGreetedThisYear: Boolean
)

object BirthdayHelper {
    const val SELF_BIRTHDAY_MESSAGE =
        "Joyeux anniversaire ! Toute l'équipe Prunelle te souhaite une merveilleuse journée !"

    fun safeZoneId(timezoneId: String?): ZoneId {
        if (timezoneId.isNullOrBlank()) return ZoneId.systemDefault()
        return runCatching { ZoneId.of(timezoneId) }.getOrElse { ZoneId.systemDefault() }
    }

    fun nowInZone(timezoneId: String?): ZonedDateTime = ZonedDateTime.now(safeZoneId(timezoneId))

    /**
     * Checks if `birthDay`/`birthMonth` matches today in the given timezone.
     * For Feb 29 birthdays in non-leap years, matches Feb 28 cleanly.
     */
    fun isBirthdayToday(
        birthDay: Int?,
        birthMonth: Int?,
        timezoneId: String? = null,
        now: ZonedDateTime = nowInZone(timezoneId)
    ): Boolean {
        if (birthDay == null || birthMonth == null) return false
        if (birthDay !in 1..31 || birthMonth !in 1..12) return false
        val todayMonth = now.monthValue
        val todayDay = now.dayOfMonth
        if (birthMonth == 2 && birthDay == 29 && !now.toLocalDate().isLeapYear) {
            return todayMonth == 2 && todayDay == 28
        }
        return todayMonth == birthMonth && todayDay == birthDay
    }

    fun canNotifyContactBirthday(contact: ContactEntity): Boolean {
        return !contact.isBlocked &&
            contact.birthDay != null &&
            contact.birthMonth != null &&
            contact.birthdayPrivacy != PrivacyAudience.NOBODY
    }

    /**
     * Formats ONLY day and month so a user's birth year is never exposed publicly.
     */
    fun formatPublicDayMonth(day: Int?, month: Int?): String {
        if (day == null || month == null || day !in 1..31 || month !in 1..12) return "Non renseigné"
        val monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.FRENCH)
        return "$day $monthName"
    }

    fun buildContactGreetingSuggestion(contactName: String): String {
        return "🎂 Joyeux anniversaire ${contactName.trim()} ! Je te souhaite une merveilleuse journée remplie de bonheur ✨"
    }
}
