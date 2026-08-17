package com.jeanfranck.aiworker.brain;

import com.jeanfranck.aiworker.AIWorkerMod;
import com.jeanfranck.aiworker.body.AIWorkerEntity;
import com.jeanfranck.aiworker.body.action.BotAction;
import com.jeanfranck.aiworker.brain.llm.DecisionValidator;
import com.jeanfranck.aiworker.brain.llm.LlmDecision;
import com.jeanfranck.aiworker.brain.llm.OpenAiClient;
import com.jeanfranck.aiworker.brain.llm.OpenAiConfig;
import com.jeanfranck.aiworker.brain.llm.SystemPrompt;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El loop de decision, event-driven en vez de polling ciego cada N
 * segundos: cada tick del server revisa que bots en modo automatico estan
 * idle (sin accion en curso) o trabados (accion en curso hace demasiados
 * ticks), y solo a esos les dispara un ciclo de decision nuevo. Nunca hay
 * mas de una decision en vuelo por bot a la vez.
 *
 * La llamada HTTP es siempre async (OpenAiClient.decide()); el resultado
 * se reintegra al hilo principal con server.execute() antes de tocar el
 * bot o el mundo (CLAUDE.md).
 */
public final class DecisionScheduler {
	private static final long STUCK_TIMEOUT_TICKS = 200L; // 10s

	private static final Set<Integer> AUTO_ENABLED = ConcurrentHashMap.newKeySet();
	private static final Set<Integer> IN_FLIGHT = ConcurrentHashMap.newKeySet();

	private static OpenAiClient client;
	private static OpenAiConfig config;

	private DecisionScheduler() {
	}

	public static void register() {
		config = OpenAiConfig.fromEnvironment();
		client = new OpenAiClient(config);
		ServerTickEvents.END_SERVER_TICK.register(DecisionScheduler::onTick);
	}

	public static void setAuto(AIWorkerEntity bot, boolean enabled) {
		if (enabled) {
			AUTO_ENABLED.add(bot.getId());
		} else {
			AUTO_ENABLED.remove(bot.getId());
		}
	}

	public static boolean isAuto(AIWorkerEntity bot) {
		return AUTO_ENABLED.contains(bot.getId());
	}

	public static boolean isConfigured() {
		return config != null && config.isConfigured();
	}

	/** Dispara un ciclo de decision ya mismo, salteando el chequeo de idle/trabado (para /aiworker think). */
	public static boolean triggerOnce(AIWorkerEntity bot, ServerLevel level) {
		return dispatch(bot, level);
	}

	private static void onTick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (!(entity instanceof AIWorkerEntity bot) || !isAuto(bot)) {
					continue;
				}
				boolean idle = bot.currentAction() == null;
				boolean stuck = bot.ticksOnCurrentAction(level.getGameTime()) > STUCK_TIMEOUT_TICKS;
				if (idle || stuck) {
					dispatch(bot, level);
				}
			}
		}
	}

	private static boolean dispatch(AIWorkerEntity bot, ServerLevel level) {
		int id = bot.getId();
		if (!IN_FLIGHT.add(id)) {
			return false;
		}

		WorldSnapshot snapshot = WorldSnapshotCollector.collect(bot, level);
		MinecraftServer server = level.getServer();

		client.decide(SystemPrompt.TEXT, snapshot).whenComplete((decision, error) -> server.execute(() -> {
			IN_FLIGHT.remove(id);
			if (!bot.isAlive()) {
				return;
			}
			if (error != null) {
				AIWorkerMod.LOGGER.warn("[aiworker] bot {} - fallo la decision: {}", id, error.getMessage());
				return;
			}
			applyDecision(level, bot, decision);
		}));
		return true;
	}

	private static void applyDecision(ServerLevel level, AIWorkerEntity bot, LlmDecision decision) {
		if (decision != null && decision.plan() != null) {
			bot.memory().setPlan(decision.plan());
		}

		DecisionValidator.Result result = DecisionValidator.validate(bot, level, decision);
		if (result instanceof DecisionValidator.Result.Rejected rejected) {
			AIWorkerMod.LOGGER.info("[aiworker] bot {} - decision rechazada: {}", bot.getId(), rejected.reason());
			return;
		}
		if (result instanceof DecisionValidator.Result.Idle) {
			AIWorkerMod.LOGGER.info("[aiworker] bot {} - idle", bot.getId());
			return;
		}
		if (result instanceof DecisionValidator.Result.Valid valid) {
			BotAction action = valid.action();
			String failure = bot.setAction(level, action);
			if (failure != null) {
				AIWorkerMod.LOGGER.info("[aiworker] bot {} - accion rechazada por el cuerpo: {}", bot.getId(), failure);
			} else {
				AIWorkerMod.LOGGER.info("[aiworker] bot {} - accion aplicada: {}", bot.getId(), action);
			}
		}
	}
}
