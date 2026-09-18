package com.example.ai

import com.example.automation.AutomationPlan
import com.example.automation.AutomationStep

class ActionPlanner(private val aiProvider: AiProvider) {

    /**
     * Parses single or chained commands in English, Hindi, Hinglish, or Nepali.
     */
    suspend fun planUserCommand(rawCommand: String, screenContext: String? = null): AutomationPlan {
        val lower = rawCommand.lowercase().trim()

        // 1. Chained Search & Play commands: e.g. "Open YouTube, search for Naruto AMV and play it"
        if (lower.contains("youtube") && (lower.contains("search") || lower.contains("play"))) {
            val query = when {
                lower.contains("search for") -> lower.substringAfter("search for")
                lower.contains("search") -> lower.substringAfter("search")
                lower.contains("play") -> lower.substringAfter("play")
                else -> ""
            }
            val cleanQuery = query
                .substringBefore("and play")
                .substringBefore("aur play")
                .replace("karo", "")
                .replace("aur", "")
                .replace("and", "")
                .replace("it", "")
                .replace(",", "")
                .replace("the first result", "")
                .replace("the first matching result", "")
                .trim()
                .ifEmpty { "Trending" }

            return AutomationPlan(
                title = "YouTube Search & Play",
                steps = listOf(
                    AutomationStep.LaunchApp("YouTube"),
                    AutomationStep.Wait(1200L),
                    AutomationStep.FindAndTap("Search"),
                    AutomationStep.TypeText(cleanQuery),
                    AutomationStep.Wait(1500L),
                    AutomationStep.FindAndTap(cleanQuery)
                )
            )
        }

        // Chained Settings sub-screen navigation: e.g. "Go to settings and open Wi-Fi"
        if (lower.contains("settings") && (lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("bluetooth") || lower.contains("display") || lower.contains("sound") || lower.contains("battery"))) {
            val subSection = when {
                lower.contains("wi-fi") || lower.contains("wifi") -> "Wi-Fi"
                lower.contains("bluetooth") -> "Bluetooth"
                lower.contains("display") -> "Display"
                lower.contains("sound") -> "Sound"
                lower.contains("battery") -> "Battery"
                else -> "Wi-Fi"
            }
            return AutomationPlan(
                title = "Open Settings > $subSection",
                steps = listOf(
                    AutomationStep.LaunchApp("Settings"),
                    AutomationStep.Wait(1000L),
                    AutomationStep.FindAndTap(subSection)
                )
            )
        }

        // Chained Tap & Type: e.g. "Tap search box and type Naruto"
        if (lower.contains("tap") && (lower.contains("type") || lower.contains("write") || lower.contains("enter"))) {
            val tapTarget = lower.substringAfter("tap")
                .substringBefore("and")
                .replace("the", "")
                .replace("button", "")
                .replace(",", "")
                .trim()
            val typeKeyword = when {
                lower.contains("type") -> "type"
                lower.contains("write") -> "write"
                lower.contains("enter") -> "enter"
                else -> ""
            }
            val textToType = if (typeKeyword.isNotEmpty()) {
                val idx = rawCommand.indexOf(typeKeyword, ignoreCase = true)
                if (idx != -1) {
                    rawCommand.substring(idx + typeKeyword.length)
                        .replace("karo", "", ignoreCase = true)
                        .replace(",", "")
                        .trim()
                } else ""
            } else ""

            if (tapTarget.isNotEmpty() && textToType.isNotEmpty()) {
                return AutomationPlan(
                    title = "Tap & Type",
                    steps = listOf(
                        AutomationStep.FindAndTap(tapTarget),
                        AutomationStep.Wait(500L),
                        AutomationStep.TypeText(textToType)
                    )
                )
            }
        }

        // 2. Direct Single Actions:
        // Real Device Lock
        if (lower.contains("phone lock") || lower.contains("lock karo") || lower.contains("lock phone")) {
            return AutomationPlan(
                title = "Lock Phone",
                steps = listOf(AutomationStep.LockDevice())
            )
        }

        // Scroll
        if (lower.contains("scroll down") || lower.contains("neeche scroll")) {
            return AutomationPlan(
                title = "Scroll Down",
                steps = listOf(AutomationStep.Scroll(forward = true))
            )
        }
        if (lower.contains("scroll up") || lower.contains("upar scroll")) {
            return AutomationPlan(
                title = "Scroll Up",
                steps = listOf(AutomationStep.Scroll(forward = false))
            )
        }

        // Tap button
        if (lower.contains("tap karo") || lower.contains("click karo") || lower.contains("tap ") || lower.contains("click ")) {
            val target = lower.replace("sigma", "")
                .replace("tap karo", "")
                .replace("click karo", "")
                .replace("tap", "")
                .replace("click", "")
                .replace("ko", "")
                .replace("button", "")
                .replace(",", "")
                .trim()
            if (target.isNotEmpty()) {
                return AutomationPlan(
                    title = "Tap $target",
                    steps = listOf(AutomationStep.FindAndTap(target))
                )
            }
        }

        // Direct App Launch: "YouTube kholo", "Chrome open karo", "WhatsApp kholo"
        if (lower.contains("kholo") || lower.contains("open") || lower.contains("launch")) {
            val appQuery = lower.replace("sigma", "")
                .replace("kholo", "")
                .replace("open", "")
                .replace("launch", "")
                .replace("chalao", "")
                .trim()
            return AutomationPlan(
                title = "Open $appQuery",
                steps = listOf(AutomationStep.LaunchApp(appQuery))
            )
        }

        // 3. Fallback to AI structured planner
        val aiPlan = aiProvider.planAction(rawCommand, screenContext).getOrNull()
        if (aiPlan != null) {
            val steps = mutableListOf<AutomationStep>()
            when (aiPlan.intent) {
                "LAUNCH_APP" -> steps.add(AutomationStep.LaunchApp(aiPlan.target))
                "SCREEN_ACTION" -> steps.add(AutomationStep.FindAndTap(aiPlan.target))
                "DEVICE_CONTROL" -> {
                    if (aiPlan.target.contains("lock", ignoreCase = true)) {
                        steps.add(AutomationStep.LockDevice())
                    }
                }
                "SEARCH" -> steps.add(AutomationStep.SearchWeb(aiPlan.target))
            }
            if (steps.isNotEmpty()) {
                return AutomationPlan(title = "AI Plan: ${aiPlan.intent}", steps = steps)
            }
        }

        // Default direct launch attempt
        return AutomationPlan(
            title = "Execute Command",
            steps = listOf(AutomationStep.LaunchApp(rawCommand))
        )
    }
}
