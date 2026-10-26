package com.gepricetracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "GE Price Tracker",
	description = "Search GE prices, view price history graphs, and track your bank and inventory value",
	tags = {"grand", "exchange", "ge", "price", "graph", "history", "bank", "value", "wiki"}
)
public class GePriceTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private GePriceTrackerConfig config;

	private GePriceTrackerPanel panel;
	private NavigationButton navButton;

	// Only touched on the client thread
	private List<HeldItem> inventoryItems = Collections.emptyList();
	private List<HeldItem> bankItems; // null until the bank is opened this session

	@Override
	protected void startUp()
	{
		panel = injector.getInstance(GePriceTrackerPanel.class);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("GE Price Tracker")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// If the plugin is enabled mid-session, pick up whatever is already loaded
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				inventoryItems = readContainer(client.getItemContainer(InventoryID.INV));
				ItemContainer bank = client.getItemContainer(InventoryID.BANK);
				bankItems = bank == null ? null : readContainer(bank);
			}
			publishValues();
		});
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		inventoryItems = Collections.emptyList();
		bankItems = null;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV)
		{
			inventoryItems = readContainer(event.getItemContainer());
			publishValues();
		}
		else if (id == InventoryID.BANK)
		{
			bankItems = readContainer(event.getItemContainer());
			publishValues();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Don't let one account's bank carry over to the next
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			inventoryItems = Collections.emptyList();
			bankItems = null;
			publishValues();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!GePriceTrackerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("favorites".equals(event.getKey()))
		{
			return;
		}

		clientThread.invokeLater(this::publishValues);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.onConfigChanged();
			}
		});
	}

	/**
	 * Must be called on the client thread. Merges noted/unnoted stacks and skips bank placeholders.
	 */
	private List<HeldItem> readContainer(ItemContainer container)
	{
		if (container == null)
		{
			return Collections.emptyList();
		}

		Map<Integer, Long> quantities = new LinkedHashMap<>();
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}

			ItemComposition composition = itemManager.getItemComposition(item.getId());
			if (composition.getPlaceholderTemplateId() != -1)
			{
				continue;
			}

			int canonicalId = itemManager.canonicalize(item.getId());
			quantities.merge(canonicalId, (long) item.getQuantity(), Long::sum);
		}

		List<HeldItem> items = new ArrayList<>(quantities.size());
		for (Map.Entry<Integer, Long> entry : quantities.entrySet())
		{
			int id = entry.getKey();
			items.add(new HeldItem(
				id,
				itemManager.getItemComposition(id).getName(),
				entry.getValue(),
				itemManager.getItemPrice(id)));
		}
		return items;
	}

	/**
	 * Must be called on the client thread.
	 */
	private void publishValues()
	{
		long inventoryTotal = sum(inventoryItems);
		Long bankTotal = bankItems == null ? null : sum(bankItems);

		Map<Integer, HeldItem> merged = new LinkedHashMap<>();
		for (HeldItem item : inventoryItems)
		{
			merged.merge(item.getId(), item, HeldItem::plus);
		}
		if (bankItems != null)
		{
			for (HeldItem item : bankItems)
			{
				merged.merge(item.getId(), item, HeldItem::plus);
			}
		}

		List<HeldItem> topItems = merged.values().stream()
			.filter(i -> i.getTotal() > 0)
			.sorted(Comparator.comparingLong(HeldItem::getTotal).reversed())
			.limit(config.topItemsCount())
			.collect(Collectors.toList());

		ValueSnapshot snapshot = new ValueSnapshot(inventoryTotal, bankTotal, topItems);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.updateValues(snapshot);
			}
		});
	}

	private static long sum(List<HeldItem> items)
	{
		long total = 0;
		for (HeldItem item : items)
		{
			total += item.getTotal();
		}
		return total;
	}

	@Provides
	GePriceTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GePriceTrackerConfig.class);
	}
}
