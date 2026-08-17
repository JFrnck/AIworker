package com.jeanfranck.aiworker.body;

import com.jeanfranck.aiworker.body.action.BotAction;
import com.jeanfranck.aiworker.brain.BotMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * El "cuerpo" del bot: la entidad que existe en el mundo. No decide nada por
 * si misma - no se registran goals vanilla (registerGoals() queda sin
 * sobreescribir) para que no compita con las acciones que va a mandar el LLM
 * en fases posteriores. customServerAiStep() es el hook oficial de Mob para
 * IA custom sin goal selector, y corre solo del lado del server.
 */
public class AIWorkerEntity extends PathfinderMob {
	private static final double MOVE_SPEED = 1.0D;
	private static final double MINE_REACH_SQR = 5.0 * 5.0;
	private static final double ATTACK_RANGE_SQR = 2.5 * 2.5;
	private static final double ATTACK_GIVE_UP_RANGE_SQR = 32.0 * 32.0;
	private static final int ATTACK_COOLDOWN_TICKS = 20;
	private static final double FOLLOW_STOP_DISTANCE_SQR = 3.0 * 3.0;
	private static final double FOLLOW_GIVE_UP_RANGE_SQR = 48.0 * 48.0;

	private final BotMemory memory = new BotMemory();

	private BotAction currentAction;
	private long currentActionStartTick;
	private int mineTicksElapsed;
	private int mineTicksRequired;
	private int attackCooldown;

	public AIWorkerEntity(EntityType<? extends AIWorkerEntity> entityType, Level level) {
		super(entityType, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.ATTACK_DAMAGE, 2.0D)
				.add(Attributes.ATTACK_KNOCKBACK);
	}

	public BotAction currentAction() {
		return currentAction;
	}

	public BotMemory memory() {
		return memory;
	}

	/**
	 * Ticks transcurridos desde que arranco la accion actual - lo usa
	 * DecisionScheduler para detectar acciones trabadas y forzar una
	 * decision nueva. 0 si no hay accion en curso.
	 */
	public long ticksOnCurrentAction(long currentGameTime) {
		return currentAction == null ? 0 : currentGameTime - currentActionStartTick;
	}

	/**
	 * Arranca una accion nueva, reemplazando la que este en curso (si habia
	 * una accion de minado a medio terminar, se limpia el overlay de rotura).
	 * Devuelve null si arranco bien, o un motivo legible si la rechazo -
	 * nunca falla en silencio. Cada intento (rechazado o no) queda anotado
	 * en la memoria de corto plazo.
	 */
	public String setAction(ServerLevel level, BotAction action) {
		abandonCurrentAction(level);

		if (action instanceof BotAction.Say say) {
			say(level, say.message());
			memory.addRecord(describe(action), "enviado", level.getGameTime());
			return null;
		}

		if (action instanceof BotAction.MoveTo moveTo) {
			BlockPos pos = moveTo.pos();
			getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, MOVE_SPEED);
		} else if (action instanceof BotAction.MineBlock mineBlock) {
			BlockPos pos = mineBlock.pos();
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				return reject(level, action, "no hay ningun bloque en " + pos.toShortString());
			}
			float destroySpeed = state.getDestroySpeed(level, pos);
			if (destroySpeed < 0) {
				return reject(level, action, "ese bloque es indestructible");
			}
			double distSqr = distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (distSqr > MINE_REACH_SQR) {
				return reject(level, action, "el bloque esta muy lejos (" + String.format("%.1f", Math.sqrt(distSqr))
						+ " bloques, maximo " + Math.sqrt(MINE_REACH_SQR) + ")");
			}
			this.mineTicksRequired = Math.max(1, Math.round(destroySpeed * 20.0f));
			this.mineTicksElapsed = 0;
		} else if (action instanceof BotAction.Attack attack) {
			Entity target = level.getEntity(attack.targetEntityId());
			if (target == null || !target.isAlive()) {
				return reject(level, action, "no se encontro el objetivo");
			}
		} else if (action instanceof BotAction.Follow follow) {
			Entity target = level.getEntity(follow.targetEntityId());
			if (target == null || !target.isAlive()) {
				return reject(level, action, "no se encontro a quien seguir");
			}
		}

		this.currentAction = action;
		this.currentActionStartTick = level.getGameTime();
		return null;
	}

	private String reject(ServerLevel level, BotAction action, String reason) {
		memory.addRecord(describe(action), "rechazada: " + reason, level.getGameTime());
		return reason;
	}

	private static String describe(BotAction action) {
		return switch (action) {
			case BotAction.MoveTo m -> "moveTo(" + m.pos().toShortString() + ")";
			case BotAction.MineBlock m -> "mineBlock(" + m.pos().toShortString() + ")";
			case BotAction.Attack a -> "attack(#" + a.targetEntityId() + ")";
			case BotAction.Say s -> "say(\"" + s.message() + "\")";
			case BotAction.Follow f -> "follow(#" + f.targetEntityId() + ")";
		};
	}

	private void abandonCurrentAction(ServerLevel level) {
		if (currentAction instanceof BotAction.MineBlock mineBlock) {
			level.destroyBlockProgress(getId(), mineBlock.pos(), -1);
		}
		getNavigation().stop();
		this.currentAction = null;
		this.mineTicksElapsed = 0;
		this.mineTicksRequired = 0;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (attackCooldown > 0) {
			attackCooldown--;
		}

		BotAction action = this.currentAction;
		if (action == null) {
			return;
		}

		boolean finished;
		if (action instanceof BotAction.MoveTo) {
			finished = getNavigation().isDone();
		} else if (action instanceof BotAction.MineBlock mineBlock) {
			finished = tickMineBlock(level, mineBlock);
		} else if (action instanceof BotAction.Attack attack) {
			finished = tickAttack(level, attack);
		} else if (action instanceof BotAction.Follow follow) {
			finished = tickFollow(level, follow);
		} else {
			// Say se resuelve entero en setAction(), nunca deberia quedar en curso.
			finished = true;
		}

		if (finished) {
			if (action instanceof BotAction.MineBlock mineBlock) {
				level.destroyBlockProgress(getId(), mineBlock.pos(), -1);
			}
			memory.addRecord(describe(action), "finalizada", level.getGameTime());
			this.currentAction = null;
		}
	}

	private boolean tickMineBlock(ServerLevel level, BotAction.MineBlock action) {
		BlockPos pos = action.pos();
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return true;
		}

		mineTicksElapsed++;
		int stage = mineTicksRequired <= 0 ? 9 : Math.min(9, (mineTicksElapsed * 10) / mineTicksRequired);
		level.destroyBlockProgress(getId(), pos, stage);

		if (mineTicksElapsed >= mineTicksRequired) {
			level.destroyBlock(pos, true, this, 512);
			return true;
		}
		return false;
	}

	private boolean tickAttack(ServerLevel level, BotAction.Attack action) {
		Entity target = level.getEntity(action.targetEntityId());
		if (target == null || !target.isAlive()) {
			return true;
		}
		if (distanceToSqr(target) > ATTACK_GIVE_UP_RANGE_SQR) {
			return true;
		}
		if (distanceToSqr(target) > ATTACK_RANGE_SQR) {
			getNavigation().moveTo(target, MOVE_SPEED);
			return false;
		}

		getNavigation().stop();
		if (attackCooldown <= 0) {
			doHurtTarget(level, target);
			attackCooldown = ATTACK_COOLDOWN_TICKS;
		}
		return false;
	}

	/**
	 * A diferencia de MoveTo (un punto fijo), Follow re-apunta la navegacion
	 * al target en cada tick mientras esta vivo y cerca - nunca "termina"
	 * por si sola. El mecanismo de accion trabada de DecisionScheduler
	 * (10s) se encarga de volver a consultar al LLM periodicamente aunque
	 * el seguimiento siga en curso, sin bloquear el movimiento fluido
	 * mientras tanto.
	 */
	private boolean tickFollow(ServerLevel level, BotAction.Follow action) {
		Entity target = level.getEntity(action.targetEntityId());
		if (target == null || !target.isAlive()) {
			return true;
		}
		if (distanceToSqr(target) > FOLLOW_GIVE_UP_RANGE_SQR) {
			return true;
		}
		if (distanceToSqr(target) > FOLLOW_STOP_DISTANCE_SQR) {
			getNavigation().moveTo(target, MOVE_SPEED);
		} else {
			getNavigation().stop();
		}
		return false;
	}

	private void say(ServerLevel level, String message) {
		level.getServer().getPlayerList()
				.broadcastSystemMessage(Component.literal("<AIWorker> " + message), false);
	}
}
