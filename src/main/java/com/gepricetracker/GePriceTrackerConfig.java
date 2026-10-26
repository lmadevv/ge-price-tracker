package com.gepricetracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(GePriceTrackerConfig.GROUP)
public interface GePriceTrackerConfig extends Config
{
	String GROUP = "gepricetracker";

	@ConfigItem(
		keyName = "useWikiPrices",
		name = "Live Wiki prices & history",
		description = "Fetch real-time buy/sell prices and price history graphs from the OSRS Wiki prices API",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 1
	)
	default boolean useWikiPrices()
	{
		return false;
	}

	@ConfigItem(
		keyName = "defaultTimestep",
		name = "Default graph range",
		description = "The time range the price history graph shows when you open an item",
		position = 2
	)
	default Timestep defaultTimestep()
	{
		return Timestep.TWO_WEEKS;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "topItemsCount",
		name = "Top items shown",
		description = "How many of your most valuable items to list on the Value tab",
		position = 3
	)
	default int topItemsCount()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "favorites",
		name = "Favorites",
		description = "Stored favorite item IDs",
		hidden = true
	)
	default String favorites()
	{
		return "";
	}
}
