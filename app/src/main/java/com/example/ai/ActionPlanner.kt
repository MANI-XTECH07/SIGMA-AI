package com.example.ai

import com.example.automation.AutomationPlan
import com.example.automation.AutomationStep

class ActionPlanner(private val aiProvider: AiProvider) {

    /**
     * Parses single or chained commands dynamically in English, Hindi, Hinglish, or Nepali.
     * Generates structured automation steps for any requested app/flow.
     */
    suspend fun planUserCommand(rawCommand: String, screenContext: String? = null): AutomationPlan {
        val lower = rawCommand.lowercase().trim()
        val cleanLower = lower.replace("hey sigma", "")
            .replace("sigma", "")
            .trim()

        // 1. Chained App Launch + Search + Play / Select
        // e.g. "Open YouTube, search for Naruto AMV and play it" or "Open Spotify and search Eminem and play"
        val hasLaunchWord = lower.startsWith("open ") || lower.startsWith("launch ") || lower.contains("kholo") || lower.contains("chalao")
        val hasSearchWord = lower.contains("search for ") || lower.contains("search ") || lower.contains("khojo ") || lower.contains("find ")
        val hasPlayOrSelect = lower.contains("play") || lower.contains("select") || lower.contains("chalao") || lower.contains("first result") || lower.contains("matching result")

        if ((hasLaunchWord || lower.contains("youtube") || lower.contains("spotify") || lower.contains("chrome")) && hasSearchWord) {
            val appTarget = when {
                lower.contains("youtube") -> "YouTube"
                lower.contains("spotify") -> "Spotify"
                lower.contains("chrome") -> "Chrome"
                lower.contains("play store") || lower.contains("playstore") -> "Play Store"
                lower.contains("maps") -> "Maps"
                hasLaunchWord -> {
                    val rawApp = lower.substringAfter("open ")
                        .substringAfter("launch ")
                        .substringBefore(" and")
                        .substringBefore(" search")
                        .substringBefore(",")
                        .trim()
                    rawApp.ifEmpty { "YouTube" }
                }
                else -> "YouTube"
            }

            val querySegment = when {
                lower.contains("search for") -> lower.substringAfter("search for")
                lower.contains("search") -> lower.substringAfter("search")
                lower.contains("khojo") -> lower.substringAfter("khojo")
                lower.contains("find") -> lower.substringAfter("find")
                else -> ""
            }

            val cleanQuery = querySegment
                .substringBefore("and play")
                .substringBefore("aur play")
                .substringBefore("and select")
                .replace("the first result", "")
                .replace("the first matching result", "")
                .replace("karo", "")
                .replace("aur", "")
                .replace("and", "")
                .replace("it", "")
                .replace(",", "")
                .trim()
                .ifEmpty { "Trending" }

            val steps = mutableListOf<AutomationStep>(
                AutomationStep.LaunchApp(appTarget),
                AutomationStep.Wait(1200L),
                AutomationStep.FindAndTapSearch(),
                AutomationStep.TypeText(cleanQuery),
                AutomationStep.Wait(1500L)
            )

            if (hasPlayOrSelect) {
                steps.add(AutomationStep.FindAndTapResult(cleanQuery))
            }

            return AutomationPlan(
                title = "$appTarget Search ${if (hasPlayOrSelect) "& Play" else ""}".trim(),
                steps = steps
            )
        }

        // 2. Chained Settings Sub-Screen: e.g. "Go to settings and open Wi-Fi"
        if (lower.contains("settings") && (lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("bluetooth") || lower.contains("display") || lower.contains("sound") || lower.contains("battery") || lower.contains("apps") || lower.contains("storage"))) {
            val subSection = when {
                lower.contains("wi-fi") || lower.contains("wifi") -> "Wi-Fi"
                lower.contains("bluetooth") -> "Bluetooth"
                lower.contains("display") -> "Display"
                lower.contains("sound") -> "Sound"
                lower.contains("battery") -> "Battery"
                lower.contains("storage") -> "Storage"
                lower.contains("apps") -> "Apps"
                else -> "Wi-Fi"
            }
            return AutomationPlan(
                title = "Settings > $subSection",
                steps = listOf(
                    AutomationStep.LaunchApp("Settings"),
                    AutomationStep.Wait(1000L),
                    AutomationStep.FindAndTap(subSection)
                )
            )
        }

        // 3. Chained Scroll + Tap: e.g. "Scroll down and tap Naruto"
        if ((lower.contains("scroll down") || lower.contains("scroll up") || lower.contains("neeche scroll") || lower.contains("upar scroll")) && (lower.contains("and tap") || lower.contains("aur tap") || lower.contains("and click") || lower.contains("aur click"))) {
            val forward = !lower.contains("scroll up") && !lower.contains("upar scroll")
            val target = lower.substringAfter("tap")
                .substringAfter("click")
                .replace("the", "")
                .replace("button", "")
                .replace(",", "")
                .replace("karo", "")
                .trim()

            return AutomationPlan(
                title = "Scroll & Tap",
                steps = listOf(
                    AutomationStep.Scroll(forward = forward),
                    AutomationStep.Wait(600L),
                    AutomationStep.FindAndTap(target)
                )
            )
        }

        // 4. Chained Tap & Type: e.g. "Tap search and type Naruto"
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

        // 5. Direct System Actions:
        // Lock Phone
        if (cleanLower.contains("phone lock") || cleanLower.contains("lock karo") || cleanLower.contains("lock phone") || cleanLower.contains("lock my phone") || cleanLower == "lock") {
            return AutomationPlan(
                title = "Lock Phone",
                steps = listOf(AutomationStep.LockDevice())
            )
        }

        // Go Home / Go Back
        if (cleanLower == "go home" || cleanLower == "home screen" || cleanLower == "home jao" || cleanLower == "home") {
            return AutomationPlan(
                title = "Go Home",
                steps = listOf(AutomationStep.GoHome())
            )
        }
        if (cleanLower == "go back" || cleanLower == "back jao" || cleanLower == "back" || cleanLower == "piche jao") {
            return AutomationPlan(
                title = "Go Back",
                steps = listOf(AutomationStep.GoBack())
            )
        }

        // Single Scroll
        if (cleanLower.contains("scroll down") || cleanLower.contains("neeche scroll") || cleanLower.contains("scroll neeche")) {
            return AutomationPlan(
                title = "Scroll Down",
                steps = listOf(AutomationStep.Scroll(forward = true))
            )
        }
        if (cleanLower.contains("scroll up") || cleanLower.contains("upar scroll") || cleanLower.contains("scroll upar")) {
            return AutomationPlan(
                title = "Scroll Up",
                steps = listOf(AutomationStep.Scroll(forward = false))
            )
        }

        // Single Tap
        if (cleanLower.startsWith("tap ") || cleanLower.startsWith("click ") || cleanLower.contains("tap karo") || cleanLower.contains("click karo")) {
            val target = cleanLower.replace("tap karo", "")
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

        // Fallback to AI structured planner if available
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

        return AutomationPlan(title = "No Action", steps = emptyList())
    }
}
