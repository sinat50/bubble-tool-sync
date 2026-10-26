package com.sinat.bubbletoolsync;

import com.sinat.bubbletoolsync.qr.QrCode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.Filepath;

/**
 * The side panel: the account name, when it was read, the QR code, and "Copy text" and "Export file" buttons.
 * Everything here runs on Swing's thread (the "EDT"), never the game's.
 */
@Slf4j
class SyncPanel extends PluginPanel
{
	private static final DateTimeFormatter READ_AT_FORMAT =
		DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
	/** Space around the panel's edges, in pixels. */
	private static final int SIDE_GAP = 6;

	private final Runnable onOpen;
	/** RuneLite's background worker, for writing exported files without freezing the panel. */
	private final ScheduledExecutorService executor;

	private final JLabel nameLabel = new JLabel();
	private final JLabel questPointsLabel = new JLabel();
	private final JLabel readAtLabel = new JLabel();
	private final QrView qrView = new QrView();
	private final JButton copyButton = new JButton("Copy text");
	private final JButton exportButton = new JButton("Export file");
	private final JLabel messageLabel = new JLabel();

	/** The text in the QR code right now, or null if there isn't one yet. */
	private String text;
	/** The account the text belongs to, for the exported file's suggested name. */
	private String accountName;

	/**
	 * @param onOpen   what to do each time the player opens this panel (the plugin reads the game again)
	 * @param executor RuneLite's background worker, used to write exported files
	 */
	SyncPanel(Runnable onOpen, ScheduledExecutorService executor)
	{
		// false: no scroll bar, so the QR code can use the panel's full width.
		super(false);
		this.onOpen = onOpen;
		this.executor = executor;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(SIDE_GAP, SIDE_GAP, SIDE_GAP, SIDE_GAP));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Bubble Tool Sync");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);

		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(ColorScheme.BRAND_ORANGE);
		questPointsLabel.setFont(FontManager.getRunescapeSmallFont());
		questPointsLabel.setForeground(Color.WHITE);
		readAtLabel.setFont(FontManager.getRunescapeSmallFont());
		readAtLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		// One line each, top to bottom.
		JPanel top = new JPanel(new GridLayout(0, 1, 0, 2));
		top.setOpaque(false);
		top.add(title);
		top.add(nameLabel);
		top.add(questPointsLabel);
		top.add(readAtLabel);

		messageLabel.setFont(FontManager.getRunescapeSmallFont());
		messageLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

		copyButton.setFocusable(false);
		copyButton.setToolTipText("Copies the text inside the QR code");
		copyButton.addActionListener(e -> copyText());

		exportButton.setFocusable(false);
		exportButton.setToolTipText("Saves the text inside the QR code to a file, in a folder you choose");
		exportButton.addActionListener(e -> exportFile());

		// The two buttons side by side.
		JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
		buttons.setOpaque(false);
		buttons.add(copyButton);
		buttons.add(exportButton);

		JPanel bottom = new JPanel(new BorderLayout(0, 6));
		bottom.setOpaque(false);
		bottom.add(buttons, BorderLayout.NORTH);
		bottom.add(messageLabel, BorderLayout.SOUTH);

		// Everything stacked at the top of the panel.
		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.setOpaque(false);
		content.add(top, BorderLayout.NORTH);
		content.add(qrView, BorderLayout.CENTER);
		content.add(bottom, BorderLayout.SOUTH);
		add(content, BorderLayout.NORTH);

		showNothing();
	}

	/**
	 * Shows a new reading.
	 *
	 * @param newText the reading packed as text ({@link SyncData#toText()})
	 */
	void showData(SyncData data, String newText)
	{
		accountName = data.getAccountName();
		QrCode qr;
		try
		{
			// LOW error correction keeps the code as small as possible; a screen gives a clean picture.
			// The generator raises it for free if there's room.
			qr = QrCode.encodeText(newText, QrCode.Ecc.LOW);
		}
		catch (RuntimeException e)
		{
			// Shouldn't happen (a QR code holds about 2,900 bytes; ours is about 600), but don't crash the panel.
			showMessage("Couldn't make the QR code. Use Copy text instead.");
			text = newText;
			copyButton.setEnabled(true);
			exportButton.setEnabled(true);
			return;
		}

		text = newText;
		nameLabel.setText(data.getAccountName());
		questPointsLabel.setText("Quest points: " + data.getQuestPoints());
		readAtLabel.setText("Read " + READ_AT_FORMAT.format(
			Instant.ofEpochSecond(data.getReadAt()).atZone(ZoneId.systemDefault())));
		qrView.setQr(qr);
		copyButton.setEnabled(true);
		exportButton.setEnabled(true);
		messageLabel.setText("Scan with Bubble Tool on your phone");
	}

	/** Shows a message in place of the QR code, for example before the player logs in. */
	void showNothing()
	{
		text = null;
		accountName = null;
		nameLabel.setText(" ");
		questPointsLabel.setText(" ");
		readAtLabel.setText(" ");
		qrView.setQr(null);
		copyButton.setEnabled(false);
		exportButton.setEnabled(false);
		messageLabel.setText("Log in to the game to see your QR code");
	}

	private void showMessage(String message)
	{
		qrView.setQr(null);
		messageLabel.setText(message);
	}

	private void copyText()
	{
		if (text != null)
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			messageLabel.setText("Copied");
		}
	}

	/**
	 * Asks where to save, then writes the same text as the QR code to that file (UTF-8, one line, no line break
	 * at the end), for the Bubble Tool app to import.
	 */
	private void exportFile()
	{
		if (text == null)
		{
			return;
		}
		// Keep the text as it is now, in case a new reading arrives while the dialog is open.
		String exportText = text;

		// RuneLite's own file chooser (Plugin Hub plugins must use it instead of Java's JFileChooser).
		Filepath.Chooser chooser = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Export for Bubble Tool")
			.addExtensionFilter("Text file (*.txt)", "txt")
			.setDefaultExtension("txt")
			.setFileName(BubbleToolSyncPlugin.fileNameFor(accountName));
		List<Filepath> chosen = chooser.showDialog(this);
		if (chosen == null || chosen.isEmpty())
		{
			// The player cancelled.
			return;
		}
		Filepath file = chosen.get(0);

		if (file.exists())
		{
			int answer = JOptionPane.showConfirmDialog(this,
				file.getFileName() + " already exists. Replace it?", "Export for Bubble Tool",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer != JOptionPane.YES_OPTION)
			{
				return;
			}
		}

		messageLabel.setText("Exporting...");
		executor.execute(() ->
		{
			String result;
			try
			{
				file.write(exportText);
				result = "Exported " + file.getFileName();
			}
			catch (IOException e)
			{
				log.warn("Couldn't export the file", e);
				result = "Couldn't export the file";
			}
			String message = result;
			SwingUtilities.invokeLater(() -> messageLabel.setText(message));
		});
	}

	@Override
	public void onActivate()
	{
		onOpen.run();
	}

	/**
	 * Draws the QR code as big as the panel allows: black squares on white, with the white border ("quiet zone")
	 * of 4 squares that QR scanners need around the code.
	 */
	private static class QrView extends JComponent
	{
		private static final int QUIET_ZONE = 4;

		private QrCode qr;

		QrView()
		{
			// Square, filling the panel's width. Without a scroll bar the panel is PANEL_WIDTH + SCROLLBAR_WIDTH
			// wide; take off the gaps on both sides.
			int side = PANEL_WIDTH + SCROLLBAR_WIDTH - 2 * SIDE_GAP;
			setPreferredSize(new Dimension(side, side));
		}

		void setQr(QrCode qr)
		{
			this.qr = qr;
			setVisible(qr != null);
			revalidate();
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			if (qr == null)
			{
				return;
			}

			int side = Math.min(getWidth(), getHeight());
			int left = (getWidth() - side) / 2;
			int top = (getHeight() - side) / 2;
			int cells = qr.size + 2 * QUIET_ZONE;

			g.setColor(Color.WHITE);
			g.fillRect(left, top, side, side);

			// Each square's edges are worked out from the whole size, so the squares fill the space exactly
			// with no gaps, even when the size doesn't divide evenly.
			g.setColor(Color.BLACK);
			for (int y = 0; y < qr.size; y++)
			{
				int y0 = top + (y + QUIET_ZONE) * side / cells;
				int y1 = top + (y + QUIET_ZONE + 1) * side / cells;
				for (int x = 0; x < qr.size; x++)
				{
					if (qr.getModule(x, y))
					{
						int x0 = left + (x + QUIET_ZONE) * side / cells;
						int x1 = left + (x + QUIET_ZONE + 1) * side / cells;
						g.fillRect(x0, y0, x1 - x0, y1 - y0);
					}
				}
			}
		}
	}
}
