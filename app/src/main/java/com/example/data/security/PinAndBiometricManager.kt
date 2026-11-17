package com.example.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Handles biometric authentication (fingerprint/face/device credential) and salted PIN verification
 * before executing sensitive actions or unlocking blocklisted banking/UPI/password apps.
 */
object PinAndBiometricManager {
  fun canAuthenticateBiometric(context: Context): Boolean {
    val manager = BiometricManager.from(context)
    val authenticators =
      BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
  }

  fun promptBiometric(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onFallbackToPin: () -> Unit,
    onError: (String) -> Unit,
  ) {
    val executor = ContextCompat.getMainExecutor(activity)
    val manager = BiometricManager.from(activity)
    val strongOrCredential =
      BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    if (manager.canAuthenticate(strongOrCredential) != BiometricManager.BIOMETRIC_SUCCESS) {
      onFallbackToPin()
      return
    }

    val prompt =
      BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (
              errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
            ) {
              onFallbackToPin()
            } else {
              onError(errString.toString())
            }
          }

          override fun onAuthenticationFailed() {
            // BiometricPrompt handles retry UI internally
          }
        },
      )

    val info =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(strongOrCredential)
        .setConfirmationRequired(true)
        .build()

    prompt.authenticate(info)
  }
}
