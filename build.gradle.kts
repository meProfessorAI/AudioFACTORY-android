// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// Automatically decode debug.keystore from debug.keystore.base64 if missing in clean checkouts
val debugKeystoreFile = file("debug.keystore")
val debugKeystoreBase64File = file("debug.keystore.base64")
if (!debugKeystoreFile.exists() && debugKeystoreBase64File.exists()) {
  try {
    val decodedBytes = java.util.Base64.getDecoder().decode(debugKeystoreBase64File.readText().trim())
    debugKeystoreFile.writeBytes(decodedBytes)
  } catch (e: Exception) {
    logger.warn("Could not decode debug.keystore.base64: ${e.message}")
  }
}

