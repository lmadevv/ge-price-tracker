package com.gepricetracker;

import com.gepricetracker.wiki.WikiPriceClient;
import java.awt.BorderLayout;
import javax.inject.Inject;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Sidebar panel hosting the Lookup and Value tabs.
 */
class GePriceTrackerPanel extends PluginPanel
{
	private final LookupPanel lookupPanel;
	private final ValuePanel valuePanel;

	@Inject
	GePriceTrackerPanel(ItemManager itemManager, ClientThread clientThread, WikiPriceClient wikiClient, GePriceTrackerConfig config)
	{
		super(false);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		lookupPanel = new LookupPanel(itemManager, clientThread, wikiClient, config);
		valuePanel = new ValuePanel(itemManager);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		MaterialTab lookupTab = new MaterialTab("Lookup", tabGroup, lookupPanel);
		MaterialTab valueTab = new MaterialTab("Value", tabGroup, valuePanel);
		tabGroup.addTab(lookupTab);
		tabGroup.addTab(valueTab);
		tabGroup.select(lookupTab);

		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}

	void updateValues(ValueSnapshot snapshot)
	{
		valuePanel.update(snapshot);
	}

	void onConfigChanged()
	{
		lookupPanel.onConfigChanged();
	}
}
