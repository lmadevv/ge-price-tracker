package com.gepricetracker;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.JLabel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

final class UiUtil
{
	static final Color POSITIVE = new Color(80, 200, 120);
	static final Color NEGATIVE = new Color(230, 80, 80);

	private static final long GE_TAX_CAP = 5_000_000L;
	private static final int GE_TAX_EXEMPT_BELOW = 50;

	private UiUtil()
	{
	}

	static String formatGp(long value)
	{
		return QuantityFormatter.formatNumber(value) + " gp";
	}

	/**
	 * GE sale tax: 2% of the sale price, rounded down, capped at 5m. Items sold under 50 gp are exempt.
	 */
	static long geTax(long sellPrice)
	{
		if (sellPrice < GE_TAX_EXEMPT_BELOW)
		{
			return 0;
		}
		return Math.min(sellPrice * 2 / 100, GE_TAX_CAP);
	}

	static String timeAgo(long epochSeconds)
	{
		long seconds = Math.max(0, System.currentTimeMillis() / 1000 - epochSeconds);
		if (seconds < 60)
		{
			return "just now";
		}
		if (seconds < 3600)
		{
			return (seconds / 60) + "m ago";
		}
		if (seconds < 86400)
		{
			return (seconds / 3600) + "h ago";
		}
		return (seconds / 86400) + "d ago";
	}

	/**
	 * A JLabel that word-wraps to the panel width.
	 */
	static JLabel wrappingLabel(String html)
	{
		JLabel label = new JLabel("<html>" + html + "</html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	static JLabel smallLabel(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	/**
	 * Constraints for a full-width row in a single-column GridBagLayout.
	 */
	static GridBagConstraints row(int y, int topGap)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = y;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTH;
		c.insets = new Insets(topGap, 0, 0, 0);
		return c;
	}
}
