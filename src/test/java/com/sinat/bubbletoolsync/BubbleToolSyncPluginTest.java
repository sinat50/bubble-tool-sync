package com.sinat.bubbletoolsync;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

// Starts RuneLite with this plugin loaded, for trying it out (./gradlew run)
public class BubbleToolSyncPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BubbleToolSyncPlugin.class);
		RuneLite.main(args);
	}
}
