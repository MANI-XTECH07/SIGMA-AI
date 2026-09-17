package com.example.ai

import com.example.automation.AutomationPlan
import com.example.automation.AutomationStep

class ActionPlanner(private val aiProvider: AiProvider) {

    /**
     * Parses single or chained commands in English, Hindi, Hinglish, or Nepali.
     */
    suspend fun planUserCommand(rawCommand: String, screenContext: String? = null): AutomationPlan {
        val lower = rawCommand.lowercase().trim()

        // 1. Fast path for direct Android actions:
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
        if (lower.contains("tap karo") || lower.contains("click karo") || lower.contains("tap ")) {
            val target = lower.replace("sigma", "")
                .replace("tap karo", "")
                .replace("click karo", "")
                .replace("tap", "")
                .replace("click", "")
                .replace("ko", "")
                .replace("button", "")
                .trim()
            if (target.isNotEmpty()) {
                return AutomationPlan(
                    title = "Tap $target",
                    steps = listOf(AutomationStep.FindAndTap(target))
                )
            }
        }

        // 2. Chained Search & Play commands: e.g. "YouTube kholo aur Naruto AMV search karo"
        if (lower.contains("youtube") && (lower.contains("search") || lower.contains("play"))) {
            val query = lower.substringAfter("search")
                .substringAfter("play")
                .replace("karo", "")
                .replace("aur", "")
                .trim()
            return AutomationPlan(
                title = "YouTube Search",
                steps = listOf(
                    AutomationStep.LaunchApp("YouTube"),
                    AutomationStep.Wait(1200L),
                    AutomationStep.FindAndTap("Search"),
                    AutomationStep.TypeText(query.ifEmpty { "Naruto AMV" })
                )
            )
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
