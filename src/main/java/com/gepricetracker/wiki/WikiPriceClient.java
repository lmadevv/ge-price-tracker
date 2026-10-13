package com.gepricetracker.wiki;

import com.gepricetracker.Timestep;
import com.gepricetracker.wiki.WikiModels.LatestPrice;
import com.gepricetracker.wiki.WikiModels.LatestResponse;
import com.gepricetracker.wiki.WikiModels.TimeseriesPoint;
import com.gepricetracker.wiki.WikiModels.TimeseriesResponse;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Async client for https://prices.runescape.wiki/api/v1/osrs/.
 * Callbacks run on the OkHttp thread pool, so callers must hop to the EDT/client thread themselves.
 */
@Slf4j
@Singleton
public class WikiPriceClient
{
	private static final HttpUrl BASE_URL = HttpUrl.get("https://prices.runescape.wiki/api/v1/osrs/");

	// The Wiki asks for a descriptive User-Agent with contact info.
	// TODO: replace the contact with your Discord/GitHub before publishing.
	private static final String USER_AGENT = "GE Price Tracker RuneLite plugin - contact - https://github.com/lmadevv/ge-price-tracker";

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	WikiPriceClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * onSuccess receives null if the item has no recorded trades.
	 */
	public void fetchLatest(int itemId, Consumer<LatestPrice> onSuccess, Consumer<String> onError)
	{
		HttpUrl url = BASE_URL.newBuilder()
			.addPathSegment("latest")
			.addQueryParameter("id", String.valueOf(itemId))
			.build();

		get(url, body ->
		{
			LatestResponse response = gson.fromJson(body, LatestResponse.class);
			onSuccess.accept(response == null || response.data == null
				? null
				: response.data.get(String.valueOf(itemId)));
		}, onError);
	}

	public void fetchTimeseries(int itemId, Timestep timestep, Consumer<List<TimeseriesPoint>> onSuccess, Consumer<String> onError)
	{
		HttpUrl url = BASE_URL.newBuilder()
			.addPathSegment("timeseries")
			.addQueryParameter("timestep", timestep.getApiValue())
			.addQueryParameter("id", String.valueOf(itemId))
			.build();

		get(url, body ->
		{
			TimeseriesResponse response = gson.fromJson(body, TimeseriesResponse.class);
			List<TimeseriesPoint> points = response == null || response.data == null
				? Collections.emptyList()
				: response.data;

			// Keep only the most recent points for the chosen range
			int max = timestep.getMaxPoints();
			if (points.size() > max)
			{
				points = points.subList(points.size() - max, points.size());
			}
			onSuccess.accept(points);
		}, onError);
	}

	private void get(HttpUrl url, Consumer<String> onBody, Consumer<String> onError)
	{
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Wiki price request failed: {}", url, e);
				onError.accept("Couldn't reach the OSRS Wiki");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.debug("Wiki price request returned {}: {}", response.code(), url);
						onError.accept("Wiki returned HTTP " + response.code());
						return;
					}
					onBody.accept(body.string());
				}
				catch (IOException | JsonParseException e)
				{
					log.debug("Bad Wiki price response: {}", url, e);
					onError.accept("Unexpected response from the Wiki");
				}
			}
		});
	}
}
