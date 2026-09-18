package com.example.ai

import android.os.SystemClock
import android.util.Log
import com.example.automation.AutomationDiagnostics
import com.example.automation.AutomationPlan
import com.example.automation.AutomationStep
import com.example.automation.ClassifiedIntent
import com.example.automation.CommandClassifier
import com.example.automation.ContextualResolution
import com.example.automation.DeviceAction
import com.example.automation.GestureAction
import com.example.automation.MediaAction
import com.example.automation.NavigationAction

class ActionPlanner(private val aiProvider: AiProvider) {

    companion object {
        private const val TAG = "ActionPlanner"
    }

    /**
     * Parses single or chained commands dynamically in English, Hindi, Hinglish, or Nepali.
     * Uses Local-First Routing for deterministic commands to achieve 0ms cloud latency,
     * falling back to Gemini only for complex multi-intent reasoning or conversational answers.
     */
    suspend fun planUserCommand(rawCommand: String, screenContext: String? = null): AutomationPlan {
        val lower = rawCommand.lowercase().trim()
        val cleanLower = lower.replace("hey sigma", "")
            .replace("sigma,", "")
            .replace("sigma", "")
            .trim()

        Log.d(TAG, "Planning command: \"$rawCommand\" (clean: \"$cleanLower\")")

        // 1. Classify intent via multilingual local classifier
        val classified = CommandClassifier.classify(rawCommand)

        when (classified) {
            is ClassifiedIntent.Interrupt -> {
                return AutomationPlan(
                    title = "Stop",
                    steps = emptyList(),
                    intent = "INTERRUPT",
                    command = rawCommand
                )
            }

            is ClassifiedIntent.Contextual -> {
                return when (val res = classified.resolution) {
                    is ContextualResolution.SelectFirstResult -> {
                        AutomationPlan(
                            title = "Select First Result",
                            steps = listOf(
                                AutomationStep.ObserveScreen(),
                                AutomationStep.SelectResult(res.query),
                                AutomationStep.PlayMedia(),
                                AutomationStep.VerifyPlayback()
                            ),
                            intent = "PLAY_MEDIA",
                            query = res.query,
                            command = rawCommand
                        )
                    }
                    is ContextualResolution.PlayActive -> {
                        AutomationPlan(
                            title = "Play Active",
                            steps = listOf(
                                AutomationStep.PlayMedia(),
                                AutomationStep.VerifyPlayback()
                            ),
                            intent = "PLAY_MEDIA",
                            query = res.query,
                            command = rawCommand
                        )
                    }
                    is ContextualResolution.ScrollMore -> {
                        AutomationPlan(
                            title = "Scroll More",
                            steps = listOf(AutomationStep.Scroll(res.forward)),
                            intent = "GESTURE",
                            command = rawCommand
                        )
                    }
                    is ContextualResolution.OpenItem -> {
                        AutomationPlan(
                            title = "Open Item",
                            steps = listOf(AutomationStep.FindAndTap(res.query)),
                            intent = "SCREEN_ACTION",
                            command = rawCommand
                        )
                    }
                    ContextualResolution.None -> {
                        AutomationPlan(title = "No Action", steps = emptyList(), command = rawCommand)
                    }
                }
            }

            is ClassifiedIntent.Navigation -> {
                val step = when (classified.action) {
                    NavigationAction.HOME -> AutomationStep.GoHome()
                    NavigationAction.BACK -> AutomationStep.GoBack()
                    NavigationAction.RECENTS -> AutomationStep.OpenRecents()
                    NavigationAction.LOCK -> AutomationStep.LockDevice()
                    NavigationAction.NOTIFICATIONS -> AutomationStep.FindAndTap("Notifications")
                    NavigationAction.QUICK_SETTINGS -> AutomationStep.FindAndTap("Quick settings")
                }
                return AutomationPlan(
                    title = "Navigate ${classified.action.name}",
                    steps = listOf(step),
                    intent = "NAVIGATION",
                    command = rawCommand
                )
            }

            is ClassifiedIntent.DeviceControl -> {
                val step = when (val act = classified.action) {
                    is DeviceAction.SetVolume -> AutomationStep.SetVolume(act.percent)
                    is DeviceAction.VolumeUp -> AutomationStep.AdjustVolume(up = true)
                    is DeviceAction.VolumeDown -> AutomationStep.AdjustVolume(up = false)
                    is DeviceAction.Mute -> AutomationStep.SetMute(mute = true)
                    is DeviceAction.Unmute -> AutomationStep.SetMute(mute = false)
                    is DeviceAction.TakeScreenshot -> AutomationStep.TakeScreenshot()
                    is DeviceAction.TorchOn, is DeviceAction.TorchOff -> AutomationStep.FindAndTap("Flashlight")
                }
                return AutomationPlan(
                    title = "Device Control",
                    steps = listOf(step),
                    intent = "DEVICE_CONTROL",
                    command = rawCommand
                )
            }

            is ClassifiedIntent.MediaControl -> {
                return AutomationPlan(
                    title = "Media ${classified.action.name}",
                    steps = listOf(AutomationStep.MediaControl(classified.action)),
                    intent = "MEDIA_CONTROL",
                    command = rawCommand
                )
            }

            is ClassifiedIntent.ScreenGesture -> {
                val step = when (val g = classified.gesture) {
                    is GestureAction.Tap -> AutomationStep.FindAndTap(g.label)
                    is GestureAction.DoubleTap -> AutomationStep.DoubleTap(g.label, g.x, g.y)
                    is GestureAction.LongPress -> AutomationStep.LongPress(g.label)
                    is GestureAction.Type -> AutomationStep.TypeText(g.text)
                    is GestureAction.ClearText -> AutomationStep.ClearText()
                    is GestureAction.Scroll -> AutomationStep.Scroll(g.forward)
                    is GestureAction.Swipe -> AutomationStep.Swipe(g.direction)
                    is GestureAction.Drag -> AutomationStep.Drag(g.startX, g.startY, g.endX, g.endY)
                }
                return AutomationPlan(
                    title = "Screen Action",
                    steps = listOf(step),
                    intent = "SCREEN_ACTION",
                    command = rawCommand
                )
            }

            is ClassifiedIntent.AppControl -> {
                return if (classified.open) {
                    AutomationPlan(
                        title = "Open ${classified.appQuery}",
                        steps = listOf(AutomationStep.LaunchApp(classified.appQuery)),
                        intent = "LAUNCH_APP",
                        command = rawCommand
                    )
                } else {
                    AutomationPlan(
                        title = "Close App",
                        steps = listOf(AutomationStep.CloseApp()),
                        intent = "NAVIGATION",
                        command = rawCommand
                    )
                }
            }

            is ClassifiedIntent.SearchAndExecute -> {
                val steps = if (classified.autoPlay) {
                    listOf(
                        AutomationStep.LaunchApp(classified.app),
                        AutomationStep.FindAndTapSearch(),
                        AutomationStep.TypeText(classified.query),
                        AutomationStep.SubmitSearch(),
                        AutomationStep.Wait(1200L),
                        AutomationStep.ObserveScreen(),
                        AutomationStep.SelectResult(classified.query),
                        AutomationStep.PlayMedia(),
                        AutomationStep.VerifyPlayback()
                    )
                } else {
                    listOf(
                        AutomationStep.LaunchApp(classified.app),
                        AutomationStep.FindAndTapSearch(),
                        AutomationStep.TypeText(classified.query),
                        AutomationStep.SubmitSearch(),
                        AutomationStep.Wait(1200L),
                        AutomationStep.ObserveScreen(),
                        AutomationStep.VerifyResultsDetected(classified.query)
                    )
                }
                return AutomationPlan(
                    title = "${classified.app}: ${classified.query}",
                    steps = steps,
                    intent = if (classified.autoPlay) "PLAY_MEDIA" else "SEARCH_ONLY",
                    query = classified.query,
                    command = rawCommand
                )
            }

            is ClassifiedIntent.ReadScreen -> {
                return AutomationPlan(
                    title = "Read Screen",
                    steps = listOf(AutomationStep.ObserveScreen()),
                    intent = "READ_SCREEN",
                    command = rawCommand
                )
            }

            is ClassifiedIntent.LearnWorkflow -> {
                return AutomationPlan(
                    title = "Learn Workflow: ${classified.name}",
                    steps = emptyList(),
                    intent = "WORKFLOW_LEARN",
                    command = rawCommand
                )
            }

            is ClassifiedIntent.StopRecordingWorkflow -> {
                return AutomationPlan(
                    title = "Save Workflow",
                    steps = emptyList(),
                    intent = "WORKFLOW_SAVE",
                    command = rawCommand
                )
            }

            is ClassifiedIntent.RunWorkflow -> {
                return AutomationPlan(
                    title = "Run Workflow: ${classified.workflowName}",
                    steps = emptyList(),
                    intent = "WORKFLOW_RUN",
                    command = rawCommand
                )
            }

            is ClassifiedIntent.SmartResultSelection -> {
                return AutomationPlan(
                    title = "Select Result: ${classified.query}",
                    steps = listOf(AutomationStep.SelectResult(classified.query)),
                    intent = "SMART_SELECTION",
                    query = classified.query,
                    command = rawCommand
                )
            }

            is ClassifiedIntent.CompoundMultiStep -> {
                // Parse compound request
                return parseCompoundCommand(rawCommand)
            }

            is ClassifiedIntent.AiReasoningRequired -> {
                // Check compound patterns first before fallback
                val compound = parseCompoundCommand(rawCommand)
                if (compound.steps.isNotEmpty()) {
                    return compound
                }
            }
        }

        // 2. Fallback to AI structured planner if local rules couldn't resolve
        val aiStart = SystemClock.elapsedRealtime()
        try {
            val aiPlan = aiProvider.planAction(rawCommand, screenContext).getOrNull()
            val aiLatency = SystemClock.elapsedRealtime() - aiStart
            AutomationDiagnostics.aiLatencyMs = aiLatency

            if (aiPlan != null) {
                Log.i(TAG, "AI_PLANNER generated intent: ${aiPlan.intent} target: ${aiPlan.target} in ${aiLatency}ms")
                val steps = mutableListOf<AutomationStep>()
                when (aiPlan.intent) {
                    "LAUNCH_APP" -> {
                        val normalizedTarget = when (aiPlan.target.lowercase().trim()) {
                            "music_player", "music player", "music", "audio_player" -> "music"
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
            Log.w(TAG, "AI planning error: ${e.message}")
        }

        return AutomationPlan(title = "No Action", steps = emptyList(), command = rawCommand)
    }

    /**
     * Parses complex compound multi-step commands into a coherent sequential plan.
     * e.g., "Sigma, YouTube kholo, search Naruto, first result play karo aur volume 30 percent karo"
     */
    private fun parseCompoundCommand(rawCommand: String): AutomationPlan {
        val lower = rawCommand.lowercase().trim()
        val cleanLower = lower.replace("hey sigma", "")
            .replace("sigma,", "")
            .replace("sigma", "")
            .trim()

        val steps = mutableListOf<AutomationStep>()
        var parsedIntent = "COMPOUND"
        var mainQuery = ""

        // Detect target app
        val app = when {
            cleanLower.contains("youtube") -> "YouTube"
            cleanLower.contains("spotify") -> "Spotify"
            cleanLower.contains("chrome") || cleanLower.contains("browser") -> "Chrome"
            cleanLower.contains("play store") || cleanLower.contains("playstore") -> "Play Store"
            cleanLower.contains("maps") -> "Maps"
            cleanLower.contains("settings") -> "Settings"
            else -> "YouTube"
        }

        if (cleanLower.contains("kholo") || cleanLower.contains("open") || cleanLower.contains("launch") || cleanLower.contains("khol")) {
            steps.add(AutomationStep.LaunchApp(app))
            steps.add(AutomationStep.Wait(1000L))
        }

        // Detect Tap & Type sequences (e.g. "Tap search and type Naruto")
        if (cleanLower.contains("tap") && cleanLower.contains("type")) {
            val tapTarget = cleanLower.substringAfter("tap ")
                .substringBefore(" and")
                .substringBefore(" aur")
                .substringBefore(" then")
                .replace("on", "")
                .trim()
            val textToType = cleanLower.substringAfter("type ")
                .substringBefore(" and")
                .substringBefore(" aur")
                .trim()

            if (tapTarget.isNotEmpty()) {
                steps.add(AutomationStep.FindAndTap(tapTarget))
                steps.add(AutomationStep.Wait(500L))
            }
            if (textToType.isNotEmpty()) {
                steps.add(AutomationStep.TypeText(textToType))
            }
        }

        // Detect search query
        val searchPrefixes = listOf(
            "search for ", "search youtube for ", "search in youtube ", "search ",
            "find in youtube ", "find ", "khojo ", "khoj "
        )
        for (prefix in searchPrefixes) {
            val idx = rawCommand.indexOf(prefix, ignoreCase = true)
            if (idx != -1) {
                var queryCandidate = rawCommand.substring(idx + prefix.length)
                    .substringBefore(",")
                    .substringBefore(" and")
                    .substringBefore(" aur")
                    .substringBefore(" ani")
                    .substringBefore(" then")
                    .trim()

                val cleanQuery = queryCandidate.replace("karo", "", ignoreCase = true)
                    .replace("gara", "", ignoreCase = true)
                    .trim()

                if (cleanQuery.isNotEmpty()) {
                    mainQuery = cleanQuery
                    steps.add(AutomationStep.FindAndTapSearch())
                    steps.add(AutomationStep.TypeText(cleanQuery))
                    steps.add(AutomationStep.SubmitSearch())
                    steps.add(AutomationStep.Wait(1200L))
                    steps.add(AutomationStep.ObserveScreen())
                    break
                }
            }
        }

        // Detect result selection / play
        if (cleanLower.contains("first result") || cleanLower.contains("first one") || cleanLower.contains("play") ||
            cleanLower.contains("chalao") || cleanLower.contains("bajao") || cleanLower.contains("baja") ||
            cleanLower.contains("pehla") || cleanLower.contains("pahilo")
        ) {
            parsedIntent = "PLAY_MEDIA"
            val target = mainQuery.ifEmpty { "first" }
            steps.add(AutomationStep.SelectResult(target))
            steps.add(AutomationStep.PlayMedia())
            steps.add(AutomationStep.VerifyPlayback())
        }

        // Detect volume setting in compound sentence (e.g. "aur volume 30 percent karo")
        val volumePercentRegex = Regex("""(?:volume|aawaz|sound)\s*(?:to\s*)?(\d{1,3})\s*(?:percent|%|pratishat)?""")
        val volMatch = volumePercentRegex.find(cleanLower)
        if (volMatch != null) {
            val percent = volMatch.groupValues[1].toIntOrNull()
            if (percent != null && percent in 0..100) {
                steps.add(AutomationStep.SetVolume(percent))
            }
        }

        if (steps.isNotEmpty()) {
            Log.i(TAG, "PARSED COMPOUND PLAN with ${steps.size} steps for \"$rawCommand\"")
            return AutomationPlan(
                title = "Multi-Step: $app",
                steps = steps,
                intent = parsedIntent,
                query = mainQuery,
                command = rawCommand
            )
        }

        return AutomationPlan(title = "No Action", steps = emptyList(), command = rawCommand)
    }
}
