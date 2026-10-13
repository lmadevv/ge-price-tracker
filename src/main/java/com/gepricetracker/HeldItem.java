package com.gepricetracker;

import lombok.Value;

/**
 * A stack of one item (by canonical/unnoted id) that the player holds.
 */
@Value
public class HeldItem
{
	int id;
	String name;
	long quantity;
	long unitPrice;

	public long getTotal()
	{
		return quantity * unitPrice;
	}

	public HeldItem plus(HeldItem other)
	{
		return new HeldItem(id, name, quantity + other.quantity, unitPrice);
	}
}
