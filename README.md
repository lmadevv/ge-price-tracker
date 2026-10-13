# GE Price Tracker

A RuneLite plugin that adds a sidebar panel with two tabs:

- **Lookup** – search any item to see its GE guide price and high alch value. With live Wiki prices enabled, it also shows the instant buy/sell prices, the flip margin after GE tax, and an interactive price history graph (1D / 2W / 3M / 1Y).
- **Value** – live inventory value, bank value (calculated once you open your bank), the combined total, and your most valuable items.

## Live Wiki prices

Live prices and history come from the [OSRS Wiki real-time prices API](https://prices.runescape.wiki/). This is **off by default** because it sends requests to a third-party server. Turn it on in the plugin config.

Before you publish, set `USER_AGENT` in `WikiPriceClient.java` to something that identifies you (e.g. your Discord or GitHub). The Wiki asks for this.

## Running

Requires JDK 11+.

```
./gradlew run
```