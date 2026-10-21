# GE Price Tracker

A clean, responsive [RuneLite](https://runelite.net) plugin for checking Grand Exchange prices, inspecting item price history graphs, and tracking inventory and bank wealth in real time.

---

## Features

| 🔍 Item Lookup | 💰 Net Worth & Valuation | ⚙️ Configuration |
| :---:|:---:|:---:|
| <img width="230" alt="Lookup Tab" src="screenshots/lookup-tab.png" /> | <img width="230" alt="Value Tab" src="screenshots/value-tab.png" /> | <img width="230" alt="Plugin Configuration" src="screenshots/configuration.png" /> |
| Search GE guide prices, high alch values, margins, & history | Track live inventory, bank, and combined account wealth | Toggle live Wiki prices, default graph range, & top items |

### 1. Lookup Tab
- Search any item to instantly view its **GE guide price** and **High Alchemy value**.
- When **Live Wiki Prices** are enabled:
  - View instant buy and sell prices.
  - Calculate flip margins after GE tax.
  - Explore interactive price history graphs (`1D` / `2W` / `3M` / `1Y`).

### 2. Value Tab
- **Live Inventory Value:** Real-time net worth of items in your bag.
- **Bank Value:** Calculated automatically when opening your bank.
- **Combined Total:** Total account value across inventory and bank.
- **Top Items:** Quick breakdown of your most valuable possessions.

---

## Live Wiki Prices & Privacy

Real-time price quotes and historical charts are powered by the official [OSRS Wiki real-time prices API](https://prices.runescape.wiki/).

> [!NOTE]  
> **Disabled by default:** This feature connects to an external, third-party server. You can enable it anytime in the plugin configuration.

---

## Configuration

| Setting | Default | Description |
|---|---|---|
| **Live Wiki prices & history** | `Disabled` | Fetch real-time buy/sell prices, margins, and history graphs from the OSRS Wiki API. |
| **Default graph range** | `2W` | Default timeframe shown on the item price history graph (1D, 2W, 3M, 1Y). |
| **Top items shown** | `10` | Number of top valuable items displayed in the Value tab (1–20). |

---

## Running in Development

Run with the included development launcher:
```powershell
.\run-dev.bat
```

Or build with Gradle:
```powershell
.\gradlew.bat run
```

---

## License

This project is licensed under the BSD 2-Clause License - see the [LICENSE](LICENSE) file for details.
