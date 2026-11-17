package com.example.domain.skills

import android.content.Context
import com.example.domain.model.AssistantAction
import com.example.domain.model.ParsedIntent
import com.example.domain.model.SkillResult

/**
 * Modular Skill contract so new commands/capabilities can be plugged into SkillRegistry cleanly.
 */
interface AssistantSkill {
  val skillId: String
  val supportedActions: Set<AssistantAction>

  suspend fun execute(context: Context, intent: ParsedIntent): SkillResult
}
