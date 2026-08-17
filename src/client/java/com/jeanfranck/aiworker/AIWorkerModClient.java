package com.jeanfranck.aiworker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AIWorkerModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(AIWorkerEntities.WORKER, AIWorkerEntityRenderer::new);
	}
}
