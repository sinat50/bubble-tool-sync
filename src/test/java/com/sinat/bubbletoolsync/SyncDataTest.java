package com.sinat.bubbletoolsync;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks that readings pack into exactly the bytes of format version 1 (see the table in SyncData), using
 * examples worked out by hand. The phone app reads these same bytes, so if one of these tests fails after a
 * change, the app would read the QR code wrongly too.
 */
public class SyncDataTest
{
	/** A reading with something worth checking in every part. Its packed bytes are worked out in packsEveryPart. */
	private static SyncData example()
	{
		int[] levels = new int[24];
		for (int i = 0; i < levels.length; i++)
		{
			levels[i] = i + 1;
		}
		levels[0] = 99;
		levels[23] = 300; // too big for a byte: sent as 255

		int[] varps = new int[23];
		varps[0] = 0x12345678;
		varps[1] = 1 << 31; // only the top bit set
		varps[22] = -1; // every bit set

		int[] karamja = new int[39];
		Arrays.fill(karamja, 1);
		karamja[0] = 5;
		karamja[1] = 300; // sent as 255
		karamja[2] = -1; // sent as 0

		int[] questIds = {0, 1, 3, 200, 16614};
		int[] questStates = {2, 1, 0, 2, 1};

		return new SyncData(1793000000L, "Bob", 2, 337, levels, varps, karamja, questIds, questStates);
	}

	@Test
	public void packsEveryPart()
	{
		byte[] expected = join(
			// Format version
			bytes(1),
			// Read at 1,793,000,000 seconds = 0x6ADF0240
			bytes(0x6A, 0xDF, 0x02, 0x40),
			// Name: length 3, then "Bob"
			bytes(3, 'B', 'o', 'b'),
			// Account type 2 (ultimate ironman)
			bytes(2),
			// Quest points 337 = 0x0151
			bytes(0x01, 0x51),
			// 24 skill levels: 99, then 2 to 23, then 300 sent as 255
			bytes(99, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 255),
			// 23 diary varps, 4 bytes each, biggest byte first
			bytes(0x12, 0x34, 0x56, 0x78),
			bytes(0x80, 0x00, 0x00, 0x00),
			new byte[20 * 4],
			bytes(0xFF, 0xFF, 0xFF, 0xFF),
			// 39 Karamja varbits: 5, 300 sent as 255, -1 sent as 0, then 36 ones
			bytes(5, 255, 0),
			ones(36),
			// 5 quests
			bytes(5),
			// Quest IDs 0, 1, 3, 200, 16614 as differences 0, 1, 2, 197, 16414:
			// 197 = 0b1_1000101 -> 0xC5 0x01; 16414 = 0b1_0000000_0011110 -> 0x9E 0x80 0x01
			bytes(0x00, 0x01, 0x02, 0xC5, 0x01, 0x9E, 0x80, 0x01),
			// States 2, 1, 0, 2 -> 10 01 00 10 = 0x92; then 1 -> 01 00 00 00 = 0x40
			bytes(0x92, 0x40)
		);

		assertArrayEquals(expected, example().pack());
	}

	@Test
	public void questPointsGoStraightAfterAccountType()
	{
		byte[] packed = example().pack();
		// Version 1 + time 4 + name length 1 + "Bob" 3 = 9, so the account type is byte 9.
		assertEquals(2, packed[9]);
		assertEquals(0x01, packed[10]);
		assertEquals(0x51, packed[11]);
		// And the first skill level (99) straight after.
		assertEquals(99, packed[12]);
	}

	@Test
	public void questPointsAreKeptWithinTwoBytes()
	{
		assertArrayEquals(bytes(0xFF, 0xFF), questPointBytes(70_000));
		assertArrayEquals(bytes(0x00, 0x00), questPointBytes(-5));
		assertArrayEquals(bytes(0x01, 0x51), questPointBytes(337));
	}

	@Test
	public void timesAfter2038AreSentUnsigned()
	{
		// 4,000,000,000 seconds (the year 2096) doesn't fit in a signed 4-byte number, but does unsigned.
		SyncData data = withTime(4_000_000_000L);
		byte[] packed = data.pack();
		assertArrayEquals(bytes(0xEE, 0x6B, 0x28, 0x00), Arrays.copyOfRange(packed, 1, 5));
	}

	@Test
	public void namesAreSentAsUtf8()
	{
		// "é" takes 2 bytes in UTF-8, so the length byte counts bytes, not letters.
		SyncData data = withName("Zé");
		byte[] packed = data.pack();
		assertArrayEquals(bytes(3, 'Z', 0xC3, 0xA9), Arrays.copyOfRange(packed, 5, 9));
	}

	@Test
	public void textIsPrefixPlusBase64UrlWithoutPadding()
	{
		SyncData data = example();
		String text = data.toText();

		assertTrue(text.startsWith("BTS1:"));
		String encoded = text.substring("BTS1:".length());
		assertFalse("no padding", encoded.contains("="));
		assertFalse("url-safe letters only", encoded.contains("+") || encoded.contains("/"));
		assertArrayEquals(data.pack(), Base64.getUrlDecoder().decode(encoded));
	}

	@Test
	public void unpacksBackToTheSameReading()
	{
		SyncData original = example();
		SyncData unpacked = unpack(original.pack());

		assertEquals(original.getReadAt(), unpacked.getReadAt());
		assertEquals("Bob", unpacked.getAccountName());
		assertEquals(337, unpacked.getQuestPoints());
		assertArrayEquals(original.getQuestIds(), unpacked.getQuestIds());
		assertArrayEquals(original.getQuestStates(), unpacked.getQuestStates());
		assertArrayEquals(original.getDiaryVarps(), unpacked.getDiaryVarps());
		// Levels and Karamja values come back clamped to 0-255.
		assertEquals(255, unpacked.getSkillLevels()[23]);
		assertEquals(255, unpacked.getKaramjaVarbits()[1]);
		assertEquals(0, unpacked.getKaramjaVarbits()[2]);
	}

	@Test
	public void unpacksAFullSizedReading()
	{
		// About as big as a real account: 214 quests with IDs up to 17,027, all finished.
		int[] questIds = new int[214];
		for (int i = 0; i < questIds.length; i++)
		{
			questIds[i] = i * 80;
		}
		questIds[213] = 17027;
		int[] questStates = new int[214];
		Arrays.fill(questStates, 2);
		int[] levels = new int[24];
		Arrays.fill(levels, 99);
		int[] varps = new int[23];
		Arrays.fill(varps, -1);
		SyncData original = new SyncData(1793000000L, "Zezima", 0, 337, levels, varps, new int[39],
			questIds, questStates);

		SyncData unpacked = unpack(original.pack());

		assertTrue(original.hasSameProgressAs(unpacked));
	}

	@Test
	public void sameProgressIgnoresOnlyTheTime()
	{
		SyncData data = example();
		assertTrue(data.hasSameProgressAs(withTime(1)));
		assertFalse(data.hasSameProgressAs(withName("Alice")));
		assertFalse(data.hasSameProgressAs(withQuestPoints(338)));
		assertFalse(data.hasSameProgressAs(null));

		int[] questStates = data.getQuestStates().clone();
		questStates[2] = 1;
		SyncData questStarted = new SyncData(data.getReadAt(), "Bob", 2, 337, data.getSkillLevels(),
			data.getDiaryVarps(), data.getKaramjaVarbits(), data.getQuestIds(), questStates);
		assertFalse(data.hasSameProgressAs(questStarted));
	}

	@Test(expected = IllegalArgumentException.class)
	public void questIdsMustBeSorted()
	{
		SyncData data = example();
		new SyncData(0, "Bob", 0, 0, data.getSkillLevels(), data.getDiaryVarps(), data.getKaramjaVarbits(),
			new int[]{5, 3}, new int[]{0, 0}).pack();
	}

	@Test(expected = IllegalArgumentException.class)
	public void needsEverySkill()
	{
		SyncData data = example();
		new SyncData(0, "Bob", 0, 0, new int[23], data.getDiaryVarps(), data.getKaramjaVarbits(),
			data.getQuestIds(), data.getQuestStates()).pack();
	}

	@Test(expected = IllegalArgumentException.class)
	public void namesLongerThan255BytesAreRefused()
	{
		char[] name = new char[256];
		Arrays.fill(name, 'a');
		withName(new String(name)).pack();
	}

	/** The order of the lists is part of the format: these pin it down. */
	@Test
	public void listsAreInFormatOrder()
	{
		assertEquals(24, SyncData.SKILLS.length);
		assertEquals(Skill.ATTACK, SyncData.SKILLS[0]);
		assertEquals(Skill.HITPOINTS, SyncData.SKILLS[3]);
		assertEquals(Skill.RUNECRAFT, SyncData.SKILLS[20]);
		assertEquals(Skill.SAILING, SyncData.SKILLS[23]);

		// The game's varp numbers, in the format's order (Ardougne, Desert, Falador, Fremennik,
		// Kandarin, Kourend, Lumbridge & Draynor, Morytania, Varrock, Western, Wilderness, then Karamja elite).
		assertArrayEquals(new int[]{
			1196, 1197, 1198, 1199, 1186, 1187, 1184, 1185, 1178, 1179, 2085, 2086,
			1194, 1195, 1180, 1181, 1176, 1177, 1182, 1183, 1192, 1193, 1200,
		}, SyncData.DIARY_VARPS);

		assertEquals(39, SyncData.KARAMJA_VARBITS.length);
	}

	// ---- Helpers ----

	/** Reads packed bytes back, the way the phone app does. */
	private static SyncData unpack(byte[] packed)
	{
		Reader in = new Reader(packed);
		assertEquals("format version", 1, in.next());
		long readAt = in.int4() & 0xFFFFFFFFL;
		int nameLength = in.next();
		String name = new String(packed, in.pos, nameLength, StandardCharsets.UTF_8);
		in.pos += nameLength;
		int accountType = in.next();
		int questPoints = (in.next() << 8) | in.next();

		int[] levels = new int[24];
		for (int i = 0; i < levels.length; i++)
		{
			levels[i] = in.next();
		}
		int[] varps = new int[23];
		for (int i = 0; i < varps.length; i++)
		{
			varps[i] = in.int4();
		}
		int[] karamja = new int[39];
		for (int i = 0; i < karamja.length; i++)
		{
			karamja[i] = in.next();
		}

		int questCount = in.varint();
		int[] questIds = new int[questCount];
		int id = 0;
		for (int i = 0; i < questCount; i++)
		{
			id += in.varint();
			questIds[i] = id;
		}
		int[] questStates = new int[questCount];
		for (int i = 0; i < questCount; i++)
		{
			int b = packed[in.pos + i / 4] & 0xFF;
			questStates[i] = (b >> (6 - 2 * (i % 4))) & 0b11;
		}
		in.pos += (questCount + 3) / 4;

		assertEquals("no bytes left over", packed.length, in.pos);
		return new SyncData(readAt, name, accountType, questPoints, levels, varps, karamja, questIds, questStates);
	}

	private static class Reader
	{
		private final byte[] bytes;
		private int pos;

		Reader(byte[] bytes)
		{
			this.bytes = bytes;
		}

		int next()
		{
			return bytes[pos++] & 0xFF;
		}

		int int4()
		{
			return (next() << 24) | (next() << 16) | (next() << 8) | next();
		}

		int varint()
		{
			int value = 0;
			int shift = 0;
			int b;
			do
			{
				b = next();
				value |= (b & 0x7F) << shift;
				shift += 7;
			}
			while ((b & 0x80) != 0);
			return value;
		}
	}

	private static byte[] questPointBytes(int questPoints)
	{
		return Arrays.copyOfRange(withQuestPoints(questPoints).pack(), 10, 12);
	}

	private static SyncData withTime(long readAt)
	{
		SyncData d = example();
		return new SyncData(readAt, d.getAccountName(), d.getAccountType(), d.getQuestPoints(), d.getSkillLevels(),
			d.getDiaryVarps(), d.getKaramjaVarbits(), d.getQuestIds(), d.getQuestStates());
	}

	private static SyncData withName(String name)
	{
		SyncData d = example();
		return new SyncData(d.getReadAt(), name, d.getAccountType(), d.getQuestPoints(), d.getSkillLevels(),
			d.getDiaryVarps(), d.getKaramjaVarbits(), d.getQuestIds(), d.getQuestStates());
	}

	private static SyncData withQuestPoints(int questPoints)
	{
		SyncData d = example();
		return new SyncData(d.getReadAt(), d.getAccountName(), d.getAccountType(), questPoints, d.getSkillLevels(),
			d.getDiaryVarps(), d.getKaramjaVarbits(), d.getQuestIds(), d.getQuestStates());
	}

	/** Numbers 0 to 255 as bytes (so 0xFF can be written without a cast). */
	private static byte[] bytes(int... values)
	{
		byte[] out = new byte[values.length];
		for (int i = 0; i < values.length; i++)
		{
			out[i] = (byte) values[i];
		}
		return out;
	}

	private static byte[] ones(int count)
	{
		byte[] out = new byte[count];
		Arrays.fill(out, (byte) 1);
		return out;
	}

	private static byte[] join(byte[]... parts)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] part : parts)
		{
			out.write(part, 0, part.length);
		}
		return out.toByteArray();
	}
}
