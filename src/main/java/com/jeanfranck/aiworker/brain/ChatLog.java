package com.jeanfranck.aiworker.brain;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Buffer global (no por bot) de los ultimos mensajes de chat del server.
 * Cada WorldSnapshot filtra de aca los mensajes cercanos al bot que arma
 * el snapshot - la nocion de "dirigido al bot" es simplemente proximidad,
 * el LLM decide si el mensaje le compete.
 */
public final class ChatLog {
	private static final int MAX_ENTRIES = 50;
	private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

	private ChatLog() {
	}

	public record Entry(String sender, String message, double x, double y, double z) {
	}

	public static void register() {
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
				add(sender.getName().getString(), message.signedContent(), sender));
	}

	private static synchronized void add(String sender, String text, ServerPlayer player) {
		ENTRIES.addLast(new Entry(sender, text, player.getX(), player.getY(), player.getZ()));
		while (ENTRIES.size() > MAX_ENTRIES) {
			ENTRIES.removeFirst();
		}
	}

	public static synchronized List<Entry> recentNear(double x, double y, double z, double radius, int limit) {
		double radiusSqr = radius * radius;
		List<Entry> matches = new ArrayList<>();
		for (Entry entry : ENTRIES) {
			double dx = entry.x() - x;
			double dy = entry.y() - y;
			double dz = entry.z() - z;
			if (dx * dx + dy * dy + dz * dz <= radiusSqr) {
				matches.add(entry);
			}
		}
		int from = Math.max(0, matches.size() - limit);
		return matches.subList(from, matches.size());
	}
}
