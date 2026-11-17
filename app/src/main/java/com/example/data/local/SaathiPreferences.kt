package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.security.LocalCryptoManager
import com.example.domain.model.AssistantLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.saathiDataStore: DataStore<Preferences> by preferencesDataStore(name = "saathi_prefs")

data class SaathiSettingsState(
  val onlineModeEnabled: Boolean = false,
  val wakeWordEnabled: Boolean = false,
  val wakeWordPhrase: String = "Hey Saathi",
  val preferredLanguage: AssistantLanguage = AssistantLanguage.HINGLISH,
  val ttsPitch: Float = 1.12f,
  val ttsSpeechRate: Float = 1.0f,
  val requireAuthForRiskyActions: Boolean = true,
  val requireVoiceProfileMatch: Boolean = false,
  val hasPinConfigured: Boolean = false,
  val killSwitchActive: Boolean = false,
  val localModelFileName: String = "",
  val optionalOnlineEndpoint: String = "",
)

class SaathiPreferences(private val context: Context) {
  private object Keys {
    val ONLINE_MODE = booleanPreferencesKey("online_mode_enabled")
    val WAKE_WORD = booleanPreferencesKey("wake_word_enabled")
    val WAKE_PHRASE = stringPreferencesKey("wake_word_phrase")
    val LANGUAGE = stringPreferencesKey("preferred_language")
    val TTS_PITCH = floatPreferencesKey("tts_pitch")
    val TTS_RATE = floatPreferencesKey("tts_rate")
    val REQUIRE_AUTH = booleanPreferencesKey("require_auth_risky")
    val REQUIRE_VOICE_MATCH = booleanPreferencesKey("require_voice_match")
    val PIN_HASH_ENC = stringPreferencesKey("pin_hash_enc")
    val PIN_SALT_ENC = stringPreferencesKey("pin_salt_enc")
    val KILL_SWITCH = booleanPreferencesKey("kill_switch_active")
    val LOCAL_MODEL_NAME = stringPreferencesKey("local_model_name")
    val ONLINE_ENDPOINT_ENC = stringPreferencesKey("online_endpoint_enc")
  }

  val settingsFlow: Flow<SaathiSettingsState> =
    context.saathiDataStore.data.map { prefs ->
      val langName = prefs[Keys.LANGUAGE] ?: AssistantLanguage.HINGLISH.name
      val lang = runCatching { AssistantLanguage.valueOf(langName) }.getOrDefault(AssistantLanguage.HINGLISH)
      SaathiSettingsState(
        onlineModeEnabled = prefs[Keys.ONLINE_MODE] ?: false,
        wakeWordEnabled = prefs[Keys.WAKE_WORD] ?: false,
        wakeWordPhrase = prefs[Keys.WAKE_PHRASE] ?: "Hey Saathi",
        preferredLanguage = lang,
        ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.12f,
        ttsSpeechRate = prefs[Keys.TTS_RATE] ?: 1.0f,
        requireAuthForRiskyActions = prefs[Keys.REQUIRE_AUTH] ?: true,
        requireVoiceProfileMatch = prefs[Keys.REQUIRE_VOICE_MATCH] ?: false,
        hasPinConfigured = !prefs[Keys.PIN_HASH_ENC].isNullOrBlank(),
        killSwitchActive = prefs[Keys.KILL_SWITCH] ?: false,
        localModelFileName = prefs[Keys.LOCAL_MODEL_NAME] ?: "",
        optionalOnlineEndpoint =
          prefs[Keys.ONLINE_ENDPOINT_ENC]?.let { LocalCryptoManager.decrypt(it) } ?: "",
      )
    }

  suspend fun setOnlineMode(enabled: Boolean) {
    context.saathiDataStore.edit { it[Keys.ONLINE_MODE] = enabled }
  }

  suspend fun setWakeWordEnabled(enabled: Boolean) {
    context.saathiDataStore.edit { it[Keys.WAKE_WORD] = enabled }
  }

  suspend fun setPreferredLanguage(language: AssistantLanguage) {
    context.saathiDataStore.edit { it[Keys.LANGUAGE] = language.name }
  }

  suspend fun setTtsConfig(pitch: Float, rate: Float) {
    context.saathiDataStore.edit {
      it[Keys.TTS_PITCH] = pitch.coerceIn(0.8f, 1.5f)
      it[Keys.TTS_RATE] = rate.coerceIn(0.75f, 1.4f)
    }
  }

  suspend fun setRequireAuthForRiskyActions(required: Boolean) {
    context.saathiDataStore.edit { it[Keys.REQUIRE_AUTH] = required }
  }

  suspend fun setRequireVoiceProfileMatch(required: Boolean) {
    context.saathiDataStore.edit { it[Keys.REQUIRE_VOICE_MATCH] = required }
  }

  suspend fun setKillSwitchActive(active: Boolean) {
    context.saathiDataStore.edit {
      it[Keys.KILL_SWITCH] = active
      if (active) {
        it[Keys.WAKE_WORD] = false
      }
    }
  }

  suspend fun setLocalModelName(name: String) {
    context.saathiDataStore.edit { it[Keys.LOCAL_MODEL_NAME] = name }
  }

  suspend fun setOptionalOnlineEndpoint(url: String) {
    context.saathiDataStore.edit {
      it[Keys.ONLINE_ENDPOINT_ENC] = if (url.isBlank()) "" else LocalCryptoManager.encrypt(url.trim())
    }
  }

  suspend fun savePin(pin: String) {
    val salt = LocalCryptoManager.generateSalt()
    val hash = LocalCryptoManager.hashPin(pin, salt)
    context.saathiDataStore.edit {
      it[Keys.PIN_SALT_ENC] = LocalCryptoManager.encrypt(salt)
      it[Keys.PIN_HASH_ENC] = LocalCryptoManager.encrypt(hash)
    }
  }

  suspend fun verifyPin(pin: String): Boolean {
    var result = false
    context.saathiDataStore.edit { prefs ->
      val encSalt = prefs[Keys.PIN_SALT_ENC]
      val encHash = prefs[Keys.PIN_HASH_ENC]
      if (!encSalt.isNullOrBlank() && !encHash.isNullOrBlank()) {
        val salt = LocalCryptoManager.decrypt(encSalt)
        val expectedHash = LocalCryptoManager.decrypt(encHash)
        result = LocalCryptoManager.hashPin(pin, salt) == expectedHash
      }
    }
    return result
  }
}
