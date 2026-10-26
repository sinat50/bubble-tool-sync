package com.sinat.bubbletoolsync;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;

/**
 * Reads the player's own quests, levels and achievement diary tasks, and shows them in a side panel as a QR code
 * for the OSRS Bubble Tool phone app to scan. Each new reading is also saved to a file, one per account.
 */
@Slf4j
@PluginDescriptor(
	name = "Bubble Tool Sync",
	description = "Shows your quests, levels and diary tasks as a QR code for the OSRS Bubble Tool phone app",
	tags = {"bubble", "phone", "mobile", "qr", "quests", "diaries", "sync"},
	// Needed to save files through RuneLite's Filepath (the folder is .runelite/plugin-data/bubbletoolsync).
	internalName = "bubbletoolsync"
)
public class BubbleToolSyncPlugin extends Plugin
{
	/**
	 * While playing, read again at most this often after something changes: 8 game ticks, about 5 seconds.
	 * Game values change all the time, so this stops the plugin reading on every tick.
	 */
	private static final int TICKS_BETWEEN_READS = 8;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	/** RuneLite's shared background worker, used for saving files so the game never waits for the disk. */
	@Inject
	private ScheduledExecutorService executor;

	private SyncPanel panel;
	private NavigationButton navButton;

	// These three are only used on the game's thread.
	/** True when something may have changed since the last reading. */
	private boolean changed;
	/** Game ticks since the last reading. */
	private int ticksSinceRead;
	/** The reading the panel shows now, or null if none yet. */
	private SyncData lastShown;

	@Override
	protected void startUp()
	{
		panel = new SyncPanel(this::readSoon, executor);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Bubble Tool Sync")
			.icon(icon)
			.priority(10)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// Read on the first game tick, in case the plugin is switched on while already logged in.
		changed = true;
		ticksSinceRead = TICKS_BETWEEN_READS;
		lastShown = null;
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			changed = true;
		}
	}

	/** A skill's level or experience changed. */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		changed = true;
	}

	/** A game value changed: quest progress, diary tasks and quest points are all kept in these. */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		changed = true;
	}

	/** Every game tick (0.6 seconds) while logged in: read again if something changed and it's been long enough. */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		ticksSinceRead++;
		if (changed && ticksSinceRead >= TICKS_BETWEEN_READS)
		{
			read(false);
		}
	}

	/** Called when the panel is opened: read straight away, at the game's next chance. */
	private void readSoon()
	{
		clientThread.invokeLater(() -> read(true));
	}

	/**
	 * Reads everything, and if it's new, shows it in the panel and saves it. Runs on the game's thread.
	 *
	 * @param panelOpened true when the player just opened the panel: then the reading is always shown (with
	 *                    the new time), even if nothing changed
	 */
	private void read(boolean panelOpened)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		SyncData data = SyncData.read(client, Instant.now().getEpochSecond());
		if (data == null)
		{
			// Not fully logged in yet (no name). "changed" stays true, so the next tick tries again.
			return;
		}
		changed = false;
		ticksSinceRead = 0;

		if (!panelOpened && data.hasSameProgressAs(lastShown))
		{
			return;
		}
		String text = data.toText();
		// Only remembered once it has packed without a problem.
		lastShown = data;

		// The panel belongs to Swing's thread, so hand the reading over to it.
		SyncPanel currentPanel = panel;
		if (currentPanel != null)
		{
			SwingUtilities.invokeLater(() -> currentPanel.showData(data, text));
		}

		save(data.getAccountName(), text);
	}

	/**
	 * The saved file's name for an account: "account-" + name + ".txt". The "account-" part matters: Windows
	 * reserves some names (Con, Aux, Nul, Prn, Com1, Lpt1, ...) that are also valid player names, and RuneLite
	 * refuses to make a file called, say, "Con.txt".
	 */
	static String fileNameFor(String accountName)
	{
		return "account-" + accountName + ".txt";
	}

	/**
	 * Saves the QR code's text to {@link #fileNameFor} in the plugin's folder, on the background worker.
	 * It's written to a temporary file first and then swapped in, so the file is never left half-written.
	 */
	private void save(String accountName, String text)
	{
		executor.execute(() ->
		{
			Filepath temp = null;
			try
			{
				Filepath folder = getPluginDirectory();
				folder.createDirectories();
				Filepath file = folder.joinSegment(fileNameFor(accountName));
				temp = folder.createTempFile("saving", ".tmp");
				temp.write(text);
				try
				{
					temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				}
				catch (AtomicMoveNotSupportedException e)
				{
					// Rare: this drive can't swap in one step, so do a normal replace instead.
					temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			catch (IOException | IllegalArgumentException e)
			{
				// The account name is left out on purpose: players often share their log when asking for help.
				log.warn("Couldn't save the reading", e);
			}
			finally
			{
				if (temp != null)
				{
					try
					{
						// Only still there if the swap failed.
						temp.deleteIfExists();
					}
					catch (IOException e)
					{
						log.debug("Couldn't delete {}", temp, e);
					}
				}
			}
		});
	}
}
