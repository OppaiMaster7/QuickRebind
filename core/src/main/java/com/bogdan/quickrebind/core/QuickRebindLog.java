package com.bogdan.quickrebind.core;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Core has to run on Minecraft 1.8.9 through 26.2, and those don't agree on a
 * logging library — slf4j only shows up in the modern ones. So core logs through
 * here and each platform wires it to whatever that version actually has.
 */
public final class QuickRebindLog {
	private static Consumer<String> info = message -> {
	};
	private static BiConsumer<String, Throwable> error = (message, thrown) -> {
	};

	private QuickRebindLog() {
	}

	public static void wire(Consumer<String> infoSink, BiConsumer<String, Throwable> errorSink) {
		info = infoSink;
		error = errorSink;
	}

	public static void info(String message) {
		info.accept(message);
	}

	public static void error(String message, Throwable thrown) {
		error.accept(message, thrown);
	}
}
