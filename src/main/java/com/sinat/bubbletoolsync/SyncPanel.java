package com.sinat.bubbletoolsync;

import com.sinat.bubbletoolsync.qr.QrCode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: the account name, when it was read, the QR code, and a "Copy text" button.
 * Everything here runs on Swing's thread (the "EDT"), never the game's.
 */
class SyncPanel extends PluginPanel
{
	private static final DateTimeFormatter READ_AT_FORMAT =
		DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
	/** Space around the panel's edges, in pixels. */
	private static final int SIDE_GAP = 6;

	private final Runnable onOpen;

	private final JLabel nameLabel = new JLabel();
	private final JLabel questPointsLabel = new JLabel();
	private final JLabel readAtLabel = new JLabel();
	private final QrView qrView = new QrView();
	private final JButton copyButton = new JButton("Copy text");
	private final JLabel messageLabel = new JLabel();

	/** The text in the QR code right now, or null if there isn't one yet. */
	private String text;

	/**
	 * @param onOpen what to do each time the player opens this panel (the plugin reads the game again)
	 */
	SyncPanel(Runnable onOpen)
	{
		// false: no scroll bar, so the QR code can use the panel's full width.
		super(false);
		this.onOpen = onOpen;

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

		JPanel bottom = new JPanel(new BorderLayout(0, 6));
		bottom.setOpaque(false);
		bottom.add(copyButton, BorderLayout.NORTH);
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
			return;
		}

		text = newText;
		nameLabel.setText(data.getAccountName());
		questPointsLabel.setText("Quest points: " + data.getQuestPoints());
		readAtLabel.setText("Read " + READ_AT_FORMAT.format(
			Instant.ofEpochSecond(data.getReadAt()).atZone(ZoneId.systemDefault())));
		qrView.setQr(qr);
		copyButton.setEnabled(true);
		messageLabel.setText("Scan with Bubble Tool on your phone");
	}

	/** Shows a message in place of the QR code, for example before the player logs in. */
	void showNothing()
	{
		text = null;
		nameLabel.setText(" ");
		questPointsLabel.setText(" ");
		readAtLabel.setText(" ");
		qrView.setQr(null);
		copyButton.setEnabled(false);
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
