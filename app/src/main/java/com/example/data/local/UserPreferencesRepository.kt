package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId

private val Context.prunelleDataStore: DataStore<Preferences> by preferencesDataStore(name = "prunelle_prefs")

enum class PrivacyAudience(val labelFr: String) {
    EVERYONE("Tout le monde"),
    CONTACTS("Mes contacts"),
    NOBODY("Personne")
}

data class UserSessionPrefs(
    val isLoggedIn: Boolean = false,
    val userId: String = "me",
    val email: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val statusText: String = "Disponible sur Prunelle",
    val avatarUri: String? = null,
    val countryCode: String = "+33",
    val phoneNumberPrivate: String = "",
    val isPhoneVerified: Boolean = false,
    val birthDay: Int? = null,
    val birthMonth: Int? = null,
    val birthYear: Int? = null, // Strictement privé, jamais partagé publiquement
    val birthdayPrivacy: PrivacyAudience = PrivacyAudience.CONTACTS,
    val timezoneId: String = ZoneId.systemDefault().id,
    val lastBirthdayCardYear: Int = 0,
    val cachedBirthdayCardPath: String? = null,
    val lastSelfBirthdayNotifiedYear: Int = 0,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val languageCode: String = "fr",
    val globalWallpaperId: String = "cosmos_aurora",
    val globalWallpaperUri: String? = null,
    val targetFps: Int = 120,
    val dataSaverEnabled: Boolean = false,
    val aiMemoryEnabled: Boolean = true,
    val readReceiptsEnabled: Boolean = true,
    val avatarPrivacy: PrivacyAudience = PrivacyAudience.EVERYONE,
    val bioPrivacy: PrivacyAudience = PrivacyAudience.EVERYONE,
    val onlinePresenceVisible: Boolean = true,
    val lastSeenVisible: Boolean = true,
    val notifyMessages: Boolean = true,
    val notifyGroups: Boolean = true,
    val notifyCalls: Boolean = true,
    val notifyReactions: Boolean = true,
    val notifyChannels: Boolean = true,
    val notifyStatusLikes: Boolean = true,
    val notifyBirthdays: Boolean = true,
    val backendBaseUrl: String = "https://api.prunelle.invalid/",
    val fcmToken: String? = null
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val USERNAME = stringPreferencesKey("username")
        val BIO = stringPreferencesKey("bio")
        val STATUS_TEXT = stringPreferencesKey("status_text")
        val AVATAR_URI = stringPreferencesKey("avatar_uri")
        val COUNTRY_CODE = stringPreferencesKey("country_code")
        val PHONE_NUMBER = stringPreferencesKey("phone_number")
        val PHONE_VERIFIED = booleanPreferencesKey("phone_verified")
        val BIRTH_DAY = intPreferencesKey("birth_day")
        val BIRTH_MONTH = intPreferencesKey("birth_month")
        val BIRTH_YEAR = intPreferencesKey("birth_year")
        val BIRTHDAY_PRIVACY = stringPreferencesKey("birthday_privacy")
        val TIMEZONE_ID = stringPreferencesKey("timezone_id")
        val LAST_BDAY_CARD_YEAR = intPreferencesKey("last_bday_card_year")
        val CACHED_BDAY_CARD_PATH = stringPreferencesKey("cached_bday_card_path")
        val LAST_SELF_BDAY_NOTIF_YEAR = intPreferencesKey("last_self_bday_notif_year")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val GLOBAL_WALLPAPER_ID = stringPreferencesKey("global_wallpaper_id")
        val GLOBAL_WALLPAPER_URI = stringPreferencesKey("global_wallpaper_uri")
        val TARGET_FPS = intPreferencesKey("target_fps")
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val AI_MEMORY_ENABLED = booleanPreferencesKey("ai_memory_enabled")
        val READ_RECEIPTS = booleanPreferencesKey("read_receipts")
        val AVATAR_PRIVACY = stringPreferencesKey("avatar_privacy")
        val BIO_PRIVACY = stringPreferencesKey("bio_privacy")
        val ONLINE_VISIBLE = booleanPreferencesKey("online_visible")
        val LAST_SEEN_VISIBLE = booleanPreferencesKey("last_seen_visible")
        val NOTIFY_MESSAGES = booleanPreferencesKey("notify_messages")
        val NOTIFY_GROUPS = booleanPreferencesKey("notify_groups")
        val NOTIFY_CALLS = booleanPreferencesKey("notify_calls")
        val NOTIFY_REACTIONS = booleanPreferencesKey("notify_reactions")
        val NOTIFY_CHANNELS = booleanPreferencesKey("notify_channels")
        val NOTIFY_STATUS_LIKES = booleanPreferencesKey("notify_status_likes")
        val NOTIFY_BIRTHDAYS = booleanPreferencesKey("notify_birthdays")
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
    }

    val userPrefs: Flow<UserSessionPrefs> = context.prunelleDataStore.data.map { p ->
        UserSessionPrefs(
            isLoggedIn = p[Keys.IS_LOGGED_IN] ?: false,
            userId = p[Keys.USER_ID] ?: "me",
            email = p[Keys.EMAIL] ?: "",
            displayName = p[Keys.DISPLAY_NAME] ?: "",
            username = p[Keys.USERNAME] ?: "",
            bio = p[Keys.BIO] ?: "",
            statusText = p[Keys.STATUS_TEXT] ?: "Disponible sur Prunelle",
            avatarUri = p[Keys.AVATAR_URI],
            countryCode = p[Keys.COUNTRY_CODE] ?: "+33",
            phoneNumberPrivate = p[Keys.PHONE_NUMBER] ?: "",
            isPhoneVerified = p[Keys.PHONE_VERIFIED] ?: false,
            birthDay = p[Keys.BIRTH_DAY]?.takeIf { it in 1..31 },
            birthMonth = p[Keys.BIRTH_MONTH]?.takeIf { it in 1..12 },
            birthYear = p[Keys.BIRTH_YEAR]?.takeIf { it in 1900..2100 },
            birthdayPrivacy = runCatching {
                PrivacyAudience.valueOf(p[Keys.BIRTHDAY_PRIVACY] ?: PrivacyAudience.CONTACTS.name)
            }.getOrDefault(PrivacyAudience.CONTACTS),
            timezoneId = p[Keys.TIMEZONE_ID] ?: ZoneId.systemDefault().id,
            lastBirthdayCardYear = p[Keys.LAST_BDAY_CARD_YEAR] ?: 0,
            cachedBirthdayCardPath = p[Keys.CACHED_BDAY_CARD_PATH],
            lastSelfBirthdayNotifiedYear = p[Keys.LAST_SELF_BDAY_NOTIF_YEAR] ?: 0,
            themePreference = runCatching {
                ThemePreference.valueOf(p[Keys.THEME_MODE] ?: ThemePreference.SYSTEM.name)
            }.getOrDefault(ThemePreference.SYSTEM),
            languageCode = p[Keys.LANGUAGE] ?: "fr",
            globalWallpaperId = p[Keys.GLOBAL_WALLPAPER_ID] ?: "cosmos_aurora",
            globalWallpaperUri = p[Keys.GLOBAL_WALLPAPER_URI],
            targetFps = p[Keys.TARGET_FPS] ?: 120,
            dataSaverEnabled = p[Keys.DATA_SAVER] ?: false,
            aiMemoryEnabled = p[Keys.AI_MEMORY_ENABLED] ?: true,
            readReceiptsEnabled = p[Keys.READ_RECEIPTS] ?: true,
            avatarPrivacy = runCatching {
                PrivacyAudience.valueOf(p[Keys.AVATAR_PRIVACY] ?: PrivacyAudience.EVERYONE.name)
            }.getOrDefault(PrivacyAudience.EVERYONE),
            bioPrivacy = runCatching {
                PrivacyAudience.valueOf(p[Keys.BIO_PRIVACY] ?: PrivacyAudience.EVERYONE.name)
            }.getOrDefault(PrivacyAudience.EVERYONE),
            onlinePresenceVisible = p[Keys.ONLINE_VISIBLE] ?: true,
            lastSeenVisible = p[Keys.LAST_SEEN_VISIBLE] ?: true,
            notifyMessages = p[Keys.NOTIFY_MESSAGES] ?: true,
            notifyGroups = p[Keys.NOTIFY_GROUPS] ?: true,
            notifyCalls = p[Keys.NOTIFY_CALLS] ?: true,
            notifyReactions = p[Keys.NOTIFY_REACTIONS] ?: true,
            notifyChannels = p[Keys.NOTIFY_CHANNELS] ?: true,
            notifyStatusLikes = p[Keys.NOTIFY_STATUS_LIKES] ?: true,
            notifyBirthdays = p[Keys.NOTIFY_BIRTHDAYS] ?: true,
            backendBaseUrl = p[Keys.BACKEND_URL] ?: "https://api.prunelle.invalid/",
            fcmToken = p[Keys.FCM_TOKEN]
        )
    }

    suspend fun bindAuthenticatedIdentity(
        uid: String,
        email: String?,
        displayNameFallback: String?,
        avatarFallback: String?
    ) {
        context.prunelleDataStore.edit { p ->
            p[Keys.USER_ID] = uid
            if (!email.isNullOrBlank()) p[Keys.EMAIL] = email
            if (p[Keys.DISPLAY_NAME].isNullOrBlank() && !displayNameFallback.isNullOrBlank()) {
                p[Keys.DISPLAY_NAME] = displayNameFallback
            }
            if (p[Keys.AVATAR_URI].isNullOrBlank() && !avatarFallback.isNullOrBlank()) {
                p[Keys.AVATAR_URI] = avatarFallback
            }
            p[Keys.TIMEZONE_ID] = ZoneId.systemDefault().id
        }
    }

    suspend fun saveProfileAndLogin(
        displayName: String,
        username: String,
        bio: String,
        avatarUri: String?,
        countryCode: String,
        phoneNumber: String,
        isPhoneVerified: Boolean,
        birthDay: Int? = null,
        birthMonth: Int? = null,
        birthYear: Int? = null,
        birthdayPrivacy: PrivacyAudience = PrivacyAudience.CONTACTS
    ) {
        context.prunelleDataStore.edit { p ->
            p[Keys.IS_LOGGED_IN] = true
            p[Keys.DISPLAY_NAME] = displayName.trim()
            p[Keys.USERNAME] = username.trim().lowercase().removePrefix("@")
            p[Keys.BIO] = bio.trim()
            if (avatarUri != null) p[Keys.AVATAR_URI] = avatarUri
            p[Keys.COUNTRY_CODE] = countryCode
            p[Keys.PHONE_NUMBER] = phoneNumber.trim()
            p[Keys.PHONE_VERIFIED] = isPhoneVerified
            if (birthDay != null && birthDay in 1..31) p[Keys.BIRTH_DAY] = birthDay else p.remove(Keys.BIRTH_DAY)
            if (birthMonth != null && birthMonth in 1..12) p[Keys.BIRTH_MONTH] = birthMonth else p.remove(Keys.BIRTH_MONTH)
            if (birthYear != null && birthYear in 1900..2100) p[Keys.BIRTH_YEAR] = birthYear else p.remove(Keys.BIRTH_YEAR)
            p[Keys.BIRTHDAY_PRIVACY] = birthdayPrivacy.name
            p[Keys.TIMEZONE_ID] = ZoneId.systemDefault().id
        }
    }

    suspend fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        statusText: String,
        avatarUri: String?,
        birthDay: Int? = null,
        birthMonth: Int? = null,
        birthYear: Int? = null,
        birthdayPrivacy: PrivacyAudience = PrivacyAudience.CONTACTS
    ) {
        context.prunelleDataStore.edit { p ->
            p[Keys.DISPLAY_NAME] = displayName.trim()
            p[Keys.USERNAME] = username.trim().lowercase().removePrefix("@")
            p[Keys.BIO] = bio.trim()
            p[Keys.STATUS_TEXT] = statusText.trim()
            if (avatarUri != null) p[Keys.AVATAR_URI] = avatarUri
            if (birthDay != null && birthDay in 1..31) p[Keys.BIRTH_DAY] = birthDay else p.remove(Keys.BIRTH_DAY)
            if (birthMonth != null && birthMonth in 1..12) p[Keys.BIRTH_MONTH] = birthMonth else p.remove(Keys.BIRTH_MONTH)
            if (birthYear != null && birthYear in 1900..2100) p[Keys.BIRTH_YEAR] = birthYear else p.remove(Keys.BIRTH_YEAR)
            p[Keys.BIRTHDAY_PRIVACY] = birthdayPrivacy.name
            p[Keys.TIMEZONE_ID] = ZoneId.systemDefault().id
        }
    }

    suspend fun saveBirthdayCardGenerated(year: Int, cardPath: String?) {
        context.prunelleDataStore.edit { p ->
            p[Keys.LAST_BDAY_CARD_YEAR] = year
            if (!cardPath.isNullOrBlank()) {
                p[Keys.CACHED_BDAY_CARD_PATH] = cardPath
            } else {
                p.remove(Keys.CACHED_BDAY_CARD_PATH)
            }
        }
    }

    suspend fun markSelfBirthdayNotified(year: Int) {
        context.prunelleDataStore.edit { p ->
            p[Keys.LAST_SELF_BDAY_NOTIF_YEAR] = year
        }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.prunelleDataStore.edit { it[Keys.THEME_MODE] = theme.name }
    }

    suspend fun setLanguage(code: String) {
        context.prunelleDataStore.edit { it[Keys.LANGUAGE] = code }
    }

    suspend fun setGlobalWallpaper(wallpaperId: String, customUri: String?) {
        context.prunelleDataStore.edit { p ->
            p[Keys.GLOBAL_WALLPAPER_ID] = wallpaperId
            if (customUri != null) {
                p[Keys.GLOBAL_WALLPAPER_URI] = customUri
            } else {
                p.remove(Keys.GLOBAL_WALLPAPER_URI)
            }
        }
    }

    suspend fun setPerformanceSettings(targetFps: Int, dataSaver: Boolean) {
        context.prunelleDataStore.edit { p ->
            p[Keys.TARGET_FPS] = targetFps
            p[Keys.DATA_SAVER] = dataSaver
        }
    }

    suspend fun setAiMemoryEnabled(enabled: Boolean) {
        context.prunelleDataStore.edit { it[Keys.AI_MEMORY_ENABLED] = enabled }
    }

    suspend fun updatePrivacy(
        readReceipts: Boolean,
        avatarPrivacy: PrivacyAudience,
        bioPrivacy: PrivacyAudience,
        onlineVisible: Boolean,
        lastSeenVisible: Boolean,
        birthdayPrivacy: PrivacyAudience = PrivacyAudience.CONTACTS
    ) {
        context.prunelleDataStore.edit { p ->
            p[Keys.READ_RECEIPTS] = readReceipts
            p[Keys.AVATAR_PRIVACY] = avatarPrivacy.name
            p[Keys.BIO_PRIVACY] = bioPrivacy.name
            p[Keys.ONLINE_VISIBLE] = onlineVisible
            p[Keys.LAST_SEEN_VISIBLE] = lastSeenVisible
            p[Keys.BIRTHDAY_PRIVACY] = birthdayPrivacy.name
        }
    }

    suspend fun updateNotifications(
        messages: Boolean,
        groups: Boolean,
        calls: Boolean,
        reactions: Boolean,
        channels: Boolean,
        statusLikes: Boolean,
        birthdays: Boolean = true
    ) {
        context.prunelleDataStore.edit { p ->
            p[Keys.NOTIFY_MESSAGES] = messages
            p[Keys.NOTIFY_GROUPS] = groups
            p[Keys.NOTIFY_CALLS] = calls
            p[Keys.NOTIFY_REACTIONS] = reactions
            p[Keys.NOTIFY_CHANNELS] = channels
            p[Keys.NOTIFY_STATUS_LIKES] = statusLikes
            p[Keys.NOTIFY_BIRTHDAYS] = birthdays
        }
    }

    suspend fun setBackendUrl(url: String) {
        context.prunelleDataStore.edit { it[Keys.BACKEND_URL] = url.trim() }
    }

    suspend fun saveFcmToken(token: String) {
        if (token.isBlank()) return
        context.prunelleDataStore.edit { it[Keys.FCM_TOKEN] = token.trim() }
    }

    suspend fun logout() {
        context.prunelleDataStore.edit { p ->
            p[Keys.IS_LOGGED_IN] = false
        }
    }
}
