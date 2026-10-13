package com.gepricetracker;

import java.util.List;
import lombok.Value;

/**
 * Snapshot of the player's wealth, built on the client thread and handed to the UI.
 */
@Value
public class ValueSnapshot
{
	long inventoryTotal;
	/**
	 * Null when the bank hasn't been opened yet this session.
	 */
	Long bankTotal;
	List<HeldItem> topItems;
}
