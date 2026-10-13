package com.gepricetracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Graph ranges, mapped onto the Wiki timeseries API's timesteps.
 * The API returns up to 365 points per request; we trim to a tidy range.
 */
@Getter
@RequiredArgsConstructor
public enum Timestep
{
	ONE_DAY("1D", "5m", 288),
	TWO_WEEKS("2W", "1h", 336),
	THREE_MONTHS("3M", "6h", 360),
	ONE_YEAR("1Y", "24h", 365);

	private final String label;
	private final String apiValue;
	private final int maxPoints;

	@Override
	public String toString()
	{
		return label;
	}
}
