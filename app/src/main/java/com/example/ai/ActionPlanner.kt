package com.example.ai

import android.util.Log
import com.example.automation.AutomationPlan
import com.example.automation.AutomationStep

class ActionPlanner(private val aiProvider: AiProvider) {

    companion object {
        private const val TAG = "ActionPlanner"
    }

    /**
     * Parses single or chained commands dynamically in English, Hindi, Hinglish, or Nepali.
     * Preserves original command intent (Section 10) and generates sequential actions.
     */
    suspend fun planUserCommand(rawCommand: String, screenContext: String? = null): AutomationPlan {
        val lower = rawCommand.lowercase().trim()
        val cleanLower = lower.replace("hey sigma", "")
            .replace("sigma,", "")
            .replace("sigma", "")
            .trim()

        Log.d(TAG, "PARSED_ACTIONS planning command: \"$rawCommand\" (clean: \"$cleanLower\")")

        // 1. Chained Tap & Type: e.g. "Tap search and type Naruto"
        if (cleanLower.startsWith("tap ") && (cleanLower.contains("type") || cleanLower.contains("write") || cleanLower.contains("enter"))) {
            val tapTarget = cleanLower.substringAfter("tap ")
                .substringBefore(" and")
                .substringBefore(" aur")
                .replace("the ", "")
                .replace("button", "")
                .replace(",", "")
                .trim()
            val typeKeyword = when {
                cleanLower.contains("type ") -> "type "
                cleanLower.contains("write ") -> "write "
                cleanLower.contains("enter ") -> "enter "
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
                        AutomationStep.TypeText(textToType),
                        AutomationStep.SubmitSearch()
                    ),
                    intent = "SCREEN_ACTION",
                    command = rawCommand
                )
            }
        }

        // 2. Chained App Launch + Search + Play / Select (YouTube, Spotify, etc.)
        // e.g. "search Alan Walker Faded and play it", "search YouTube for [query] and play the first result",
        // "open YouTube, search for lo-fi beats, and play it", "search [query] then play", "find [song] and play"
        val hasSearchWord = cleanLower.contains("search") || cleanLower.contains("find ") ||
                cleanLower.startsWith("find ") || cleanLower.contains("khojo") || cleanLower.contains("look for")
        val hasPlayOrSelect = cleanLower.contains("play") || cleanLower.contains("select") ||
                cleanLower.contains("chalao") || cleanLower.contains("bajao") ||
                cleanLower.contains("first result") || cleanLower.contains("matching result") ||
                cleanLower.contains("listen") || cleanLower.contains("watch")

        if (hasSearchWord) {
            val appTarget = when {
                cleanLower.contains("spotify") -> "Spotify"
                cleanLower.contains("chrome") -> "Chrome"
                cleanLower.contains("play store") || cleanLower.contains("playstore") -> "Play Store"
                cleanLower.contains("maps") -> "Maps"
                cleanLower.startsWith("open ") || cleanLower.startsWith("launch ") -> {
                    val rawApp = cleanLower.substringAfter("open ")
                        .substringAfter("launch ")
                        .substringBefore(" and")
                        .substringBefore(" search")
                        .substringBefore(",")
                        .trim()
                    rawApp.ifEmpty { "YouTube" }
                }
                else -> "YouTube"
            }

            // Extract original-cased query from rawCommand
            val searchPrefixes = listOf(
                "search youtube for ", "search spotify for ", "search in youtube ",
                "search for ", "search ", "find in youtube ", "find ", "khojo "
            )
            var startIndex = -1
            var matchedPrefix = ""
            for (p in searchPrefixes) {
                val idx = rawCommand.indexOf(p, ignoreCase = true)
                if (idx != -1) {
                    startIndex = idx + p.length
                    matchedPrefix = p
                    break
                }
            }

            var queryExtracted = if (startIndex != -1 && startIndex < rawCommand.length) {
                rawCommand.substring(startIndex).trim()
            } else {
                cleanLower
            }

            val playSuffixes = listOf(
                "and play the first result", "and play first result", "play the first result",
                "the first matching result", "the first result", "and play it", "then play it",
                "then play", "and play", "aur play karo", "aur play", "and select it",
                "and select", "play it", "play", "karo", "chalao", "bajao"
            )

            for (s in playSuffixes) {
                val sIdx = queryExtracted.lastIndexOf(s, ignoreCase = true)
                if (sIdx != -1 && sIdx >= queryExtracted.length - s.length - 4) {
                    queryExtracted = queryExtracted.substring(0, sIdx).trim()
                    break
                }
            }

            val cleanQuery = queryExtracted
                .trimEnd(',', '.', '!', ' ')
                .trim()
                .ifEmpty { "Trending" }

            val intent = if (hasPlayOrSelect) "PLAY_MEDIA" else "SEARCH_ONLY"

            val steps = if (hasPlayOrSelect) {
                listOf(
                    AutomationStep.LaunchApp(appTarget),
                    AutomationStep.FindAndTapSearch(),
                    AutomationStep.TypeText(cleanQuery),
                    AutomationStep.SubmitSearch(),
                    AutomationStep.Wait(1200L),
                    AutomationStep.ObserveScreen(),
                    AutomationStep.SelectResult(cleanQuery),
                    AutomationStep.PlayMedia(),
                    AutomationStep.VerifyPlayback()
                )
            } else {
                listOf(
                    AutomationStep.LaunchApp(appTarget),
                    AutomationStep.FindAndTapSearch(),
                    AutomationStep.TypeText(cleanQuery),
                    AutomationStep.SubmitSearch(),
                    AutomationStep.Wait(1200L),
                    AutomationStep.ObserveScreen(),
                    AutomationStep.VerifyResultsDetected(cleanQuery)
                )
            }

            Log.i(TAG, "SIGMA_AUTOMATION: Generated ${steps.size} steps for intent=$intent, target=$appTarget, query=\"$cleanQuery\"")
            return AutomationPlan(
                title = "$appTarget: $cleanQuery",
                steps = steps,
                intent = intent,
                query = cleanQuery,
                command = rawCommand
            )
        }

        // 3. Direct "Play <song / music>" Commands: e.g. "Play Bohemian Rhapsody", "Play songs", "Play music on YouTube"
        if (cleanLower.startsWith("play ") || cleanLower.startsWith("bajao ") || cleanLower.contains("gana chalao") || cleanLower.contains("music chalao")) {
            val isGenericMusic = cleanLower == "play music" || cleanLower == "play some music" || cleanLower == "play songs" ||
                    cleanLower == "play a song" || cleanLower == "play audio" || cleanLower == "music chalao" || cleanLower == "gana chalao" ||
                    cleanLower == "gana bajao" || cleanLower == "play something"

            if (isGenericMusic) {
                return AutomationPlan(
                    title = "Play Music",
                    steps = listOf(
                        AutomationStep.LaunchApp("music"),
                        AutomationStep.Wait(1200L)
                    ),
                    intent = "PLAY_MEDIA",
                    query = "music",
                    command = rawCommand
                )
            }

            val appTarget = if (cleanLower.contains("spotify")) "Spotify" else "YouTube"
            val playIdx = rawCommand.indexOf("play ", ignoreCase = true)
            val bajaoIdx = rawCommand.indexOf("bajao ", ignoreCase = true)
            val prefixLen = if (playIdx != -1) playIdx + 5 else if (bajaoIdx != -1) bajaoIdx + 6 else 0
            val songQuery = rawCommand.substring(prefixLen)
                .replace("on youtube", "", ignoreCase = true)
                .replace("on spotify", "", ignoreCase = true)
                .replace("youtube par", "", ignoreCase = true)
                .replace("spotify par", "", ignoreCase = true)
                .replace("karo", "", ignoreCase = true)
                .replace("chalao", "", ignoreCase = true)
                .trim()

            if (songQuery.isNotEmpty() && songQuery != "music" && songQuery != "songs") {
                return AutomationPlan(
                    title = "$appTarget: $songQuery",
                    steps = listOf(
                        AutomationStep.LaunchApp(appTarget),
                        AutomationStep.FindAndTapSearch(),
                        AutomationStep.TypeText(songQuery),
                        AutomationStep.SubmitSearch(),
                        AutomationStep.Wait(1200L),
                        AutomationStep.ObserveScreen(),
                        AutomationStep.SelectResult(songQuery),
                        AutomationStep.PlayMedia(),
                        AutomationStep.VerifyPlayback()
                    ),
                    intent = "PLAY_MEDIA",
                    query = songQuery,
                    command = rawCommand
                )
            }
        }

        // 4. Chained Settings Sub-Screen: e.g. "Go to settings and open Wi-Fi"
        if (cleanLower.contains("settings") && (cleanLower.contains("wi-fi") || cleanLower.contains("wifi") || cleanLower.contains("bluetooth") || cleanLower.contains("display") || cleanLower.contains("sound") || cleanLower.contains("battery") || cleanLower.contains("apps") || cleanLower.contains("storage"))) {
            val subSection = when {
                cleanLower.contains("wi-fi") || cleanLower.contains("wifi") -> "Wi-Fi"
                cleanLower.contains("bluetooth") -> "Bluetooth"
                cleanLower.contains("display") -> "Display"
                cleanLower.contains("sound") -> "Sound"
                cleanLower.contains("battery") -> "Battery"
                cleanLower.contains("storage") -> "Storage"
                cleanLower.contains("apps") -> "Apps"
                else -> "Wi-Fi"
            }
            return AutomationPlan(
                title = "Settings > $subSection",
                steps = listOf(
                    AutomationStep.LaunchApp("Settings"),
                    AutomationStep.Wait(1000L),
                    AutomationStep.FindAndTap(subSection)
                ),
                intent = "SETTINGS",
                command = rawCommand
            )
        }

        // 5. Chained Scroll + Tap: e.g. "Scroll down and tap Naruto"
        if ((cleanLower.contains("scroll down") || cleanLower.contains("scroll up") || cleanLower.contains("neeche scroll") || cleanLower.contains("upar scroll")) && (cleanLower.contains("and tap") || cleanLower.contains("aur tap") || cleanLower.contains("and click") || cleanLower.contains("aur click"))) {
            val forward = !cleanLower.contains("scroll up") && !cleanLower.contains("upar scroll")
            val target = cleanLower.substringAfter("tap")
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
                ),
                intent = "SCREEN_ACTION",
                command = rawCommand
            )
        }

        // 6. Direct System Actions:
        // Lock Phone
        if (cleanLower.contains("phone lock") || cleanLower.contains("lock karo") || cleanLower.contains("lock phone") || cleanLower.contains("lock my phone") || cleanLower == "lock") {
            return AutomationPlan(
                title = "Lock Phone",
                steps = listOf(AutomationStep.LockDevice()),
                intent = "DEVICE_CONTROL",
                command = rawCommand
            )
        }

        // Go Home / Go Back
        if (cleanLower == "go home" || cleanLower == "home screen" || cleanLower == "home jao" || cleanLower == "home") {
            return AutomationPlan(
                title = "Go Home",
                steps = listOf(AutomationStep.GoHome()),
                intent = "NAVIGATION",
                command = rawCommand
            )
        }
        if (cleanLower == "go back" || cleanLower == "back jao" || cleanLower == "back" || cleanLower == "piche jao") {
            return AutomationPlan(
                title = "Go Back",
                steps = listOf(AutomationStep.GoBack()),
                intent = "NAVIGATION",
                command = rawCommand
            )
        }

        // Single Scroll
        if (cleanLower.contains("scroll down") || cleanLower.contains("neeche scroll") || cleanLower.contains("scroll neeche")) {
            return AutomationPlan(
                title = "Scroll Down",
                steps = listOf(AutomationStep.Scroll(forward = true)),
                intent = "SCREEN_ACTION",
                command = rawCommand
            )
        }
        if (cleanLower.contains("scroll up") || cleanLower.contains("upar scroll") || cleanLower.contains("scroll upar")) {
            return AutomationPlan(
                title = "Scroll Up",
                steps = listOf(AutomationStep.Scroll(forward = false)),
                intent = "SCREEN_ACTION",
                command = rawCommand
            )
        }

        // Single Tap / Click
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
                    steps = listOf(AutomationStep.FindAndTap(target)),
                    intent = "SCREEN_ACTION",
                    command = rawCommand
                )
            }
        }

        // Single Type
        if (cleanLower.startsWith("type ") || cleanLower.startsWith("write ")) {
            val textToType = cleanLower.replace("type ", "").replace("write ", "").trim()
            if (textToType.isNotEmpty()) {
                return AutomationPlan(
                    title = "Type $textToType",
                    steps = listOf(AutomationStep.TypeText(textToType)),
                    intent = "SCREEN_ACTION",
                    command = rawCommand
                )
            }
        }

        // 7. Fallback to AI structured planner if available
        try {
            val aiPlan = aiProvider.planAction(rawCommand, screenContext).getOrNull()
            if (aiPlan != null) {
                Log.i(TAG, "AI_RESPONSE planner generated intent: ${aiPlan.intent} target: ${aiPlan.target}")
                val steps = mutableListOf<AutomationStep>()
                when (aiPlan.intent) {
                    "LAUNCH_APP" -> {
                        val normalizedTarget = when (aiPlan.target.lowercase().trim()) {
                            "music_player", "music player", "music", "audio_player", "audio player" -> "music"
                            "web_browser", "browser" -> "chrome"
                            "video_player" -> "youtube"
                            "file_manager" -> "files"
                            "photo_gallery", "gallery_app" -> "photos"
                            else -> aiPlan.target.replace("_", " ")
                        }
                        steps.add(AutomationStep.LaunchApp(normalizedTarget))
                    }
                    "SCREEN_ACTION" -> steps.add(AutomationStep.FindAndTap(aiPlan.target))
                    "DEVICE_CONTROL" -> {
                        if (aiPlan.target.contains("lock", ignoreCase = true)) {
                            steps.add(AutomationStep.LockDevice())
                        }
                    }
                    "SEARCH" -> steps.add(AutomationStep.SearchWeb(aiPlan.target))
                }
                if (steps.isNotEmpty()) {
                    return AutomationPlan(
                        title = "AI Plan: ${aiPlan.intent}",
                        steps = steps,
                        intent = aiPlan.intent,
                        command = rawCommand
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI planning fallback error: ${e.message}")
        }

        return AutomationPlan(title = "No Action", steps = emptyList(), command = rawCommand)
    }
}
