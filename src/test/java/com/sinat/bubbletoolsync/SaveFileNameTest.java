package com.sinat.bubbletoolsync;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Checks the saved file's name. Windows reserves some names that are also valid player names (Con, Aux, Nul,
 * Prn, Com1, Lpt1, ...), and RuneLite won't make a file called "Con.txt", so every name starts with "account-".
 */
public class SaveFileNameTest
{
	@Test
	public void startsWithAccount()
	{
		assertEquals("account-VersaceSinat.txt", BubbleToolSyncPlugin.fileNameFor("VersaceSinat"));
		assertEquals("account-Iron Bob.txt", BubbleToolSyncPlugin.fileNameFor("Iron Bob"));
	}

	@Test
	public void windowsReservedNamesAreNoLongerReserved()
	{
		// RuneLite only checks the part before the first dot, so "account-con" is an ordinary name.
		assertEquals("account-Con.txt", BubbleToolSyncPlugin.fileNameFor("Con"));
		assertEquals("account-Lpt1.txt", BubbleToolSyncPlugin.fileNameFor("Lpt1"));
	}
}
