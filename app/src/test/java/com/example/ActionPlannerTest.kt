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
    fun testDirectAppLaunch() = runBlocking {
        val plan = planner.planUserCommand("Sigma YouTube kholo")
        assertEquals("Open youtube", plan.title)
        val step = plan.steps.first() as AutomationStep.LaunchApp
        assertEquals("youtube", step.appQuery)
    }
}
