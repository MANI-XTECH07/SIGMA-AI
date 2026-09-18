package com.example

import android.graphics.Bitmap
import com.example.ai.ActionPlanner
import com.example.ai.AiActionPlan
import com.example.ai.AiProvider
import com.example.automation.AutomationStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals("YouTube Search & Play", plan.title)
        assertEquals(6, plan.steps.size)
        assertTrue(plan.steps[0] is AutomationStep.LaunchApp)
        assertTrue(plan.steps[2] is AutomationStep.FindAndTapSearch)
        assertTrue(plan.steps[3] is AutomationStep.TypeText)
        assertEquals("lo-fi beats", (plan.steps[3] as AutomationStep.TypeText).textToType)
        assertTrue(plan.steps[5] is AutomationStep.FindAndTapResult)
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
        assertEquals(3, plan.steps.size)
        assertTrue(plan.steps[0] is AutomationStep.FindAndTap)
        assertTrue(plan.steps[2] is AutomationStep.TypeText)
        assertEquals("Naruto", (plan.steps[2] as AutomationStep.TypeText).textToType)
    }
}
