package com.gepricetracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * "Value" tab: inventory, bank, and combined wealth plus the most valuable items held.
 */
class ValuePanel extends JPanel
{
	private final ItemManager itemManager;

	private final JLabel inventoryValue = statValue();
	private final JLabel bankValue = statValue();
	private final JLabel totalValue = statValue();
	private final JLabel totalNote = UiUtil.smallLabel(" ", ColorScheme.LIGHT_GRAY_COLOR);
	private final JPanel topList = new JPanel(new GridBagLayout());

	ValuePanel(ItemManager itemManager)
	{
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel(new GridBagLayout());
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(10, 0, 0, 0));
		int y = 0;

		content.add(statBox("Inventory", inventoryValue, null), UiUtil.row(y++, 0));
		content.add(statBox("Bank", bankValue, null), UiUtil.row(y++, 4));
		content.add(statBox("Total", totalValue, totalNote), UiUtil.row(y++, 4));

		JLabel header = new JLabel("Most valuable items");
		header.setForeground(Color.WHITE);
		header.setFont(FontManager.getRunescapeBoldFont());
		content.add(header, UiUtil.row(y++, 12));

		topList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(topList, UiUtil.row(y, 4));

		add(ScrollableColumn.wrap(content), BorderLayout.CENTER);

		update(new ValueSnapshot(0, null, List.of()));
	}

	void update(ValueSnapshot snapshot)
	{
		inventoryValue.setText(UiUtil.formatGp(snapshot.getInventoryTotal()));

		Long bank = snapshot.getBankTotal();
		if (bank == null)
		{
			bankValue.setText("Open your bank");
			bankValue.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			totalNote.setText("Inventory only until you open your bank");
		}
		else
		{
			bankValue.setText(UiUtil.formatGp(bank));
			bankValue.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
			totalNote.setText(" ");
		}

		long total = snapshot.getInventoryTotal() + (bank == null ? 0 : bank);
		totalValue.setText(UiUtil.formatGp(total) + " (" + QuantityFormatter.quantityToStackSize(total) + ")");

		topList.removeAll();
		List<HeldItem> items = snapshot.getTopItems();
		if (items.isEmpty())
		{
			topList.add(UiUtil.wrappingLabel("Log in to see your most valuable items."), UiUtil.row(0, 0));
		}
		else
		{
			for (int i = 0; i < items.size(); i++)
			{
				topList.add(buildItemRow(items.get(i)), UiUtil.row(i, i == 0 ? 0 : 2));
			}
		}
		topList.revalidate();
		topList.repaint();
	}

	private JPanel buildItemRow(HeldItem item)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setToolTipText(item.getName() + " - " + UiUtil.formatGp(item.getUnitPrice()) + " each");

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		int quantity = (int) Math.min(Integer.MAX_VALUE, item.getQuantity());
		itemManager.getImage(item.getId(), quantity, quantity > 1).addTo(icon);

		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setOpaque(false);
		JLabel name = new JLabel(item.getName());
		name.setForeground(Color.WHITE);
		text.add(name);
		text.add(UiUtil.smallLabel(UiUtil.formatGp(item.getTotal()), ColorScheme.GRAND_EXCHANGE_PRICE));

		row.add(icon, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);
		return row;
	}

	private static JPanel statBox(String title, JLabel value, JLabel note)
	{
		JPanel box = new JPanel(new GridLayout(note == null ? 2 : 3, 1, 0, 2));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(new EmptyBorder(6, 8, 6, 8));
		box.add(UiUtil.smallLabel(title, ColorScheme.LIGHT_GRAY_COLOR));
		box.add(value);
		if (note != null)
		{
			box.add(note);
		}
		return box;
	}

	private static JLabel statValue()
	{
		JLabel label = new JLabel();
		label.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
		label.setFont(FontManager.getRunescapeBoldFont());
		return label;
	}
}
