package com.jeanfranck.aiworker;

import com.jeanfranck.aiworker.body.AIWorkerEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

/**
 * Placeholder visual: reusa el modelo/textura vanilla de zombie hasta que
 * el mod tenga arte propio. No hereda de Zombie ni de su AI, solo del
 * modelo/render.
 */
public class AIWorkerEntityRenderer extends MobRenderer<AIWorkerEntity, ZombieRenderState, ZombieModel<ZombieRenderState>> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");

	public AIWorkerEntityRenderer(EntityRendererProvider.Context context) {
		super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
	}

	@Override
	public ZombieRenderState createRenderState() {
		return new ZombieRenderState();
	}

	@Override
	public Identifier getTextureLocation(ZombieRenderState state) {
		return TEXTURE;
	}
}
