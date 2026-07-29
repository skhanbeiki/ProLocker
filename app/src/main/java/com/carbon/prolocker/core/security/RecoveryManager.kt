package com.carbon.prolocker.core.security

import com.carbon.prolocker.core.datastore.PreferencesRepository
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

class RecoveryManager(
    private val preferencesRepository: PreferencesRepository
) {
    suspend fun setupRecovery(question: String, answer: String) {
        val hashedAnswer = hashString(answer.trim().lowercase())
        preferencesRepository.updatePreferences { 
            it.copy(
                securityQuestionHash = question,
                securityAnswerHash = hashedAnswer
            )
        }
    }

    suspend fun validateAnswer(answer: String): Boolean {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        if (prefs.securityQuestionHash.isEmpty() || prefs.securityAnswerHash.isEmpty() || prefs.hashedCredential.isEmpty()) {
            return false
        }
        val hashedInput = hashString(answer.trim().lowercase())
        return hashedInput == prefs.securityAnswerHash
    }

    suspend fun clearSecurityData() {
        // Only clear the lock configurations. Keep recovery for the new lock or clear it too?
        // Usually, removing old lock keeps the recovery.
        preferencesRepository.updatePreferences {
            it.copy(
                lockType = "NONE",
                hashedCredential = "",
                securitySalt = ""
            )
        }
    }

    suspend fun hasRecoverySetup(): Boolean {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        return prefs.securityQuestionHash.isNotEmpty() && prefs.securityAnswerHash.isNotEmpty()
    }

    suspend fun getRecoveryQuestion(): String {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        return prefs.securityQuestionHash
    }

    private fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (_e: Exception) {
            input // Fallback, shouldn't happen
        }
    }
}
