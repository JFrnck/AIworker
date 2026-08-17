package com.jeanfranck.aiworker;

import com.jeanfranck.aiworker.body.AIWorkerCommands;
import com.jeanfranck.aiworker.brain.ChatLog;
import com.jeanfranck.aiworker.brain.DecisionScheduler;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIWorkerMod implements ModInitializer {
	public static final String MOD_ID = "aiworker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[{}] Inicializando", MOD_ID);
		AIWorkerEntities.register();
		AIWorkerCommands.register();
		ChatLog.register();
		DecisionScheduler.register();
		if (!DecisionScheduler.isConfigured()) {
			LOGGER.warn("[{}] OPENAI_API_KEY no esta configurada - el modo automatico (/aiworker auto on) no va a funcionar hasta que se setee.", MOD_ID);
		}
	}
}
