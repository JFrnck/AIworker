package com.jeanfranck.aiworker.brain.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jeanfranck.aiworker.brain.WorldSnapshot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente async contra la Responses API de OpenAI. Nunca se usa de forma
 * sincronica (CLAUDE.md) - decide() siempre devuelve un CompletableFuture,
 * nunca bloquea el hilo que lo llama. Quien consuma el resultado es
 * responsable de reintegrarlo al hilo principal del server con
 * server.execute() antes de tocar el mundo.
 */
public final class OpenAiClient {
	private static final String ENDPOINT = "https://api.openai.com/v1/responses";
	private static final Duration TIMEOUT = Duration.ofSeconds(15);
	private static final Gson GSON = new Gson();

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.build();

	private final OpenAiConfig config;

	public OpenAiClient(OpenAiConfig config) {
		this.config = config;
	}

	public CompletableFuture<LlmDecision> decide(String systemPrompt, WorldSnapshot snapshot) {
		if (!config.isConfigured()) {
			return CompletableFuture.failedFuture(new LlmException("OPENAI_API_KEY no esta configurada"));
		}

		HttpRequest request;
		try {
			request = HttpRequest.newBuilder()
					.uri(URI.create(ENDPOINT))
					.timeout(TIMEOUT)
					.header("Content-Type", "application/json")
					.header("Authorization", "Bearer " + config.apiKey())
					.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(buildRequestBody(systemPrompt, snapshot))))
					.build();
		} catch (RuntimeException e) {
			return CompletableFuture.failedFuture(new LlmException("No se pudo armar el request", e));
		}

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(OpenAiClient::parseResponse);
	}

	private JsonObject buildRequestBody(String systemPrompt, WorldSnapshot snapshot) {
		JsonObject root = new JsonObject();
		root.addProperty("model", config.model());

		JsonArray input = new JsonArray();
		input.add(message("system", systemPrompt));
		input.add(message("user", GSON.toJson(snapshot)));
		root.add("input", input);

		root.add("text", textFormat());
		root.add("reasoning", reasoning());
		return root;
	}

	private static JsonObject reasoning() {
		// "none": elegir 1 de 5 acciones sobre un snapshot ya armado no
		// necesita razonamiento profundo - reduce latencia y costo (la
		// guia oficial de OpenAI lo recomienda para tareas latency-critical).
		JsonObject reasoning = new JsonObject();
		reasoning.addProperty("effort", "none");
		return reasoning;
	}

	private static JsonObject message(String role, String content) {
		JsonObject message = new JsonObject();
		message.addProperty("role", role);
		message.addProperty("content", content);
		return message;
	}

	private static JsonObject textFormat() {
		JsonObject format = new JsonObject();
		format.addProperty("type", "json_schema");
		format.addProperty("name", "bot_decision");
		format.addProperty("strict", true);
		format.add("schema", ActionSchema.SCHEMA);

		JsonObject text = new JsonObject();
		text.add("format", format);
		return text;
	}

	private static LlmDecision parseResponse(HttpResponse<String> response) {
		if (response.statusCode() != 200) {
			throw new LlmException("OpenAI respondio " + response.statusCode() + ": " + truncate(response.body()));
		}

		JsonObject root;
		try {
			root = GSON.fromJson(response.body(), JsonObject.class);
		} catch (RuntimeException e) {
			throw new LlmException("Respuesta de OpenAI no es JSON valido", e);
		}

		String outputText = extractOutputText(root);
		if (outputText == null) {
			throw new LlmException("Respuesta de OpenAI sin output_text: " + truncate(response.body()));
		}

		try {
			return GSON.fromJson(outputText, LlmDecision.class);
		} catch (RuntimeException e) {
			throw new LlmException("La decision del LLM no matchea el schema esperado", e);
		}
	}

	private static String extractOutputText(JsonObject root) {
		// output_text es una comodidad que agregan algunos SDKs oficiales -
		// no viene en el JSON crudo de la API. El array "output" real trae
		// varios items (ej. "reasoning" con contenido vacio/encriptado antes
		// del "message" con la respuesta) - hay que buscar el que
		// corresponde, nunca asumir que es el primero.
		if (root.has("output_text") && !root.get("output_text").isJsonNull()) {
			return root.get("output_text").getAsString();
		}
		if (!root.has("output") || !root.get("output").isJsonArray()) {
			return null;
		}
		for (JsonElement outputElement : root.getAsJsonArray("output")) {
			if (!outputElement.isJsonObject()) {
				continue;
			}
			JsonObject outputItem = outputElement.getAsJsonObject();
			if (!"message".equals(stringOrNull(outputItem, "type")) || !outputItem.has("content")
					|| !outputItem.get("content").isJsonArray()) {
				continue;
			}
			for (JsonElement contentElement : outputItem.getAsJsonArray("content")) {
				if (!contentElement.isJsonObject()) {
					continue;
				}
				JsonObject contentItem = contentElement.getAsJsonObject();
				if ("output_text".equals(stringOrNull(contentItem, "type")) && contentItem.has("text")) {
					return contentItem.get("text").getAsString();
				}
			}
		}
		return null;
	}

	private static String stringOrNull(JsonObject obj, String key) {
		return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
	}

	private static String truncate(String text) {
		return text.length() > 500 ? text.substring(0, 500) + "..." : text;
	}
}
