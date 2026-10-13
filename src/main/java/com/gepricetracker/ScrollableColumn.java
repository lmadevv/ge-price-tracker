package com.gepricetracker;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * A panel that always matches the scroll pane's width (so content never overflows sideways)
 * while scrolling vertically. Content is pinned to the top.
 */
class ScrollableColumn extends JPanel implements Scrollable
{
	private ScrollableColumn(JComponent content)
	{
		super(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(content, BorderLayout.NORTH);
	}

	static JScrollPane wrap(JComponent content)
	{
		JScrollPane scrollPane = new JScrollPane(new ScrollableColumn(content));
		scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		return scrollPane;
	}

	@Override
	public Dimension getPreferredScrollableViewportSize()
	{
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return 16;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return visibleRect.height;
	}

	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight()
	{
		return false;
	}
}
