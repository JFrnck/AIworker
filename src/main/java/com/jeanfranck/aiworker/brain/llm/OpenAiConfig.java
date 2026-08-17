package com.jeanfranck.aiworker.brain.llm;

/**
 * La API key nunca se hardcodea ni se commitea (CLAUDE.md) - se lee de la
 * variable de entorno OPENAI_API_KEY. Si no esta seteada, isConfigured()
 * devuelve false y el mod no debe intentar llamar a la API.
 */
public final class OpenAiConfig {
	private static final String DEFAULT_MODEL = "gpt-5.6-luna";
	private static final String API_KEY_ENV = "OPENAI_API_KEY";
	private static final String MODEL_ENV = "AIWORKER_OPENAI_MODEL";

	private final String apiKey;
	private final String model;

	private OpenAiConfig(String apiKey, String model) {
		this.apiKey = apiKey;
		this.model = model;
	}

	public static OpenAiConfig fromEnvironment() {
		String key = System.getenv(API_KEY_ENV);
		String model = System.getenv(MODEL_ENV);
		return new OpenAiConfig(key, model != null && !model.isBlank() ? model : DEFAULT_MODEL);
	}

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

	public String apiKey() {
		return apiKey;
	}

	public String model() {
		return model;
	}
}
