package com.gepricetracker.wiki;

import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * Gson models for the OSRS Wiki real-time prices API.
 * Field names must match the JSON exactly.
 */
public final class WikiModels
{
	private WikiModels()
	{
	}

	/**
	 * /latest?id=X -> {"data": {"X": {...}}}
	 */
	static class LatestResponse
	{
		Map<String, LatestPrice> data;
	}

	@Getter
	public static class LatestPrice
	{
		/**
		 * Most recent instant-buy price (someone bought at this).
		 */
		private Integer high;
		private Long highTime;
		/**
		 * Most recent instant-sell price (someone sold at this).
		 */
		private Integer low;
		private Long lowTime;
	}

	/**
	 * /timeseries?timestep=T&id=X -> {"data": [{...}, ...]}
	 */
	static class TimeseriesResponse
	{
		List<TimeseriesPoint> data;
	}

	@Getter
	public static class TimeseriesPoint
	{
		/**
		 * Unix seconds.
		 */
		private long timestamp;
		private Integer avgHighPrice;
		private Integer avgLowPrice;
		private long highPriceVolume;
		private long lowPriceVolume;
	}
}
