package com.jeanfranck.aiworker.brain;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Memoria de corto plazo de un bot: historial acotado de (accion, resultado)
 * y un campo de "plan" en texto libre que el LLM reescribe cada ciclo. El
 * sistema nunca interpreta el contenido de plan() - es una pizarra del LLM,
 * no un dato estructurado. Vive solo en RAM, no sobrevive un reinicio del
 * server (ver STATUS.md, pendiente si hace falta persistirla a NBT).
 */
public final class BotMemory {
	private static final int MAX_HISTORY = 10;

	private final Deque<CycleRecord> history = new ArrayDeque<>();
	private String plan = "";

	public record CycleRecord(String action, String result, long tick) {
	}

	public void addRecord(String action, String result, long tick) {
		history.addLast(new CycleRecord(action, result, tick));
		while (history.size() > MAX_HISTORY) {
			history.removeFirst();
		}
	}

	public List<CycleRecord> history() {
		return List.copyOf(history);
	}

	public String plan() {
		return plan;
	}

	public void setPlan(String plan) {
		this.plan = plan;
	}

	public void clear() {
		history.clear();
		plan = "";
	}
}
