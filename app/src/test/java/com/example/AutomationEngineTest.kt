package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.apps.AppResolver
import com.example.apps.InstalledAppRepository
import com.example.automation.ActionExecutor
import com.example.automation.ActionVerifier
import com.example.automation.AutomationEngine
import com.example.automation.AutomationPlan
import com.example.automation.AutomationState
import com.example.automation.AutomationStep
import com.example.automation.RecoveryEngine
import com.example.automation.ScreenObserver
import com.example.device.DeviceController
import com.example.device.LockController
import com.example.screen.ScreenAnalysisManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutomationEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val appRepository = InstalledAppRepository(context)
    private val appResolver = AppResolver(context, appRepository)
    private val lockController = LockController(context)
    private val screenAnalysisManager = ScreenAnalysisManager()
    private val screenObserver = ScreenObserver(screenAnalysisManager)
    private val actionExecutor = ActionExecutor(lockController)
    private val actionVerifier = ActionVerifier(screenObserver)
    private val recoveryEngine = RecoveryEngine(actionExecutor, screenObserver)
    private val deviceController = DeviceController(context)

    private val engine = AutomationEngine(
        appResolver,
        actionExecutor,
        screenObserver,
        actionVerifier,
        recoveryEngine,
        deviceController
    )

    @Test
    fun testAccessibilityDisconnectedFailsFastWithoutFakeSuccess() = runBlocking {
        // When accessibility service is not connected, AutomationEngine must report failure truthfully
        val plan = AutomationPlan(
            title = "YouTube: Alan Walker Faded",
            steps = listOf(
                AutomationStep.LaunchApp("YouTube"),
                AutomationStep.FindAndTapSearch(),
                AutomationStep.TypeText("Alan Walker Faded"),
                AutomationStep.SubmitSearch(),
                AutomationStep.Wait(1200L),
                AutomationStep.ObserveScreen(),
                AutomationStep.SelectResult("Alan Walker Faded"),
                AutomationStep.PlayMedia(),
                AutomationStep.VerifyPlayback()
            ),
            intent = "PLAY_MEDIA",
            query = "Alan Walker Faded",
            command = "search Alan Walker Faded and play it"
        )

        val report = engine.executePlanWithReport(plan) { }
        assertFalse("Report must not claim success when accessibility service is disconnected", report.success)
        assertEquals(AutomationState.FAILED, report.state)
        assertEquals("Accessibility permission is required for screen control.", report.finalMessage)
        assertNotNull(report.failedStep)
    }
}
