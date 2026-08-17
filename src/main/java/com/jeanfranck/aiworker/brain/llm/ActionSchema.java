package com.jeanfranck.aiworker.brain.llm;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * JSON Schema estricto que le mandamos a la Responses API de OpenAI
 * (text.format.schema, con strict:true). En modo estricto, OpenAI exige
 * que TODOS los campos esten en "required" - los que no aplican segun la
 * accion elegida van tipados como nullable ("type": [T, "null"]) en vez de
 * quedar ausentes.
 */
public final class ActionSchema {
	public static final JsonElement SCHEMA = JsonParser.parseString("""
			{
			  "type": "object",
			  "properties": {
			    "action_type": {
			      "type": "string",
			      "enum": ["move_to", "mine_block", "attack", "follow", "say", "idle"]
			    },
			    "x": {"type": ["integer", "null"]},
			    "y": {"type": ["integer", "null"]},
			    "z": {"type": ["integer", "null"]},
			    "target_entity_id": {"type": ["integer", "null"]},
			    "message": {"type": ["string", "null"]},
			    "plan": {"type": "string"}
			  },
			  "required": ["action_type", "x", "y", "z", "target_entity_id", "message", "plan"],
			  "additionalProperties": false
			}
			""");

	private ActionSchema() {
	}
}
