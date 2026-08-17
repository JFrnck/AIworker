package com.jeanfranck.aiworker;

import com.jeanfranck.aiworker.body.AIWorkerEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class AIWorkerEntities {
	public static final EntityType<AIWorkerEntity> WORKER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			id("worker"),
			FabricEntityType.Builder.createMob(
					AIWorkerEntity::new,
					MobCategory.CREATURE,
					builder -> builder.defaultAttributes(AIWorkerEntity::createAttributes))
					.sized(0.6f, 1.95f)
					.build(ResourceKey.create(Registries.ENTITY_TYPE, id("worker"))));

	private AIWorkerEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(WORKER, AIWorkerEntity.createAttributes());
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(AIWorkerMod.MOD_ID, path);
	}
}
