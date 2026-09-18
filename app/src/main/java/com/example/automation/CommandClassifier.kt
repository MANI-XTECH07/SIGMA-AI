package com.example.automation

import android.util.Log

sealed class ClassifiedIntent {
    data class Interrupt(val phrase: String) : ClassifiedIntent()
    data class Navigation(val action: NavigationAction) : ClassifiedIntent()
    data class DeviceControl(val action: DeviceAction) : ClassifiedIntent()
    data class MediaControl(val action: MediaAction) : ClassifiedIntent()
    data class ScreenGesture(val gesture: GestureAction) : ClassifiedIntent()
    data class AppControl(val appQuery: String, val open: Boolean = true) : ClassifiedIntent()
    data class SearchAndExecute(val app: String, val query: String, val autoPlay: Boolean) : ClassifiedIntent()
    data class Contextual(val resolution: ContextualResolution) : ClassifiedIntent()
    data class CompoundMultiStep(val rawCommand: String) : ClassifiedIntent()
    object ReadScreen : ClassifiedIntent()
    data class LearnWorkflow(val name: String) : ClassifiedIntent()
    object StopRecordingWorkflow : ClassifiedIntent()
    data class RunWorkflow(val workflowName: String) : ClassifiedIntent()
    data class SmartResultSelection(val query: String) : ClassifiedIntent()
    data class AiReasoningRequired(val prompt: String) : ClassifiedIntent()
}

enum class NavigationAction {
    HOME, BACK, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LOCK
}

sealed class DeviceAction {
    data class SetVolume(val percent: Int) : DeviceAction()
    object VolumeUp : DeviceAction()
    object VolumeDown : DeviceAction()
    object Mute : DeviceAction()
    object Unmute : DeviceAction()
    object TakeScreenshot : DeviceAction()
    object TorchOn : DeviceAction()
    object TorchOff : DeviceAction()
}

enum class MediaAction {
    PLAY, PAUSE, NEXT, PREVIOUS, TOGGLE
}

sealed class GestureAction {
    data class Tap(val label: String) : GestureAction()
    data class DoubleTap(val x: Float? = null, val y: Float? = null, val label: String? = null) : GestureAction()
    data class LongPress(val label: String) : GestureAction()
    data class Type(val text: String) : GestureAction()
    object ClearText : GestureAction()
    data class Scroll(val forward: Boolean, val magnitude: Float = 0.5f) : GestureAction()
    data class Swipe(val direction: String) : GestureAction()
    data class Drag(val startX: Float, val startY: Float, val endX: Float, val endY: Float) : GestureAction()
}

object CommandClassifier {
    private const val TAG = "CommandClassifier"

    /**
     * Classifies raw input into structured local or AI intents supporting English, Hindi, Hinglish, and Nepali.
     */
    fun classify(raw: String): ClassifiedIntent {
        val lower = raw.lowercase().trim()
        val clean = lower.replace("hey sigma", "")
            .replace("sigma,", "")
            .replace("sigma", "")
            .replace("ok google", "")
            .trim()

        Log.d(TAG, "Classifying command: \"$raw\" (clean: \"$clean\")")

        // 1. Interrupts & Cancellations (Immediate priority across all 4 languages)
        if (clean == "stop" || clean == "cancel" || clean == "never mind" || clean == "nevermind" ||
            clean == "ruko" || clean == "ruk" || clean == "band karo" || clean == "rok" ||
            clean == "rokis" || clean == "chhod" || clean == "chhad" || clean == "paxi" ||
            clean == "abort" || clean == "hatao" || clean == "shant" || clean == "chup"
        ) {
            return ClassifiedIntent.Interrupt(clean)
        }

        // 1b. Screen Understanding: "Sigma, read this screen" / "screen padho"
        if (clean == "read this screen" || clean == "read screen" || clean == "read the screen" ||
            clean == "screen padho" || clean == "kya dikh raha hai" || clean == "screen summarize karo" ||
            clean == "screen explain karo" || clean == "what is on my screen" || clean == "explain this screen" ||
            clean == "screen ma k cha" || clean == "screen hera"
        ) {
            return ClassifiedIntent.ReadScreen
        }

        // 1c. Teach SIGMA / Workflow Recording: "Sigma, learn this" / "teach sigma"
        if (clean == "learn this" || clean == "teach sigma" || clean == "start recording" ||
            clean == "record this" || clean == "record workflow" || clean == "ye seekho" ||
            clean == "sikh sigma" || clean == "learn workflow"
        ) {
            return ClassifiedIntent.LearnWorkflow(name = "Learned Workflow")
        }

        if (clean.startsWith("learn workflow ") || clean.startsWith("record workflow ") || clean.startsWith("teach me ")) {
            val name = clean.substringAfter("workflow ").substringAfter("me ").trim()
            return ClassifiedIntent.LearnWorkflow(name = name.ifEmpty { "Learned Workflow" })
        }

        // 1d. Stop / Save Workflow Recording
        if (clean == "stop recording" || clean == "save workflow" || clean == "save this" ||
            clean == "recording band karo" || clean == "workflow save karo" || clean == "recording stop"
        ) {
            return ClassifiedIntent.StopRecordingWorkflow
        }

        // 1e. Replay Workflow: "Sigma run Daily YouTube" / "run Morning routine"
        if (clean.startsWith("run workflow ") || clean.startsWith("run ") || clean.startsWith("play workflow ") ||
            clean.startsWith("chalao workflow ") || clean.startsWith("execute workflow ")
        ) {
            val targetWorkflow = clean.replace("run workflow ", "")
                .replace("play workflow ", "")
                .replace("chalao workflow ", "")
                .replace("execute workflow ", "")
                .replace("run ", "")
                .trim()
            if (targetWorkflow.isNotEmpty() && !targetWorkflow.contains("app") && !targetWorkflow.contains("search")) {
                return ClassifiedIntent.RunWorkflow(workflowName = targetWorkflow)
            }
        }

        // 1f. Smart Result Selection: "first result", "second one", "the video about Naruto"
        if (clean.contains("result") || clean.contains("one") || clean.contains("pehla") || clean.contains("dusra") ||
            clean.contains("video about") || clean.contains("video with title") || clean.contains("play that one")
        ) {
            val isSmartResult = clean.startsWith("first ") || clean.startsWith("second ") || clean.startsWith("third ") ||
                    clean.startsWith("1st ") || clean.startsWith("2nd ") || clean.startsWith("3rd ") ||
                    clean == "first result" || clean == "second result" || clean == "first one" || clean == "second one" ||
                    clean.startsWith("open the result") || clean.startsWith("play the video") || clean.startsWith("play that one") ||
                    clean.startsWith("open that one") || clean.startsWith("the video with") || clean.startsWith("the video about")

            if (isSmartResult) {
                return ClassifiedIntent.SmartResultSelection(query = clean)
            }
        }

        // 2. Check Contextual references first ("first one", "play it", "aur scroll karo")
        val contextual = CommandContext.resolveContextualCommand(clean)
        if (contextual !is ContextualResolution.None) {
            return ClassifiedIntent.Contextual(contextual)
        }

        // 3. Navigation Controls
        if (clean == "home" || clean == "go home" || clean == "home screen" || clean == "ghar" ||
            clean.contains("home jao") || clean.contains("home ja") || clean.contains("home screen ma ja")
        ) {
            return ClassifiedIntent.Navigation(NavigationAction.HOME)
        }

        if (clean == "back" || clean == "go back" || clean == "back jao" || clean == "piche jao" ||
            clean == "pachadi ja" || clean == "ferk" || clean == "back ja" || clean.contains("pachhadi")
        ) {
            return ClassifiedIntent.Navigation(NavigationAction.BACK)
        }

        if (clean == "recents" || clean == "recent apps" || clean == "recent tasks" ||
            clean.contains("recents kholo") || clean.contains("recents dekha") || clean.contains("recent apps dekha")
        ) {
            return ClassifiedIntent.Navigation(NavigationAction.RECENTS)
        }

        if (clean == "lock" || clean == "lock phone" || clean == "lock device" ||
            clean.contains("phone lock") || clean.contains("lock gara") || clean.contains("band gara screen")
        ) {
            return ClassifiedIntent.Navigation(NavigationAction.LOCK)
        }

        if (clean.contains("notification") || clean.contains("notifications kholo") || clean.contains("notifications dekha")) {
            return ClassifiedIntent.Navigation(NavigationAction.NOTIFICATIONS)
        }

        if (clean.contains("quick settings") || clean.contains("control center")) {
            return ClassifiedIntent.Navigation(NavigationAction.QUICK_SETTINGS)
        }

        // 4. Volume & Device Controls
        // "Volume 30 percent karo", "Volume 50 gara", "Set volume to 40"
        val volumePercentRegex = Regex("""(?:volume|aawaz|sound)\s*(?:to\s*)?(\d{1,3})\s*(?:percent|%|pratishat)?""")
        val volMatch = volumePercentRegex.find(clean)
        if (volMatch != null) {
            val percent = volMatch.groupValues[1].toIntOrNull()
            if (percent != null && percent in 0..100) {
                return ClassifiedIntent.DeviceControl(DeviceAction.SetVolume(percent))
            }
        }

        if (clean.contains("volume up") || clean.contains("aawaz badhao") || clean.contains("aawaz badha") ||
            clean.contains("thulo aawaz") || clean.contains("sound up")
        ) {
            return ClassifiedIntent.DeviceControl(DeviceAction.VolumeUp)
        }

        if (clean.contains("volume down") || clean.contains("aawaz kam karo") || clean.contains("aawaz ghatao") ||
            clean.contains("sano aawaz") || clean.contains("sound down")
        ) {
            return ClassifiedIntent.DeviceControl(DeviceAction.VolumeDown)
        }

        if (clean == "mute" || clean == "mute karo" || clean == "aawaz band karo" || clean == "shant gara") {
            return ClassifiedIntent.DeviceControl(DeviceAction.Mute)
        }

        if (clean == "unmute" || clean == "unmute karo" || clean == "aawaz kholo") {
            return ClassifiedIntent.DeviceControl(DeviceAction.Unmute)
        }

        if (clean.contains("screenshot") || clean.contains("screen shot") || clean.contains("capture screen") ||
            clean.contains("photo khich") || clean.contains("screenshot leu")
        ) {
            return ClassifiedIntent.DeviceControl(DeviceAction.TakeScreenshot)
        }

        if (clean.contains("torch on") || clean.contains("flashlight on") || clean.contains("light bala") || clean.contains("light jalao")) {
            return ClassifiedIntent.DeviceControl(DeviceAction.TorchOn)
        }

        if (clean.contains("torch off") || clean.contains("flashlight off") || clean.contains("light nibhao") || clean.contains("light band karo")) {
            return ClassifiedIntent.DeviceControl(DeviceAction.TorchOff)
        }

        // 5. Media Controls
        if (clean == "play music" || clean == "music chalao" || clean == "gana bajao" || clean == "geet baja") {
            return ClassifiedIntent.AppControl(appQuery = "music", open = true)
        }

        if (clean == "play" || clean == "resume" || clean == "chalao" || clean == "baja" || clean == "bajaide") {
            return ClassifiedIntent.MediaControl(MediaAction.PLAY)
        }

        if (clean.startsWith("play ") || clean.startsWith("chalao ") || clean.startsWith("bajao ") || clean.startsWith("baja ")) {
            val songQuery = clean.replace("play ", "")
                .replace("chalao ", "")
                .replace("bajao ", "")
                .replace("baja ", "")
                .trim()
            if (songQuery.isNotEmpty() && songQuery != "music") {
                return ClassifiedIntent.SearchAndExecute(app = "YouTube", query = songQuery, autoPlay = true)
            }
        }

        if (clean == "pause" || clean == "pause karo" || clean == "roko" || clean == "rok") {
            return ClassifiedIntent.MediaControl(MediaAction.PAUSE)
        }

        if (clean == "next" || clean == "next song" || clean == "agla gana" || clean == "aruko gana" || clean == "next track") {
            return ClassifiedIntent.MediaControl(MediaAction.NEXT)
        }

        if (clean == "previous" || clean == "previous song" || clean == "pichla gana" || clean == "pahiloko gana") {
            return ClassifiedIntent.MediaControl(MediaAction.PREVIOUS)
        }

        // 6. Scrolling and Swiping Gestures
        if (clean.contains("scroll down") || clean.contains("neeche scroll") || clean.contains("tala sar") ||
            clean.contains("tala scroll") || clean == "scroll" || clean == "down"
        ) {
            return ClassifiedIntent.ScreenGesture(GestureAction.Scroll(forward = true))
        }

        if (clean.contains("scroll up") || clean.contains("upar scroll") || clean.contains("mathi sar") ||
            clean.contains("mathi scroll") || clean == "up"
        ) {
            return ClassifiedIntent.ScreenGesture(GestureAction.Scroll(forward = false))
        }

        if (clean.startsWith("swipe ")) {
            val dir = clean.substringAfter("swipe ").trim()
            val validDir = when (dir) {
                "up", "down", "left", "right" -> dir.uppercase()
                "upar", "mathi" -> "UP"
                "neeche", "tala" -> "DOWN"
                "baye", "left" -> "LEFT"
                "daye", "right" -> "RIGHT"
                else -> "UP"
            }
            return ClassifiedIntent.ScreenGesture(GestureAction.Swipe(validDir))
        }

        // 7. Clear text
        if (clean == "clear text" || clean == "clear" || clean == "delete text" || clean == "hatao text" || clean == "metau") {
            return ClassifiedIntent.ScreenGesture(GestureAction.ClearText)
        }

        // 8. Double Tap
        if (clean.startsWith("double tap ") || clean.startsWith("double click ")) {
            val target = clean.substringAfter("double tap ").substringAfter("double click ").trim()
            return ClassifiedIntent.ScreenGesture(GestureAction.DoubleTap(label = target))
        }

        // 9. Long Press
        if (clean.startsWith("long press ") || clean.startsWith("hold ") || clean.contains("dabakar rakho") || clean.contains("thichirakha")) {
            val target = clean.substringAfter("long press ").substringAfter("hold ").replace("dabakar rakho", "").replace("thichirakha", "").trim()
            return ClassifiedIntent.ScreenGesture(GestureAction.LongPress(target))
        }

        // 10. Single Tap / Click
        if (clean.startsWith("tap ") || clean.startsWith("click ") || clean.contains("pe tap karo") || clean.contains("ma tap gara") ||
            clean.contains("pe click karo") || clean.contains("button dabao")
        ) {
            val target = clean.replace("pe tap karo", "")
                .replace("ma tap gara", "")
                .replace("pe click karo", "")
                .replace("button dabao", "")
                .replace("tap on", "")
                .replace("tap", "")
                .replace("click on", "")
                .replace("click", "")
                .replace("button", "")
                .replace("is", "")
                .replace("yo", "")
                .trim()
            if (target.isNotEmpty()) {
                return ClassifiedIntent.ScreenGesture(GestureAction.Tap(target))
            }
        }

        // 11. Single Type
        if (clean.startsWith("type ") || clean.startsWith("write ") || clean.startsWith("likho ") || clean.startsWith("lekha ")) {
            val text = clean.substringAfter("type ")
                .substringAfter("write ")
                .substringAfter("likho ")
                .substringAfter("lekha ")
                .trim()
            if (text.isNotEmpty()) {
                return ClassifiedIntent.ScreenGesture(GestureAction.Type(text))
            }
        }

        // 12. App Closing
        if (clean.contains("close app") || clean.contains("close this app") || clean.contains("app close karo") ||
            clean.contains("ye app close karo") || clean.contains("yo app banda gara") || clean.contains("app band karo")
        ) {
            return ClassifiedIntent.AppControl(appQuery = "", open = false)
        }

        // 13. App Launching (e.g. "Sigma YouTube kholo", "Open Spotify", "WhatsApp open gara", "Kholo Chrome")
        val isAppLaunch = clean.startsWith("open ") || clean.startsWith("launch ") || clean.startsWith("kholo ") ||
                clean.endsWith(" kholo") || clean.endsWith(" khol") || clean.endsWith(" open gara") ||
                clean.endsWith(" khola") || clean.endsWith(" launch gara")

        if (isAppLaunch && !clean.contains("search") && !clean.contains("play") && !clean.contains("aur") && !clean.contains("and")) {
            val appTarget = clean.replace("open ", "")
                .replace("launch ", "")
                .replace("kholo ", "")
                .replace(" kholo", "")
                .replace(" khol", "")
                .replace(" open gara", "")
                .replace(" khola", "")
                .replace(" launch gara", "")
                .replace("app", "")
                .trim()
            if (appTarget.isNotEmpty()) {
                return ClassifiedIntent.AppControl(appQuery = appTarget, open = true)
            }
        }

        // 14. Compound multi-step plans (e.g., "YouTube kholo, search Naruto, first result play karo aur volume 30 percent karo")
        if (clean.contains(",") || clean.contains(" and ") || clean.contains(" aur ") || clean.contains(" ani ") ||
            clean.contains(" then ") || clean.contains(" paxi ")
        ) {
            return ClassifiedIntent.CompoundMultiStep(raw)
        }

        // 15. Search commands (e.g., "Search Naruto", "YouTube search lo-fi", "Search Alan Walker and play")
        if (clean.startsWith("search ") || clean.startsWith("khojo ") || clean.startsWith("khoj ") ||
            clean.contains("search for") || clean.contains("search in")
        ) {
            val app = if (clean.contains("spotify")) "Spotify" else "YouTube"
            val query = clean.substringAfter("search for ")
                .substringAfter("search in ")
                .substringAfter("search ")
                .substringAfter("khojo ")
                .substringAfter("khoj ")
                .replace("in youtube", "")
                .replace("on youtube", "")
                .replace("in spotify", "")
                .replace("on spotify", "")
                .trim()
            return ClassifiedIntent.SearchAndExecute(app = app, query = query, autoPlay = false)
        }

        // 16. Fallback to AI Reasoning
        return ClassifiedIntent.AiReasoningRequired(raw)
    }
}
