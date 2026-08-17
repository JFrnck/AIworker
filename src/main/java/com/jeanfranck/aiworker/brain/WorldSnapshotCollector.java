package com.jeanfranck.aiworker.brain;

import com.jeanfranck.aiworker.body.AIWorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WorldSnapshotCollector {
	private static final int RAW_BLOCK_RADIUS = 2;
	private static final int POI_RADIUS = 12;
	private static final int POI_MAX_PER_CATEGORY = 5;
	private static final double ENTITY_RADIUS = 16.0;
	private static final int ENTITY_MAX = 10;
	private static final double CHAT_RADIUS = 32.0;
	private static final int CHAT_MAX = 5;

	private WorldSnapshotCollector() {
	}

	public static WorldSnapshot collect(AIWorkerEntity bot, ServerLevel level) {
		BlockPos origin = bot.blockPosition();

		WorldSnapshot.SelfStatus self = new WorldSnapshot.SelfStatus(
				bot.getX(), bot.getY(), bot.getZ(),
				bot.getHealth(),
				itemId(bot.getMainHandItem()));

		List<WorldSnapshot.BlockInfo> nearbyBlocks = scanRawBlocks(level, origin);
		WorldSnapshot.PointsOfInterest poi = scanPointsOfInterest(level, origin);
		List<WorldSnapshot.EntityInfo> entities = scanEntities(level, bot, origin);

		List<WorldSnapshot.ChatEntry> chat = ChatLog.recentNear(bot.getX(), bot.getY(), bot.getZ(), CHAT_RADIUS, CHAT_MAX)
				.stream()
				.map(e -> new WorldSnapshot.ChatEntry(e.sender(), e.message()))
				.toList();

		List<String> recentActions = bot.memory().history().stream()
				.map(r -> r.action() + " -> " + r.result())
				.toList();

		return new WorldSnapshot(self, nearbyBlocks, poi, entities, chat, recentActions, bot.memory().plan());
	}

	private static List<WorldSnapshot.BlockInfo> scanRawBlocks(ServerLevel level, BlockPos origin) {
		List<WorldSnapshot.BlockInfo> result = new ArrayList<>();
		BlockPos min = origin.offset(-RAW_BLOCK_RADIUS, -RAW_BLOCK_RADIUS, -RAW_BLOCK_RADIUS);
		BlockPos max = origin.offset(RAW_BLOCK_RADIUS, RAW_BLOCK_RADIUS, RAW_BLOCK_RADIUS);
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			result.add(blockInfo(pos, state));
		}
		return result;
	}

	private static WorldSnapshot.PointsOfInterest scanPointsOfInterest(ServerLevel level, BlockPos origin) {
		List<WorldSnapshot.BlockInfo> crops = new ArrayList<>();
		List<WorldSnapshot.BlockInfo> trees = new ArrayList<>();
		List<WorldSnapshot.BlockInfo> chests = new ArrayList<>();
		List<WorldSnapshot.BlockInfo> furnaces = new ArrayList<>();

		BlockPos min = origin.offset(-POI_RADIUS, -POI_RADIUS, -POI_RADIUS);
		BlockPos max = origin.offset(POI_RADIUS, POI_RADIUS, POI_RADIUS);
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			if (crops.size() >= POI_MAX_PER_CATEGORY && trees.size() >= POI_MAX_PER_CATEGORY
					&& chests.size() >= POI_MAX_PER_CATEGORY && furnaces.size() >= POI_MAX_PER_CATEGORY) {
				break;
			}

			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}

			if (chests.size() < POI_MAX_PER_CATEGORY && state.getBlock() instanceof ChestBlock) {
				chests.add(blockInfo(pos, state));
			} else if (furnaces.size() < POI_MAX_PER_CATEGORY && state.getBlock() instanceof AbstractFurnaceBlock) {
				furnaces.add(blockInfo(pos, state));
			} else if (crops.size() < POI_MAX_PER_CATEGORY && state.is(BlockTags.CROPS)
					&& state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
				crops.add(blockInfo(pos, state));
			} else if (trees.size() < POI_MAX_PER_CATEGORY && state.is(BlockTags.LOGS)) {
				trees.add(blockInfo(pos, state));
			}
		}

		return new WorldSnapshot.PointsOfInterest(crops, trees, chests, furnaces);
	}

	private static List<WorldSnapshot.EntityInfo> scanEntities(ServerLevel level, AIWorkerEntity bot, BlockPos origin) {
		AABB area = new AABB(origin).inflate(ENTITY_RADIUS);
		List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, area, e -> e != bot);
		return nearby.stream()
				.sorted(Comparator.comparingDouble(bot::distanceToSqr))
				.limit(ENTITY_MAX)
				.map(e -> new WorldSnapshot.EntityInfo(e.getId(), entityTypeId(e), e.getX(), e.getY(), e.getZ(), e instanceof Enemy))
				.toList();
	}

	private static WorldSnapshot.BlockInfo blockInfo(BlockPos pos, BlockState state) {
		return new WorldSnapshot.BlockInfo(pos.getX(), pos.getY(), pos.getZ(), blockId(state));
	}

	private static String blockId(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	private static String itemId(ItemStack stack) {
		return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static String entityTypeId(Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}
}
