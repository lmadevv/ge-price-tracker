package com.gepricetracker;

import lombok.Value;

/**
 * An item search result, with prices resolved on the client thread.
 */
@Value
public class SearchResult
{
	int id;
	String name;
	long gePrice;
	int haPrice;
}
