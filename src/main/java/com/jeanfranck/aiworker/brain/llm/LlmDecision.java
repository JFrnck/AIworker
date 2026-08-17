package com.jeanfranck.aiworker.brain.llm;

import com.google.gson.annotations.SerializedName;

/**
 * Mapeo 1:1 del JSON que devuelve la API segun ActionSchema. Es la
 * respuesta cruda, todavia sin validar - DecisionValidator la convierte
 * (o la rechaza) antes de que toque el mundo.
 */
public record LlmDecision(
		@SerializedName("action_type") String actionType,
		Integer x,
		Integer y,
		Integer z,
		@SerializedName("target_entity_id") Integer targetEntityId,
		String message,
		String plan
) {
}
