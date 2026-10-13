package com.gepricetracker;

import com.gepricetracker.wiki.WikiModels.TimeseriesPoint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * Line chart of avg instant-buy (high) and instant-sell (low) prices over time.
 * Hover to see the exact values at a point.
 */
class PriceGraph extends JPanel
{
	static final Color HIGH_COLOR = new Color(255, 152, 31);
	static final Color LOW_COLOR = new Color(63, 167, 255);

	private static final Color GRID_COLOR = new Color(255, 255, 255, 25);
	private static final Color TEXT_COLOR = ColorScheme.LIGHT_GRAY_COLOR;
	private static final DateTimeFormatter HOVER_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm");
	private static final DateTimeFormatter AXIS_FORMAT = DateTimeFormatter.ofPattern("MMM d");

	private static final int PAD_LEFT = 6;
	private static final int PAD_RIGHT = 6;
	private static final int PAD_TOP = 18;
	private static final int PAD_BOTTOM = 16;

	private List<TimeseriesPoint> points = Collections.emptyList();
	private String message = "No data";
	private int hoverIndex = -1;

	PriceGraph()
	{
		setPreferredSize(new Dimension(0, 180));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int n = points.size();
				if (n == 0)
				{
					return;
				}
				int plotWidth = getWidth() - PAD_LEFT - PAD_RIGHT;
				int idx = n == 1 ? 0 : Math.round((e.getX() - PAD_LEFT) * (n - 1) / (float) plotWidth);
				idx = Math.max(0, Math.min(n - 1, idx));
				if (idx != hoverIndex)
				{
					hoverIndex = idx;
					repaint();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hoverIndex = -1;
				repaint();
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	void setData(List<TimeseriesPoint> data)
	{
		points = data;
		message = data.isEmpty() ? "No trades in this period" : null;
		hoverIndex = -1;
		repaint();
	}

	void setMessage(String text)
	{
		points = Collections.emptyList();
		message = text;
		hoverIndex = -1;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setFont(FontManager.getRunescapeSmallFont());
			paintGraph(g2);
		}
		finally
		{
			g2.dispose();
		}
	}

	private void paintGraph(Graphics2D g)
	{
		int w = getWidth();
		int h = getHeight();
		FontMetrics fm = g.getFontMetrics();

		if (message != null)
		{
			drawCentered(g, message, w / 2, h / 2);
			return;
		}

		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (TimeseriesPoint p : points)
		{
			for (Integer v : new Integer[]{p.getAvgHighPrice(), p.getAvgLowPrice()})
			{
				if (v != null)
				{
					min = Math.min(min, v);
					max = Math.max(max, v);
				}
			}
		}
		if (min > max)
		{
			drawCentered(g, "No trades in this period", w / 2, h / 2);
			return;
		}
		if (min == max)
		{
			min -= 1;
			max += 1;
		}

		int left = PAD_LEFT;
		int right = w - PAD_RIGHT;
		int top = PAD_TOP;
		int bottom = h - PAD_BOTTOM;
		final long fMin = min;
		final long fMax = max;
		Function<Integer, Integer> yOf = v -> bottom - (int) Math.round((v - fMin) * (double) (bottom - top) / (fMax - fMin));

		// Grid lines with price labels
		g.setColor(GRID_COLOR);
		for (int i = 0; i <= 3; i++)
		{
			int y = top + (bottom - top) * i / 3;
			g.drawLine(left, y, right, y);
		}
		g.setColor(TEXT_COLOR);
		g.drawString(QuantityFormatter.quantityToStackSize(max), left + 2, top + fm.getAscent() + 1);
		g.drawString(QuantityFormatter.quantityToStackSize(min), left + 2, bottom - 3);

		// Legend
		int lx = right - fm.stringWidth("Buy  Sell") - 16;
		g.setColor(HIGH_COLOR);
		g.fillRect(lx, 5, 6, 6);
		g.setColor(TEXT_COLOR);
		g.drawString("Buy", lx + 9, 12);
		lx += 9 + fm.stringWidth("Buy ") + 4;
		g.setColor(LOW_COLOR);
		g.fillRect(lx, 5, 6, 6);
		g.setColor(TEXT_COLOR);
		g.drawString("Sell", lx + 9, 12);

		// Date labels
		ZoneId zone = ZoneId.systemDefault();
		String start = AXIS_FORMAT.format(Instant.ofEpochSecond(points.get(0).getTimestamp()).atZone(zone));
		String end = AXIS_FORMAT.format(Instant.ofEpochSecond(points.get(points.size() - 1).getTimestamp()).atZone(zone));
		g.drawString(start, left, h - 3);
		g.drawString(end, right - fm.stringWidth(end), h - 3);

		// Lines
		Stroke oldStroke = g.getStroke();
		g.setStroke(new BasicStroke(1.5f));
		drawSeries(g, TimeseriesPoint::getAvgLowPrice, LOW_COLOR, left, right, yOf);
		drawSeries(g, TimeseriesPoint::getAvgHighPrice, HIGH_COLOR, left, right, yOf);
		g.setStroke(oldStroke);

		if (hoverIndex >= 0 && hoverIndex < points.size())
		{
			drawHover(g, fm, left, right, top, bottom, yOf, zone);
		}
	}

	private int xOf(int index, int left, int right)
	{
		int n = points.size();
		return n == 1 ? (left + right) / 2 : left + (right - left) * index / (n - 1);
	}

	private void drawSeries(Graphics2D g, Function<TimeseriesPoint, Integer> getter, Color color,
		int left, int right, Function<Integer, Integer> yOf)
	{
		g.setColor(color);
		int prevX = -1;
		int prevY = -1;
		for (int i = 0; i < points.size(); i++)
		{
			Integer v = getter.apply(points.get(i));
			if (v == null)
			{
				// Gap in trading data: break the line
				prevX = -1;
				continue;
			}
			int x = xOf(i, left, right);
			int y = yOf.apply(v);
			if (prevX >= 0)
			{
				g.drawLine(prevX, prevY, x, y);
			}
			else
			{
				g.fillRect(x - 1, y - 1, 2, 2);
			}
			prevX = x;
			prevY = y;
		}
	}

	private void drawHover(Graphics2D g, FontMetrics fm, int left, int right, int top, int bottom,
		Function<Integer, Integer> yOf, ZoneId zone)
	{
		TimeseriesPoint p = points.get(hoverIndex);
		int x = xOf(hoverIndex, left, right);

		g.setColor(new Color(255, 255, 255, 90));
		g.drawLine(x, top, x, bottom);

		if (p.getAvgHighPrice() != null)
		{
			g.setColor(HIGH_COLOR);
			int y = yOf.apply(p.getAvgHighPrice());
			g.fillOval(x - 3, y - 3, 6, 6);
		}
		if (p.getAvgLowPrice() != null)
		{
			g.setColor(LOW_COLOR);
			int y = yOf.apply(p.getAvgLowPrice());
			g.fillOval(x - 3, y - 3, 6, 6);
		}

		String[] lines = {
			HOVER_FORMAT.format(Instant.ofEpochSecond(p.getTimestamp()).atZone(zone)),
			"Buy: " + format(p.getAvgHighPrice()),
			"Sell: " + format(p.getAvgLowPrice()),
		};
		Color[] colors = {Color.WHITE, HIGH_COLOR, LOW_COLOR};

		int boxW = 0;
		for (String line : lines)
		{
			boxW = Math.max(boxW, fm.stringWidth(line));
		}
		boxW += 8;
		int lineH = fm.getHeight();
		int boxH = lineH * lines.length + 4;
		int boxX = x + 6 + boxW > right ? x - 6 - boxW : x + 6;
		int boxY = top + 2;

		g.setColor(new Color(20, 20, 20, 220));
		g.fillRoundRect(boxX, boxY, boxW, boxH, 4, 4);
		for (int i = 0; i < lines.length; i++)
		{
			g.setColor(colors[i]);
			g.drawString(lines[i], boxX + 4, boxY + 2 + lineH * (i + 1) - fm.getDescent());
		}
	}

	private void drawCentered(Graphics2D g, String text, int cx, int cy)
	{
		FontMetrics fm = g.getFontMetrics();
		g.setColor(TEXT_COLOR);
		g.drawString(text, cx - fm.stringWidth(text) / 2, cy + fm.getAscent() / 2);
	}

	private static String format(Integer price)
	{
		return price == null ? "-" : QuantityFormatter.formatNumber(price) + " gp";
	}
}
