package com.sinat.bubbletoolsync;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * One reading of the player's progress (levels, quests, diary tasks), and the code that packs it into the
 * text the QR code holds: "BTS1:" followed by the bytes below in base64url.
 * <p>
 * The layout ("format version 1") is written out in CLAUDE.md. The phone app unpacks exactly this layout,
 * so any change here must be made in the app too, and needs a new format version.
 */
@Getter
@AllArgsConstructor
public final class SyncData
{
	static final int FORMAT_VERSION = 1;
	static final String TEXT_PREFIX = "BTS1:";

	/**
	 * The skills, in the order they're sent. Written out by hand (not Skill.values()) so that a change in
	 * RuneLite can't quietly reorder them. A new skill needs a new format version.
	 */
	static final Skill[] SKILLS = {
		Skill.ATTACK, Skill.DEFENCE, Skill.STRENGTH, Skill.HITPOINTS, Skill.RANGED, Skill.PRAYER, Skill.MAGIC,
		Skill.COOKING, Skill.WOODCUTTING, Skill.FLETCHING, Skill.FISHING, Skill.FIREMAKING, Skill.CRAFTING,
		Skill.SMITHING, Skill.MINING, Skill.HERBLORE, Skill.AGILITY, Skill.THIEVING, Skill.SLAYER, Skill.FARMING,
		Skill.RUNECRAFT, Skill.HUNTER, Skill.CONSTRUCTION, Skill.SAILING,
	};

	/**
	 * The game values ("varps") that hold the achievement diary tasks, one task per bit. Two for each area,
	 * then Karamja's elite tasks.
	 */
	static final int[] DIARY_VARPS = {
		VarPlayerID.ARDOUNGE_ACHIEVEMENT_DIARY, VarPlayerID.ARDOUNGE_ACHIEVEMENT_DIARY2,
		VarPlayerID.DESERT_ACHIEVEMENT_DIARY, VarPlayerID.DESERT_ACHIEVEMENT_DIARY2,
		VarPlayerID.FALADOR_ACHIEVEMENT_DIARY, VarPlayerID.FALADOR_ACHIEVEMENT_DIARY2,
		VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY, VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY2,
		VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY, VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY2,
		VarPlayerID.KOUREND_ACHIEVEMENT_DIARY, VarPlayerID.KOUREND_ACHIEVEMENT_DIARY2,
		VarPlayerID.LUMB_DRAY_ACHIEVEMENT_DIARY, VarPlayerID.LUMB_DRAY_ACHIEVEMENT_DIARY2,
		VarPlayerID.MORYTANIA_ACHIEVEMENT_DIARY, VarPlayerID.MORYTANIA_ACHIEVEMENT_DIARY2,
		VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, VarPlayerID.VARROCK_ACHIEVEMENT_DIARY2,
		VarPlayerID.WESTERN_ACHIEVEMENT_DIARY, VarPlayerID.WESTERN_ACHIEVEMENT_DIARY2,
		VarPlayerID.WILDERNESS_ACHIEVEMENT_DIARY, VarPlayerID.WILDERNESS_ACHIEVEMENT_DIARY2,
		VarPlayerID.ATJUN_TASKS_4,
	};

	/**
	 * Karamja's easy, medium and hard tasks are kept in their own small game values ("varbits") instead of
	 * bits of a varp. In the order Quest Helper lists them.
	 */
	static final int[] KARAMJA_VARBITS = {
		VarbitID.ATJUN_EASY_BANANA, VarbitID.ATJUN_EASY_SWING, VarbitID.ATJUN_EASY_GOLD,
		VarbitID.ATJUN_EASY_BOAT_SARIM, VarbitID.ATJUN_EASY_BOAT_ARDY, VarbitID.ATJUN_EASY_CAIRN,
		VarbitID.ATJUN_EASY_FISHING, VarbitID.ATJUN_EASY_SEAWEED, VarbitID.ATJUN_EASY_TZHAAR,
		VarbitID.ATJUN_EASY_JOGRE,
		VarbitID.ATJUN_HARD_FIGHTPITS, VarbitID.ATJUN_HARD_FIGHTCAVE, VarbitID.ATJUN_HARD_OOMLIE,
		VarbitID.ATJUN_HARD_NATURE, VarbitID.ATJUN_HARD_KARAMBWAN, VarbitID.ATJUN_HARD_DEATHWING,
		VarbitID.ATJUN_HARD_XBOW, VarbitID.ATJUN_HARD_PALM, VarbitID.ATJUN_HARD_DURADEL,
		VarbitID.ATJUN_HARD_DRAGON,
		VarbitID.ATJUN_MED_AGILITY, VarbitID.ATJUN_MED_VOLCANO, VarbitID.ATJUN_MED_CRANDOR,
		VarbitID.ATJUN_MED_CART, VarbitID.ATJUN_MED_CLEANUP, VarbitID.ATJUN_MED_SPIDER,
		VarbitID.ATJUN_MED_TOPAZ, VarbitID.ATJUN_MED_TEAK, VarbitID.ATJUN_MED_MAHOGANY,
		VarbitID.ATJUN_MED_KARAMBWAN, VarbitID.ATJUN_MED_MACHETTE, VarbitID.ATJUN_MED_GLIDER,
		VarbitID.ATJUN_MED_FARMING, VarbitID.ATJUN_MED_GRAAHK, VarbitID.ATJUN_MED_SHILO_VINES,
		VarbitID.ATJUN_MED_SHILO_LAVA, VarbitID.ATJUN_MED_SHILO_STAIRS, VarbitID.ATJUN_MED_KHAZARD,
		VarbitID.ATJUN_MED_CHARTER,
	};

	/** Quest states as sent: 2 bits each. */
	static final int QUEST_NOT_STARTED = 0;
	static final int QUEST_STARTED = 1;
	static final int QUEST_FINISHED = 2;

	/** When this was read, in seconds since 1 January 1970. */
	private final long readAt;
	private final String accountName;
	/** 0 normal, 1 ironman, 2 ultimate, 3 hardcore, 4 group ironman, and so on (varbit IRONMAN). */
	private final int accountType;
	/** The game's own quest point total (varp QP), so the app matches the game even before it knows a new quest. */
	private final int questPoints;
	/** One level per skill, in the order of {@link #SKILLS}. */
	private final int[] skillLevels;
	/** One value per varp, in the order of {@link #DIARY_VARPS}. */
	private final int[] diaryVarps;
	/** One value per varbit, in the order of {@link #KARAMJA_VARBITS}. */
	private final int[] karamjaVarbits;
	/** RuneLite's quest IDs, smallest first. */
	private final int[] questIds;
	/** One state per quest, in the same order as {@link #questIds}: 0, 1 or 2 (see QUEST_ constants). */
	private final int[] questStates;

	/**
	 * Reads everything from the game. Must run on the client thread (quest states come from a game script).
	 *
	 * @return the reading, or null if the player isn't fully logged in yet
	 */
	static SyncData read(Client client, long nowSeconds)
	{
		Player player = client.getLocalPlayer();
		String name = player == null ? null : player.getName();
		if (name == null || name.isEmpty())
		{
			return null;
		}
		// The game uses a "non-breaking space" in some names; turn it back into a normal space.
		name = name.replace(' ', ' ');

		int[] levels = new int[SKILLS.length];
		for (int i = 0; i < SKILLS.length; i++)
		{
			levels[i] = client.getRealSkillLevel(SKILLS[i]);
		}

		int[] varps = new int[DIARY_VARPS.length];
		for (int i = 0; i < DIARY_VARPS.length; i++)
		{
			varps[i] = client.getVarpValue(DIARY_VARPS[i]);
		}

		int[] karamja = new int[KARAMJA_VARBITS.length];
		for (int i = 0; i < KARAMJA_VARBITS.length; i++)
		{
			karamja[i] = client.getVarbitValue(KARAMJA_VARBITS[i]);
		}

		// Every quest RuneLite knows about, so new quests are sent without changing this plugin.
		// A TreeMap keeps them sorted by ID.
		Map<Integer, Integer> quests = new TreeMap<>();
		for (Quest quest : Quest.values())
		{
			int state;
			switch (quest.getState(client))
			{
				case FINISHED:
					state = QUEST_FINISHED;
					break;
				case IN_PROGRESS:
					state = QUEST_STARTED;
					break;
				default:
					state = QUEST_NOT_STARTED;
					break;
			}
			quests.put(quest.getId(), state);
		}
		int[] questIds = new int[quests.size()];
		int[] questStates = new int[quests.size()];
		int i = 0;
		for (Map.Entry<Integer, Integer> entry : quests.entrySet())
		{
			questIds[i] = entry.getKey();
			questStates[i] = entry.getValue();
			i++;
		}

		return new SyncData(nowSeconds, name, client.getVarbitValue(VarbitID.IRONMAN),
			client.getVarpValue(VarPlayerID.QP), levels, varps, karamja, questIds, questStates);
	}

	/**
	 * True if the other reading has exactly the same progress as this one: everything except when it was read.
	 * Used to skip showing and saving a reading when nothing has really changed.
	 */
	boolean hasSameProgressAs(SyncData other)
	{
		return other != null
			&& accountName.equals(other.accountName)
			&& accountType == other.accountType
			&& questPoints == other.questPoints
			&& Arrays.equals(skillLevels, other.skillLevels)
			&& Arrays.equals(diaryVarps, other.diaryVarps)
			&& Arrays.equals(karamjaVarbits, other.karamjaVarbits)
			&& Arrays.equals(questIds, other.questIds)
			&& Arrays.equals(questStates, other.questStates);
	}

	/** Packs this reading into bytes, in the layout described at the top. */
	byte[] pack()
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream(512);

		out.write(FORMAT_VERSION);
		writeInt(out, readAt);

		byte[] nameBytes = accountName.getBytes(StandardCharsets.UTF_8);
		if (nameBytes.length > 255)
		{
			throw new IllegalArgumentException("Account name is too long");
		}
		out.write(nameBytes.length);
		out.write(nameBytes, 0, nameBytes.length);

		out.write(toByte(accountType));

		// Quest points: 2 bytes, biggest first, kept within 0 to 65,535.
		int points = Math.max(0, Math.min(0xFFFF, questPoints));
		out.write(points >>> 8);
		out.write(points & 0xFF);

		checkLength(skillLevels, SKILLS.length, "skill levels");
		for (int level : skillLevels)
		{
			out.write(toByte(level));
		}

		checkLength(diaryVarps, DIARY_VARPS.length, "diary varps");
		for (int value : diaryVarps)
		{
			writeInt(out, value);
		}

		checkLength(karamjaVarbits, KARAMJA_VARBITS.length, "Karamja varbits");
		for (int value : karamjaVarbits)
		{
			out.write(toByte(value));
		}

		checkLength(questStates, questIds.length, "quest states");
		writeVarint(out, questIds.length);
		int previousId = 0;
		for (int id : questIds)
		{
			if (id < previousId)
			{
				throw new IllegalArgumentException("Quest IDs must be sorted, smallest first");
			}
			writeVarint(out, id - previousId);
			previousId = id;
		}

		// Four quest states per byte, the first quest in the top two bits.
		byte[] states = new byte[(questStates.length + 3) / 4];
		for (int q = 0; q < questStates.length; q++)
		{
			int shift = 6 - 2 * (q % 4);
			states[q / 4] |= (byte) ((questStates[q] & 0b11) << shift);
		}
		out.write(states, 0, states.length);

		return out.toByteArray();
	}

	/** The text that goes in the QR code (and in the saved file): "BTS1:" and the packed bytes in base64url. */
	String toText()
	{
		return TEXT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(pack());
	}

	/** Writes the lowest 4 bytes of a number, biggest byte first. */
	private static void writeInt(ByteArrayOutputStream out, long value)
	{
		out.write((int) (value >>> 24) & 0xFF);
		out.write((int) (value >>> 16) & 0xFF);
		out.write((int) (value >>> 8) & 0xFF);
		out.write((int) value & 0xFF);
	}

	/** Writes a number 7 bits at a time, lowest bits first; the top bit of each byte means "more follows". */
	private static void writeVarint(ByteArrayOutputStream out, int value)
	{
		if (value < 0)
		{
			throw new IllegalArgumentException("Varints can't be negative");
		}
		while (value >= 0x80)
		{
			out.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.write(value);
	}

	/** Keeps a number within one byte (0 to 255): anything bigger is sent as 255. */
	private static int toByte(int value)
	{
		return Math.max(0, Math.min(255, value));
	}

	private static void checkLength(int[] values, int expected, String what)
	{
		if (values.length != expected)
		{
			throw new IllegalArgumentException("Expected " + expected + " " + what + ", got " + values.length);
		}
	}
}
