package com.jeanfranck.aiworker.body.action;

import net.minecraft.core.BlockPos;

/**
 * Una accion que el cuerpo del bot puede ejecutar. Un bot tiene a lo sumo
 * una accion en curso a la vez, consumida tick a tick. El shape de estos
 * records va a coincidir con el schema JSON que valide la salida del LLM
 * en fases posteriores.
 */
public sealed interface BotAction {
	record MoveTo(BlockPos pos) implements BotAction {
	}

	record MineBlock(BlockPos pos) implements BotAction {
	}

	record Attack(int targetEntityId) implements BotAction {
	}

	record Say(String message) implements BotAction {
	}

	/** Persecucion continua tick a tick, a diferencia de MoveTo que apunta a un punto fijo. */
	record Follow(int targetEntityId) implements BotAction {
	}
}
