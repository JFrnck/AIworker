package com.jeanfranck.aiworker.brain.llm;

import com.jeanfranck.aiworker.body.AIWorkerEntity;
import com.jeanfranck.aiworker.body.action.BotAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.function.Function;

/**
 * Whitelist explicito, no blacklist (CLAUDE.md): solo estos 5 action_type
 * son validos, cualquier otra cosa se rechaza. Nunca se ejecuta una accion
 * sin validar tipo y rango de parametros primero - coordenadas a distancia
 * razonable, target existente y cercano.
 */
public final class DecisionValidator {
	private static final double MAX_DISTANCE_SQR = 32.0 * 32.0;
	private static final int MAX_MESSAGE_LENGTH = 256;

	private DecisionValidator() {
	}

	public sealed interface Result {
		record Valid(BotAction action) implements Result {
		}

		record Idle() implements Result {
		}

		record Rejected(String reason) implements Result {
		}
	}

	public static Result validate(AIWorkerEntity bot, ServerLevel level, LlmDecision decision) {
		if (decision == null || decision.actionType() == null) {
			return new Result.Rejected("respuesta vacia o sin action_type");
		}

		return switch (decision.actionType()) {
			case "idle" -> new Result.Idle();
			case "move_to" -> validatePosition(bot, decision, BotAction.MoveTo::new);
			case "mine_block" -> validatePosition(bot, decision, BotAction.MineBlock::new);
			case "attack" -> validateAttack(bot, level, decision);
			case "follow" -> validateFollow(bot, level, decision);
			case "say" -> validateSay(decision);
			default -> new Result.Rejected("action_type desconocida: " + decision.actionType());
		};
	}

	private static Result validatePosition(AIWorkerEntity bot, LlmDecision decision, Function<BlockPos, BotAction> factory) {
		if (decision.x() == null || decision.y() == null || decision.z() == null) {
			return new Result.Rejected("faltan coordenadas x/y/z");
		}
		BlockPos pos = new BlockPos(decision.x(), decision.y(), decision.z());
		double distSqr = bot.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		if (distSqr > MAX_DISTANCE_SQR) {
			return new Result.Rejected("coordenadas muy lejos (" + String.format("%.1f", Math.sqrt(distSqr)) + " bloques)");
		}
		return new Result.Valid(factory.apply(pos));
	}

	private static Result validateAttack(AIWorkerEntity bot, ServerLevel level, LlmDecision decision) {
		if (decision.targetEntityId() == null) {
			return new Result.Rejected("falta target_entity_id");
		}
		Entity target = level.getEntity(decision.targetEntityId());
		if (target == null || !target.isAlive()) {
			return new Result.Rejected("target_entity_id no existe o no esta vivo");
		}
		if (bot.distanceToSqr(target) > MAX_DISTANCE_SQR) {
			return new Result.Rejected("el objetivo esta muy lejos");
		}
		return new Result.Valid(new BotAction.Attack(decision.targetEntityId()));
	}

	private static Result validateFollow(AIWorkerEntity bot, ServerLevel level, LlmDecision decision) {
		if (decision.targetEntityId() == null) {
			return new Result.Rejected("falta target_entity_id");
		}
		Entity target = level.getEntity(decision.targetEntityId());
		if (target == null || !target.isAlive()) {
			return new Result.Rejected("target_entity_id no existe o no esta vivo");
		}
		if (bot.distanceToSqr(target) > MAX_DISTANCE_SQR) {
			return new Result.Rejected("el objetivo esta muy lejos para empezar a seguirlo");
		}
		return new Result.Valid(new BotAction.Follow(decision.targetEntityId()));
	}

	private static Result validateSay(LlmDecision decision) {
		if (decision.message() == null || decision.message().isBlank()) {
			return new Result.Rejected("mensaje vacio");
		}
		if (decision.message().length() > MAX_MESSAGE_LENGTH) {
			return new Result.Rejected("mensaje muy largo (" + decision.message().length() + " caracteres)");
		}
		return new Result.Valid(new BotAction.Say(decision.message()));
	}
}
