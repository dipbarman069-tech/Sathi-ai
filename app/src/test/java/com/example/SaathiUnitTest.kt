package com.example

import com.example.data.security.LocalCryptoManager
import com.example.domain.model.AssistantAction
import com.example.domain.model.AssistantLanguage
import com.example.domain.model.RiskLevel
import com.example.domain.nlu.IntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaathiUnitTest {
  @Test
  fun `intent parser parses Hinglish timer command to whitelisted JSON`() {
    val intent = IntentParser.parse("5 minute ka timer lagao", AssistantLanguage.HINGLISH)
    assertEquals(AssistantAction.SET_TIMER, intent.action)
    assertEquals("300", intent.parameters["seconds"])
    assertEquals(RiskLevel.SAFE, intent.riskLevel)
    assertTrue(intent.jsonRepresentation.contains("\"SET_TIMER\""))
  }

  @Test
  fun `intent parser marks WhatsApp message as HIGH risk requiring confirmation`() {
    val intent = IntentParser.parse("Rahul ko WhatsApp message bhejo hello kaise ho", AssistantLanguage.HINGLISH)
    assertEquals(AssistantAction.SEND_MESSAGE, intent.action)
    assertEquals(RiskLevel.HIGH, intent.riskLevel)
    assertEquals("whatsapp", intent.parameters["channel"])
    assertTrue(intent.spokenConfirmationPrompt.isNotBlank())
  }

  @Test
  fun `intent parser rejects non-whitelisted JSON actions`() {
    val maliciousJson = """{"action":"DELETE_ALL_FILES","target":"/"}"""
    val parsed = IntentParser.parseStructuredJson(maliciousJson, maliciousJson, AssistantLanguage.ENGLISH)
    assertNull(parsed)
  }

  @Test
  fun `crypto manager encrypts and decrypts cleanly with salted PIN hash`() {
    val secret = "Call Mom at 9876543210"
    val encrypted = LocalCryptoManager.encrypt(secret)
    assertNotEquals(secret, encrypted)
    assertEquals(secret, LocalCryptoManager.decrypt(encrypted))

    val salt = LocalCryptoManager.generateSalt()
    val hash1 = LocalCryptoManager.hashPin("1234", salt)
    val hash2 = LocalCryptoManager.hashPin("1234", salt)
    assertEquals(hash1, hash2)
  }
}
