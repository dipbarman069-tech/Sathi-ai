package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [ChatMessageEntity::class, ActionLogEntity::class, BlocklistedAppEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class SaathiDatabase : RoomDatabase() {
  abstract fun saathiDao(): SaathiDao

  companion object {
    @Volatile private var INSTANCE: SaathiDatabase? = null

    fun getInstance(context: Context): SaathiDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                SaathiDatabase::class.java,
                "saathi_encrypted_vault.db",
              )
              .build()
              .also { INSTANCE = it }
        }
    }

    val DEFAULT_SENSITIVE_APPS =
      listOf(
        BlocklistedAppEntity("com.google.android.apps.nbu.paisa.user", "Google Pay (GPay)", "UPI & Payments", true),
        BlocklistedAppEntity("com.phonepe.app", "PhonePe", "UPI & Payments", true),
        BlocklistedAppEntity("net.one97.paytm", "Paytm", "UPI & Payments", true),
        BlocklistedAppEntity("in.org.npci.upiapp", "BHIM UPI", "UPI & Payments", true),
        BlocklistedAppEntity("com.sbi.lotusintouch", "YONO SBI", "Banking", true),
        BlocklistedAppEntity("com.csam.icici.bank.imobile", "iMobile Pay", "Banking", true),
        BlocklistedAppEntity("com.snapwork.hdfc", "HDFC Bank", "Banking", true),
        BlocklistedAppEntity("com.axis.mobile", "Axis Mobile", "Banking", true),
        BlocklistedAppEntity("com.msf.kbank.mobile", "Kotak 811", "Banking", true),
        BlocklistedAppEntity("com.x8bit.bitwarden", "Bitwarden", "Password Vault", true),
        BlocklistedAppEntity("com.agilebits.onepassword", "1Password", "Password Vault", true),
        BlocklistedAppEntity("com.android.vending", "Google Play Store", "App Installer", true),
      )
  }
}
