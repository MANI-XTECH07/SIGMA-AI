package com.example

import android.graphics.Bitmap
import com.example.ai.ActionPlanner
import com.example.ai.AiActionPlan
import com.example.ai.AiProvider
import com.example.automation.AutomationStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActionPlannerTest {

    private val fakeAiProvider = object : AiProvider {
        override suspend fun generateText(prompt: String, systemInstruction: String?): Result<String> =
            Result.success("{}")

        override suspend fun analyzeImage(bitmap: Bitmap, prompt: String): Result<String> =
            Result.success("Analysis")

        override suspend fun planAction(userQuery: String, screenContext: String?): Result<AiActionPlan> =
            Result.success(AiActionPlan("GENERAL", "", emptyMap(), emptyList(), false, 1.0f))
    }

    private val planner = ActionPlanner(fakeAiProvider)

    @Test
    fun testLockCommandFastPath() = runBlocking {
        val plan = planner.planUserCommand("Sigma phone lock karo")
        assertEquals("Lock Phone", plan.title)
        assertTrue(plan.steps.first() is AutomationStep.LockDevice)
    }

    @Test
    fun testScrollDownFastPath() = runBlocking {
        val plan = planner.planUserCommand("Sigma neeche scroll karo")
        assertEquals("Scroll Down", plan.title)
        val step = plan.steps.first() as AutomationStep.Scroll
        assertTrue(step.forward)
    }

    @Test
    fun testChainedYouTubeSearchAndPlay() = runBlocking {
        val plan = planner.planUserCommand("Open YouTube, search for lo-fi beats, and play it")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("lo-fi beats", plan.query)
        assertEquals(9, plan.steps.size)
        assertTrue(plan.steps[0] is AutomationStep.LaunchApp)
        assertTrue(plan.steps[1] is AutomationStep.FindAndTapSearch)
        assertTrue(plan.steps[2] is AutomationStep.TypeText)
        assertEquals("lo-fi beats", (plan.steps[2] as AutomationStep.TypeText).textToType)
        assertTrue(plan.steps[3] is AutomationStep.SubmitSearch)
        assertTrue(plan.steps[6] is AutomationStep.SelectResult)
        assertTrue(plan.steps[7] is AutomationStep.PlayMedia)
        assertTrue(plan.steps[8] is AutomationStep.VerifyPlayback)
    }

    @Test
    fun testSearchAlanWalkerFadedAndPlayIt() = runBlocking {
        val plan = planner.planUserCommand("search Alan Walker Faded and play it")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("Alan Walker Faded", plan.query)
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
        assertTrue(plan.steps.any { it is AutomationStep.VerifyPlayback })
    }

    @Test
    fun testSearchQueryThenPlay() = runBlocking {
        val plan = planner.planUserCommand("search shape of you then play")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("shape of you", plan.query)
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
    }

    @Test
    fun testFindSongAndPlay() = runBlocking {
        val plan = planner.planUserCommand("find despacito and play")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("despacito", plan.query)
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
    }

    @Test
    fun testSearchYouTubeForQueryAndPlayFirstResult() = runBlocking {
        val plan = planner.planUserCommand("search YouTube for coldplay yellow and play the first result")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("coldplay yellow", plan.query)
        val selectStep = plan.steps.filterIsInstance<AutomationStep.SelectResult>().firstOrNull()
        assertTrue(selectStep != null)
        assertEquals("coldplay yellow", selectStep?.query)
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
    }

    @Test
    fun testOpenYouTubeSearchAndPlayIt() = runBlocking {
        val plan = planner.planUserCommand("open YouTube, search Alan Walker Faded and play it")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("Alan Walker Faded", plan.query)
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
    }

    @Test
    fun testSearchXAndPlayTheFirstResult() = runBlocking {
        val plan = planner.planUserCommand("search interstellar theme and play the first result")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("interstellar theme", plan.query)
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
    }

    @Test
    fun testSearchXAloneDoesNotAttemptResultPlay() = runBlocking {
        val plan = planner.planUserCommand("search Alan Walker Faded")
        assertEquals("SEARCH_ONLY", plan.intent)
        assertEquals("Alan Walker Faded", plan.query)
        assertFalse("Search alone must not have SelectResult step", plan.steps.any { it is AutomationStep.SelectResult })
        assertFalse("Search alone must not have PlayMedia step", plan.steps.any { it is AutomationStep.PlayMedia })
        assertFalse("Search alone must not have VerifyPlayback step", plan.steps.any { it is AutomationStep.VerifyPlayback })
    }

    @Test
    fun testSettingsNavigation() = runBlocking {
        val plan = planner.planUserCommand("Go to settings and open Wi-Fi")
        assertEquals("Settings > Wi-Fi", plan.title)
        assertEquals(3, plan.steps.size)
        assertTrue(plan.steps[0] is AutomationStep.LaunchApp)
        assertEquals("Settings", (plan.steps[0] as AutomationStep.LaunchApp).appQuery)
    }

    @Test
    fun testTapAndType() = runBlocking {
        val plan = planner.planUserCommand("Tap search and type Naruto")
        assertEquals("Tap & Type", plan.title)
        assertEquals(4, plan.steps.size)
        assertTrue(plan.steps[0] is AutomationStep.FindAndTap)
        assertTrue(plan.steps[2] is AutomationStep.TypeText)
        assertEquals("Naruto", (plan.steps[2] as AutomationStep.TypeText).textToType)
    }

    @Test
    fun testPlayMusicCommands() = runBlocking {
        val plan = planner.planUserCommand("play music")
        assertEquals("Play Music", plan.title)
        assertTrue(plan.steps[0] is AutomationStep.LaunchApp)
        assertEquals("music", (plan.steps[0] as AutomationStep.LaunchApp).appQuery)
    }

    @Test
    fun testPlaySongDirectCommand() = runBlocking {
        val plan = planner.planUserCommand("play Bohemian Rhapsody")
        assertEquals("YouTube: Bohemian Rhapsody", plan.title)
        assertEquals("PLAY_MEDIA", plan.intent)
        assertTrue(plan.steps[0] is AutomationStep.LaunchApp)
        assertEquals("YouTube", (plan.steps[0] as AutomationStep.LaunchApp).appQuery)
        assertTrue(plan.steps[2] is AutomationStep.TypeText)
        assertEquals("Bohemian Rhapsody", (plan.steps[2] as AutomationStep.TypeText).textToType)
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
        assertTrue(plan.steps.any { it is AutomationStep.VerifyPlayback })
    }
}
