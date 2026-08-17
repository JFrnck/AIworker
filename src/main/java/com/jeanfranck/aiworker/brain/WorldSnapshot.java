package com.jeanfranck.aiworker.brain;

import java.util.List;

/**
 * Lo que el LLM "ve" del mundo en un ciclo de decision - nunca una imagen,
 * siempre datos estructurados (igual que Mineflayer). raw_blocks_nearby es
 * un dump crudo de un radio chico (el entorno inmediato); points_of_interest
 * es un resumen acotado de un radio mas grande, para no gastar tokens
 * listando cientos de bloques irrelevantes.
 */
public record WorldSnapshot(
		SelfStatus self,
		List<BlockInfo> nearbyBlocks,
		PointsOfInterest pointsOfInterest,
		List<EntityInfo> nearbyEntities,
		List<ChatEntry> recentChat,
		List<String> recentActions,
		String plan
) {
	public record SelfStatus(double x, double y, double z, float health, String heldItem) {
	}

	public record PointsOfInterest(
			List<BlockInfo> cropsReady,
			List<BlockInfo> trees,
			List<BlockInfo> chests,
			List<BlockInfo> furnaces
	) {
	}

	public record BlockInfo(int x, int y, int z, String type) {
	}

	public record EntityInfo(int id, String type, double x, double y, double z, boolean hostile) {
	}

	public record ChatEntry(String sender, String message) {
	}
}
