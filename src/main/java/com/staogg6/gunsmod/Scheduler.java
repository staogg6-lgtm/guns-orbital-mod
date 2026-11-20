package com.staogg6.gunsmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Runs small tasks a few ticks later on the server thread (used for bursts and strike countdowns). */
public final class Scheduler {
	private static final class Task {
		int delay;
		final Runnable action;

		Task(int delay, Runnable action) {
			this.delay = delay;
			this.action = action;
		}
	}

	private static final List<Task> TASKS = new ArrayList<>();

	private Scheduler() {
	}

	public static void later(int ticks, Runnable action) {
		TASKS.add(new Task(Math.max(1, ticks), action));
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (TASKS.isEmpty()) {
				return;
			}
			List<Task> due = new ArrayList<>();
			for (int i = TASKS.size() - 1; i >= 0; i--) {
				Task t = TASKS.get(i);
				if (--t.delay <= 0) {
					due.add(0, t);
					TASKS.remove(i);
				}
			}
			for (Task t : due) {
				try {
					t.action.run();
				} catch (Exception e) {
					// never let one bad task crash the server
				}
			}
		});
	}
}
