package com.bogdan.quickrebind.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Turns a preset into a single line of text you can paste into Discord, and back.
 *
 * <p>This is the "works on another PC" half of the mod — the shared folder only
 * covers machines you own. The payload deliberately drops the local id and
 * timestamps and keeps only what is portable: the name and the binds.
 *
 * <p>Format: {@code QRB1.} followed by URL-safe base64 of gzipped JSON.
 */
public final class ShareCode {
	private static final String PREFIX = "QRB1.";
	/** Guards against a hand-crafted code that gunzips into something enormous. */
	private static final int MAX_DECOMPRESSED_BYTES = 1 << 20;

	private ShareCode() {
	}

	/** What actually travels: no ids, no timestamps. */
	private static class Payload {
		int v = Preset.CURRENT_FORMAT;
		String name;
		String mc;
		Map<String, String> binds;
	}

	public static String encode(Preset preset) {
		Payload payload = new Payload();
		payload.name = preset.name;
		payload.mc = preset.gameVersion;
		payload.binds = preset.binds;

		byte[] json = JsonStore.COMPACT.toJson(payload).getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();

		try {
			GZIPOutputStream gzip = new GZIPOutputStream(compressed);

			try {
				gzip.write(json);
			} finally {
				gzip.close();
			}
		} catch (IOException e) {
			// Writing to memory; nothing here can actually fail.
			throw new IllegalStateException("Could not encode preset", e);
		}

		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed.toByteArray());
	}

	/**
	 * Parses a share code.
	 *
	 * @throws IllegalArgumentException if the text isn't a QuickRebind code. The
	 *         message is a translation key, so the GUI can show it directly.
	 */
	public static Preset decode(String text) {
		if (Preset.isBlank(text)) {
			throw new IllegalArgumentException("quickrebind.import.error.empty");
		}

		// Pasted codes pick up line breaks and stray spaces on the way through chat apps.
		String cleaned = text.replaceAll("\\s+", "");

		if (!cleaned.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
			throw new IllegalArgumentException("quickrebind.import.error.prefix");
		}

		byte[] compressed;

		try {
			compressed = Base64.getUrlDecoder().decode(cleaned.substring(PREFIX.length()));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("quickrebind.import.error.malformed");
		}

		String json;

		try {
			GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));

			try {
				json = new String(readAtMost(gzip), StandardCharsets.UTF_8);
			} finally {
				gzip.close();
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("quickrebind.import.error.malformed");
		}

		Payload payload;

		try {
			payload = JsonStore.COMPACT.fromJson(json, Payload.class);
		} catch (Exception e) {
			throw new IllegalArgumentException("quickrebind.import.error.malformed");
		}

		if (payload == null || payload.binds == null || payload.binds.isEmpty()) {
			throw new IllegalArgumentException("quickrebind.import.error.empty_preset");
		}

		Preset preset = Preset.of(
				Preset.isBlank(payload.name) ? "Imported" : payload.name,
				new TreeMap<String, String>(payload.binds),
				payload.mc);
		return preset.sanitise();
	}

	private static byte[] readAtMost(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int total = 0;
		int read;

		while ((read = in.read(buffer)) > 0) {
			total += read;

			if (total > MAX_DECOMPRESSED_BYTES) {
				throw new IOException("Share code expands to more than " + MAX_DECOMPRESSED_BYTES + " bytes");
			}

			out.write(buffer, 0, read);
		}

		return out.toByteArray();
	}
}
