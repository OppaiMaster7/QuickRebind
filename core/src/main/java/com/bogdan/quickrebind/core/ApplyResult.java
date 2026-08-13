package com.bogdan.quickrebind.core;

/**
 * What actually happened when a preset was applied.
 *
 * <p>A plain class rather than a record: the 1.8.9 build compiles to Java 8.
 */
public final class ApplyResult {
	/** Binds moved to the key the preset asked for. */
	public final int rebound;
	/** Binds that were already on the right key. */
	public final int alreadyCorrect;
	/** Binds the preset didn't mention, put back to default. */
	public final int resetToDefault;
	/** Entries naming a key this Minecraft doesn't have. */
	public final int unreadable;
	/** Entries for binds this install doesn't have; kept in the file. */
	public final int notInstalled;
	/** Binds sharing a key with another bind afterwards, debug combos excluded. */
	public final int conflicts;
	/** The same measure taken before the preset was applied. */
	public final int conflictsBefore;

	public ApplyResult(int rebound, int alreadyCorrect, int resetToDefault,
			int unreadable, int notInstalled, int conflicts, int conflictsBefore) {
		this.rebound = rebound;
		this.alreadyCorrect = alreadyCorrect;
		this.resetToDefault = resetToDefault;
		this.unreadable = unreadable;
		this.notInstalled = notInstalled;
		this.conflicts = conflicts;
		this.conflictsBefore = conflictsBefore;
	}

	public int changed() {
		return rebound + resetToDefault;
	}

	/**
	 * Clashes this apply introduced. Only these are worth interrupting someone
	 * about — an overlap they already had is not news.
	 */
	public int newConflicts() {
		return Math.max(0, conflicts - conflictsBefore);
	}

	@Override
	public String toString() {
		return "rebound=" + rebound + " alreadyCorrect=" + alreadyCorrect
				+ " resetToDefault=" + resetToDefault + " unreadable=" + unreadable
				+ " notInstalled=" + notInstalled + " conflicts=" + conflicts
				+ " (was " + conflictsBefore + ")";
	}
}
