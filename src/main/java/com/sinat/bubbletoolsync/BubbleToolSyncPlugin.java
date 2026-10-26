package com.sinat.bubbletoolsync;

import java.awt.image.BufferedImage;
import java.time.Instant;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Reads the player's own quests, levels and achievement diary tasks, and shows them in a side panel as a QR code
 * for the OSRS Bubble Tool phone app to scan.
 */
@PluginDescriptor(
	name = "Bubble Tool Sync",
	description = "Shows your quests, levels and diary tasks as a QR code for the OSRS Bubble Tool phone app",
	tags = {"bubble", "phone", "mobile", "qr", "quests", "diaries", "sync"},
	// Needed to save files through RuneLite's Filepath (the folder is .runelite/plugin-data/bubbletoolsync).
	internalName = "bubbletoolsync"
)
public class BubbleToolSyncPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	private SyncPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		panel = new SyncPanel(this::readSoon);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Bubble Tool Sync")
			.icon(icon)
			.priority(10)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	/** Asks the game to read everything at its next chance (reading has to happen on the game's own thread). */
	private void readSoon()
	{
		clientThread.invokeLater(this::read);
	}

	/** Reads everything and shows it in the panel. Runs on the game's thread. */
	private void read()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		SyncData data = SyncData.read(client, Instant.now().getEpochSecond());
		if (data == null)
		{
			return;
		}

		// The panel belongs to Swing's thread, so hand the reading over to it.
		SyncPanel currentPanel = panel;
		if (currentPanel != null)
		{
			SwingUtilities.invokeLater(() -> currentPanel.showData(data));
		}
	}
}
