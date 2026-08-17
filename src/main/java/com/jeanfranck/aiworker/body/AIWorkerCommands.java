package com.jeanfranck.aiworker.body;

import com.google.gson.Gson;
import com.jeanfranck.aiworker.AIWorkerEntities;
import com.jeanfranck.aiworker.body.action.BotAction;
import com.jeanfranck.aiworker.brain.WorldSnapshot;
import com.jeanfranck.aiworker.brain.WorldSnapshotCollector;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Comandos de prueba para operar el bot a mano, sin depender todavia del
 * decision loop / cliente LLM (fases posteriores). Todas las acciones que
 * no sean "spawn" se le mandan al bot AIWorkerEntity mas cercano a quien
 * ejecuta el comando - todavia no hay seleccion por id.
 */
public final class AIWorkerCommands {
	private static final double SEARCH_RADIUS = 32.0;
	private static final Gson GSON = new Gson();

	private AIWorkerCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("aiworker")
						.then(Commands.literal("spawn")
								.executes(AIWorkerCommands::spawn))
						.then(Commands.literal("move")
								.then(Commands.argument("pos", BlockPosArgument.blockPos())
										.executes(AIWorkerCommands::move)))
						.then(Commands.literal("mine")
								.then(Commands.argument("pos", BlockPosArgument.blockPos())
										.executes(AIWorkerCommands::mine)))
						.then(Commands.literal("attack")
								.then(Commands.argument("target", EntityArgument.entity())
										.executes(AIWorkerCommands::attack)))
						.then(Commands.literal("say")
								.then(Commands.argument("message", StringArgumentType.greedyString())
										.executes(AIWorkerCommands::say)))
						.then(Commands.literal("snapshot")
								.executes(AIWorkerCommands::snapshot))));
	}

	private static int spawn(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		Vec3 pos = source.getPosition();

		AIWorkerEntity worker = AIWorkerEntities.WORKER.create(level, EntitySpawnReason.COMMAND);
		if (worker == null) {
			source.sendFailure(Component.literal("No se pudo crear la entidad aiworker."));
			return 0;
		}

		worker.setPos(pos.x, pos.y, pos.z);
		if (!level.addFreshEntity(worker)) {
			source.sendFailure(Component.literal("No se pudo agregar el bot al mundo (chunk no cargado?)."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Bot spawneado."), true);
		return 1;
	}

	private static int move(CommandContext<CommandSourceStack> context) {
		BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
		return dispatch(context, worker -> new BotAction.MoveTo(pos), "moviendose a " + pos.toShortString());
	}

	private static int mine(CommandContext<CommandSourceStack> context) {
		BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
		return dispatch(context, worker -> new BotAction.MineBlock(pos), "minando " + pos.toShortString());
	}

	private static int attack(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Entity target = EntityArgument.getEntity(context, "target");
		return dispatch(context, worker -> new BotAction.Attack(target.getId()), "atacando a " + target.getName().getString());
	}

	private static int say(CommandContext<CommandSourceStack> context) {
		String message = StringArgumentType.getString(context, "message");
		return dispatch(context, worker -> new BotAction.Say(message), "diciendo algo");
	}

	private static int snapshot(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();

		AIWorkerEntity worker = nearestWorker(level, source.getPosition());
		if (worker == null) {
			source.sendFailure(Component.literal("No hay ningun aiworker cerca (radio " + (int) SEARCH_RADIUS + " bloques)."));
			return 0;
		}

		WorldSnapshot snapshot = WorldSnapshotCollector.collect(worker, level);
		String json = GSON.toJson(snapshot);
		source.sendSuccess(() -> Component.literal(json), false);
		return 1;
	}

	private interface ActionFactory {
		BotAction create(AIWorkerEntity worker);
	}

	private static int dispatch(CommandContext<CommandSourceStack> context, ActionFactory actionFactory, String description) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();

		AIWorkerEntity worker = nearestWorker(level, source.getPosition());
		if (worker == null) {
			source.sendFailure(Component.literal("No hay ningun aiworker cerca (radio " + (int) SEARCH_RADIUS + " bloques)."));
			return 0;
		}

		String failureReason = worker.setAction(level, actionFactory.create(worker));
		if (failureReason != null) {
			source.sendFailure(Component.literal("No se pudo: " + failureReason));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Bot " + description + "."), true);
		return 1;
	}

	private static AIWorkerEntity nearestWorker(ServerLevel level, Vec3 origin) {
		AABB area = new AABB(
				origin.x - SEARCH_RADIUS, origin.y - SEARCH_RADIUS, origin.z - SEARCH_RADIUS,
				origin.x + SEARCH_RADIUS, origin.y + SEARCH_RADIUS, origin.z + SEARCH_RADIUS);
		List<AIWorkerEntity> nearby = level.getEntitiesOfClass(AIWorkerEntity.class, area);
		return nearby.stream()
				.min(Comparator.comparingDouble(worker -> worker.distanceToSqr(origin)))
				.orElse(null);
	}
}
