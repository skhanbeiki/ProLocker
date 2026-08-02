package com.carbon.prolocker.feature.callblocker.util

import com.carbon.prolocker.feature.callblocker.data.BlockRuleType
import com.carbon.prolocker.feature.callblocker.data.BlockedNumberEntity

object PhoneNumberUtils {

    /**
     * Normalizes phone numbers for consistent matching.
     * Removes spaces, hyphens, brackets, special symbols.
     * Converts +98 prefix to 0 for Iranian numbers.
     */
    fun normalize(phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""
        
        // Remove non-digit characters except leading plus if any
        var cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")

        // Normalize Iranian prefix +98 to 0
        if (cleaned.startsWith("+98")) {
            cleaned = "0" + cleaned.substring(3)
        } else if (cleaned.startsWith("0098")) {
            cleaned = "0" + cleaned.substring(4)
        } else if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1)
        }

        return cleaned
    }

    /**
     * Checks whether an incoming phone number matches any active blocking rule.
     * Returns a Pair indicating (isBlocked, matchingRule).
     */
    fun checkMatch(incomingNumber: String, activeRules: List<BlockedNumberEntity>): Pair<Boolean, BlockedNumberEntity?> {
        val normalizedIncoming = normalize(incomingNumber)
        if (normalizedIncoming.isEmpty()) return Pair(false, null)

        for (rule in activeRules) {
            if (!rule.isEnabled) continue
            val normalizedRulePattern = normalize(rule.numberOrPattern)
            if (normalizedRulePattern.isEmpty()) continue

            val isMatch = when (rule.ruleType) {
                BlockRuleType.EXACT -> {
                    normalizedIncoming == normalizedRulePattern ||
                            incomingNumber.endsWith(normalizedRulePattern) ||
                            normalizedIncoming.endsWith(normalizedRulePattern)
                }
                BlockRuleType.STARTS_WITH -> {
                    normalizedIncoming.startsWith(normalizedRulePattern) ||
                            incomingNumber.startsWith(normalizedRulePattern)
                }
                BlockRuleType.ENDS_WITH -> {
                    normalizedIncoming.endsWith(normalizedRulePattern) ||
                            incomingNumber.endsWith(normalizedRulePattern)
                }
                BlockRuleType.CONTAINS -> {
                    normalizedIncoming.contains(normalizedRulePattern) ||
                            incomingNumber.contains(normalizedRulePattern)
                }
            }

            if (isMatch) {
                return Pair(true, rule)
            }
        }

        return Pair(false, null)
    }
}
