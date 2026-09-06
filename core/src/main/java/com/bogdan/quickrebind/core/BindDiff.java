package com.bogdan.quickrebind.core;

/**
 * One line of "what would applying this preset actually do to that bind".
 *
 * <p>Computed against the live binds, so it answers the question you have while
 * hovering over Apply: not "what is in this preset" but "what is about to move".
 * A plain class rather than a record — the 1.8.9 build compiles to Java 8.
 */
public final class BindDiff {
	public enum Status {
		/** The preset names this bind and it is already on that key. */
		UNCHANGED,
		/** The preset would move this bind to a different key. */
		CHANGED,
		/** Not in the preset, and the missing-bind policy would reset it to default. */
		WOULD_RESET,
		/** Not in the preset, so it will be left exactly as it is. */
		NOT_IN_PRESET,
		/** The preset carries this bind but this install has never heard of it. */
		NOT_INSTALLED
	}

	public final String id;
	/** Key it sits on now, or null when the bind isn't installed here. */
	public final String currentKey;
	/** Key it would sit on afterwards, or null when nothing would change it. */
	public final String targetKey;
	public final Status status;

	public BindDiff(String id, String currentKey, String targetKey, Status status) {
		this.id = id;
		this.currentKey = currentKey;
		this.targetKey = targetKey;
		this.status = status;
	}

	/** Whether applying the preset would move this bind. */
	public boolean moves() {
		return status == Status.CHANGED || status == Status.WOULD_RESET;
	}
}
