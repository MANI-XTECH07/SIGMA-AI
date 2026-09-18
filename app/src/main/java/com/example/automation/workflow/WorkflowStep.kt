package com.example.automation.workflow

import org.json.JSONArray
import org.json.JSONObject

enum class WorkflowActionType {
    APP_LAUNCH,
    TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    TYPE_TEXT,
    CLEAR_TEXT,
    SCROLL,
    SWIPE,
    DRAG,
    BACK,
    HOME,
    RECENTS,
    WAIT,
    MEDIA_CONTROL,
    VOLUME_CONTROL,
    VERIFY_ELEMENT,
    VERIFY_PLAYBACK
}

data class WorkflowStep(
    val actionType: WorkflowActionType,
    val targetText: String = "",
    val targetDescription: String = "",
    val targetResourceId: String = "",
    val targetClassName: String = "",
    val packageName: String = "",
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.5f,
    val textToType: String = "",
    val extraValue: String = "",
    val timeoutMs: Long = 3000L,
    val description: String = ""
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("actionType", actionType.name)
        json.put("targetText", targetText)
        json.put("targetDescription", targetDescription)
        json.put("targetResourceId", targetResourceId)
        json.put("targetClassName", targetClassName)
        json.put("packageName", packageName)
        json.put("xRatio", xRatio.toDouble())
        json.put("yRatio", yRatio.toDouble())
        json.put("textToType", textToType)
        json.put("extraValue", extraValue)
        json.put("timeoutMs", timeoutMs)
        json.put("description", description)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): WorkflowStep {
            val actionTypeStr = json.optString("actionType", WorkflowActionType.TAP.name)
            val actionType = try {
                WorkflowActionType.valueOf(actionTypeStr)
            } catch (e: Exception) {
                WorkflowActionType.TAP
            }
            return WorkflowStep(
                actionType = actionType,
                targetText = json.optString("targetText", ""),
                targetDescription = json.optString("targetDescription", ""),
                targetResourceId = json.optString("targetResourceId", ""),
                targetClassName = json.optString("targetClassName", ""),
                packageName = json.optString("packageName", ""),
                xRatio = json.optDouble("xRatio", 0.5).toFloat(),
                yRatio = json.optDouble("yRatio", 0.5).toFloat(),
                textToType = json.optString("textToType", ""),
                extraValue = json.optString("extraValue", ""),
                timeoutMs = json.optLong("timeoutMs", 3000L),
                description = json.optString("description", "")
            )
        }

        fun listToJson(steps: List<WorkflowStep>): String {
            val array = JSONArray()
            for (step in steps) {
                array.put(step.toJson())
            }
            return array.toString()
        }

        fun listFromJson(jsonStr: String): List<WorkflowStep> {
            if (jsonStr.isBlank()) return emptyList()
            val list = mutableListOf<WorkflowStep>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(fromJson(obj))
                }
            } catch (e: Exception) {
                // Fallback for empty or malformed json
            }
            return list
        }
    }
}
