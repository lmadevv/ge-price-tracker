package com.gepricetracker;

import com.gepricetracker.wiki.WikiModels.LatestPrice;
import com.gepricetracker.wiki.WikiPriceClient;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.LinkBrowser;
import net.runelite.http.api.item.ItemPrice;

/**
 * "Lookup" tab: item search -> results list -> item detail with live prices and a history graph.
 */
class LookupPanel extends JPanel
{
	private static final int MAX_RESULTS = 30;
	private static final String RESULTS_CARD = "results";
	private static final String DETAIL_CARD = "detail";
	private static final String WIKI_ITEM_URL = "https://prices.runescape.wiki/osrs/item/";

	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final WikiPriceClient wikiClient;
	private final GePriceTrackerConfig config;

	private final IconTextField searchBar = new IconTextField();
	private final CardLayout cards = new CardLayout();
	private final JPanel cardPanel = new JPanel(cards);
	private final JPanel resultsList = new JPanel(new GridBagLayout());

	// Detail view
	private final JLabel detailIcon = new JLabel();
	private final JLabel detailName = new JLabel();
	private final JLabel geValue = valueLabel();
	private final JLabel haValue = valueLabel();
	private final JLabel buyValue = valueLabel();
	private final JLabel sellValue = valueLabel();
	private final JLabel marginValue = valueLabel();
	private final JLabel taxedMarginValue = valueLabel();
	private final JLabel updatedValue = valueLabel();
	private final JPanel wikiRows = new JPanel(new GridLayout(0, 2, 0, 4));
	private final JLabel wikiStatus = UiUtil.wrappingLabel("");
	private final JPanel timestepRow = new JPanel(new GridLayout(1, Timestep.values().length, 2, 0));
	private final Map<Timestep, JToggleButton> timestepButtons = new EnumMap<>(Timestep.class);
	private final PriceGraph graph = new PriceGraph();

	private SearchResult current;
	private Timestep timestep;
	private int searchSeq;

	LookupPanel(ItemManager itemManager, ClientThread clientThread, WikiPriceClient wikiClient, GePriceTrackerConfig config)
	{
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.wikiClient = wikiClient;
		this.config = config;
		this.timestep = config.defaultTimestep();

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(0, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.addActionListener(e -> search());
		searchBar.addClearListener(() ->
		{
			searchSeq++;
			searchBar.setIcon(IconTextField.Icon.SEARCH);
			showResultsMessage("Type an item name and press Enter.");
		});

		JPanel searchWrapper = new JPanel(new BorderLayout());
		searchWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchWrapper.setBorder(new EmptyBorder(10, 0, 8, 0));
		searchWrapper.add(searchBar, BorderLayout.CENTER);

		resultsList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		cardPanel.add(ScrollableColumn.wrap(resultsList), RESULTS_CARD);
		cardPanel.add(ScrollableColumn.wrap(buildDetailView()), DETAIL_CARD);

		add(searchWrapper, BorderLayout.NORTH);
		add(cardPanel, BorderLayout.CENTER);

		showResultsMessage("Type an item name and press Enter.");
	}

	void onConfigChanged()
	{
		if (current != null)
		{
			refreshWiki();
		}
	}

	// ---------------------------------------------------------------- search

	private void search()
	{
		String query = searchBar.getText().trim();
		if (query.isEmpty())
		{
			showResultsMessage("Type an item name and press Enter.");
			return;
		}

		final int seq = ++searchSeq;
		searchBar.setIcon(IconTextField.Icon.LOADING_DARKER);

		// Item compositions must be read on the client thread
		clientThread.invokeLater(() ->
		{
			String q = query.toLowerCase(Locale.ROOT);
			List<SearchResult> results = itemManager.search(query).stream()
				.sorted(Comparator
					.comparingInt((ItemPrice ip) -> relevance(ip.getName().toLowerCase(Locale.ROOT), q))
					.thenComparingInt(ip -> ip.getName().length())
					.thenComparing(ItemPrice::getName))
				.limit(MAX_RESULTS)
				.map(ip -> new SearchResult(
					ip.getId(),
					ip.getName(),
					ip.getPrice(),
					itemManager.getItemComposition(ip.getId()).getHaPrice()))
				.collect(Collectors.toList());

			SwingUtilities.invokeLater(() ->
			{
				if (seq != searchSeq)
				{
					return; // a newer search has started
				}
				searchBar.setIcon(IconTextField.Icon.SEARCH);
				showResults(results, query);
			});
		});
	}

	private static int relevance(String name, String query)
	{
		if (name.equals(query))
		{
			return 0;
		}
		return name.startsWith(query) ? 1 : 2;
	}

	private void showResults(List<SearchResult> results, String query)
	{
		if (results.isEmpty())
		{
			showResultsMessage("No tradeable items match \"" + query + "\".");
			return;
		}

		resultsList.removeAll();
		int y = 0;
		for (SearchResult result : results)
		{
			resultsList.add(buildResultRow(result), UiUtil.row(y, y == 0 ? 0 : 2));
			y++;
		}
		resultsList.revalidate();
		resultsList.repaint();
		cards.show(cardPanel, RESULTS_CARD);
	}

	private void showResultsMessage(String message)
	{
		resultsList.removeAll();
		JLabel label = UiUtil.wrappingLabel(message);
		label.setBorder(new EmptyBorder(6, 4, 0, 4));
		resultsList.add(label, UiUtil.row(0, 0));
		resultsList.revalidate();
		resultsList.repaint();
		cards.show(cardPanel, RESULTS_CARD);
	}

	private JPanel buildResultRow(SearchResult result)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText(result.getName());

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		itemManager.getImage(result.getId()).addTo(icon);

		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setOpaque(false);
		JLabel name = new JLabel(result.getName());
		name.setForeground(Color.WHITE);
		text.add(name);
		text.add(UiUtil.smallLabel(UiUtil.formatGp(result.getGePrice()), ColorScheme.GRAND_EXCHANGE_PRICE));

		row.add(icon, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				showDetail(result);
			}
		});
		return row;
	}

	// ---------------------------------------------------------------- detail

	private JPanel buildDetailView()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		int y = 0;

		JLabel back = UiUtil.smallLabel("< Back to results", ColorScheme.LIGHT_GRAY_COLOR);
		back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		back.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				current = null;
				cards.show(cardPanel, RESULTS_CARD);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				back.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				back.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		panel.add(back, UiUtil.row(y++, 0));

		// Header: icon + name
		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(6, 6, 6, 6));
		detailIcon.setPreferredSize(new Dimension(36, 32));
		detailName.setForeground(Color.WHITE);
		detailName.setFont(FontManager.getRunescapeBoldFont());
		header.add(detailIcon, BorderLayout.WEST);
		header.add(detailName, BorderLayout.CENTER);
		panel.add(header, UiUtil.row(y++, 6));

		// Always-available prices (from RuneLite)
		JPanel basicRows = new JPanel(new GridLayout(0, 2, 0, 4));
		basicRows.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		basicRows.setBorder(new EmptyBorder(6, 6, 6, 6));
		addRow(basicRows, "GE guide price", geValue);
		addRow(basicRows, "High alch", haValue);
		panel.add(basicRows, UiUtil.row(y++, 4));

		// Live Wiki prices
		wikiRows.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wikiRows.setBorder(new EmptyBorder(6, 6, 6, 6));
		addRow(wikiRows, "Instant buy", buyValue);
		addRow(wikiRows, "Instant sell", sellValue);
		addRow(wikiRows, "Margin", marginValue);
		addRow(wikiRows, "After GE tax", taxedMarginValue);
		addRow(wikiRows, "Last trade", updatedValue);
		buyValue.setForeground(PriceGraph.HIGH_COLOR);
		sellValue.setForeground(PriceGraph.LOW_COLOR);
		panel.add(wikiRows, UiUtil.row(y++, 4));

		wikiStatus.setBorder(new EmptyBorder(0, 2, 0, 2));
		panel.add(wikiStatus, UiUtil.row(y++, 6));

		// Graph range selector
		timestepRow.setOpaque(false);
		ButtonGroup group = new ButtonGroup();
		for (Timestep step : Timestep.values())
		{
			JToggleButton button = new JToggleButton(step.getLabel());
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setFocusPainted(false);
			button.setToolTipText(describe(step));
			button.addActionListener(e ->
			{
				if (timestep != step)
				{
					timestep = step;
					loadGraph();
				}
			});
			group.add(button);
			timestepButtons.put(step, button);
			timestepRow.add(button);
		}
		panel.add(timestepRow, UiUtil.row(y++, 8));
		panel.add(graph, UiUtil.row(y++, 4));

		JButton wikiLink = new JButton("View on OSRS Wiki");
		wikiLink.setFont(FontManager.getRunescapeSmallFont());
		wikiLink.setFocusPainted(false);
		wikiLink.addActionListener(e ->
		{
			if (current != null)
			{
				LinkBrowser.browse(WIKI_ITEM_URL + current.getId());
			}
		});
		panel.add(wikiLink, UiUtil.row(y, 8));

		return panel;
	}

	private void showDetail(SearchResult result)
	{
		current = result;
		detailIcon.setIcon(null);
		itemManager.getImage(result.getId()).addTo(detailIcon);
		detailName.setText("<html>" + result.getName() + "</html>");
		geValue.setText(UiUtil.formatGp(result.getGePrice()));
		haValue.setText(UiUtil.formatGp(result.getHaPrice()));

		timestep = config.defaultTimestep();
		timestepButtons.get(timestep).setSelected(true);

		cards.show(cardPanel, DETAIL_CARD);
		refreshWiki();
	}

	private void refreshWiki()
	{
		boolean enabled = config.useWikiPrices();
		wikiRows.setVisible(enabled);
		timestepRow.setVisible(enabled);
		graph.setVisible(enabled);

		if (!enabled)
		{
			wikiStatus.setText("<html>Turn on <b>Live Wiki prices &amp; history</b> in the plugin's config to see "
				+ "live buy/sell prices, flip margins and a price history graph.</html>");
			wikiStatus.setVisible(true);
			return;
		}

		wikiStatus.setVisible(false);
		for (JLabel label : new JLabel[]{buyValue, sellValue, marginValue, taxedMarginValue, updatedValue})
		{
			label.setText("...");
		}
		marginValue.setForeground(Color.WHITE);
		taxedMarginValue.setForeground(Color.WHITE);

		final int itemId = current.getId();
		wikiClient.fetchLatest(itemId,
			latest -> SwingUtilities.invokeLater(() ->
			{
				if (isCurrent(itemId))
				{
					showLatest(latest);
				}
			}),
			error -> SwingUtilities.invokeLater(() ->
			{
				if (isCurrent(itemId))
				{
					showWikiError(error);
				}
			}));

		loadGraph();
	}

	private void loadGraph()
	{
		if (current == null || !config.useWikiPrices())
		{
			return;
		}

		final int itemId = current.getId();
		final Timestep step = timestep;
		graph.setMessage("Loading...");
		wikiClient.fetchTimeseries(itemId, step,
			points -> SwingUtilities.invokeLater(() ->
			{
				if (isCurrent(itemId) && timestep == step)
				{
					graph.setData(points);
				}
			}),
			error -> SwingUtilities.invokeLater(() ->
			{
				if (isCurrent(itemId) && timestep == step)
				{
					graph.setMessage(error);
				}
			}));
	}

	private boolean isCurrent(int itemId)
	{
		return current != null && current.getId() == itemId;
	}

	private void showLatest(LatestPrice latest)
	{
		Integer high = latest == null ? null : latest.getHigh();
		Integer low = latest == null ? null : latest.getLow();

		buyValue.setText(high == null ? "-" : UiUtil.formatGp(high));
		sellValue.setText(low == null ? "-" : UiUtil.formatGp(low));

		if (high != null && low != null)
		{
			long margin = (long) high - low;
			long afterTax = margin - UiUtil.geTax(high);
			marginValue.setText(UiUtil.formatGp(margin));
			taxedMarginValue.setText(UiUtil.formatGp(afterTax));
			taxedMarginValue.setForeground(afterTax > 0 ? UiUtil.POSITIVE : afterTax < 0 ? UiUtil.NEGATIVE : Color.WHITE);
		}
		else
		{
			marginValue.setText("-");
			taxedMarginValue.setText("-");
		}

		long lastTrade = 0;
		if (latest != null)
		{
			lastTrade = Math.max(
				latest.getHighTime() == null ? 0 : latest.getHighTime(),
				latest.getLowTime() == null ? 0 : latest.getLowTime());
		}
		updatedValue.setText(lastTrade == 0 ? "-" : UiUtil.timeAgo(lastTrade));

		if (latest == null)
		{
			wikiStatus.setText("<html>No recent trades recorded for this item.</html>");
			wikiStatus.setVisible(true);
		}
	}

	private void showWikiError(String error)
	{
		for (JLabel label : new JLabel[]{buyValue, sellValue, marginValue, taxedMarginValue, updatedValue})
		{
			label.setText("-");
		}
		wikiStatus.setText("<html>" + error + "</html>");
		wikiStatus.setVisible(true);
	}

	// ---------------------------------------------------------------- helpers

	private static void addRow(JPanel panel, String name, JLabel value)
	{
		panel.add(UiUtil.smallLabel(name, ColorScheme.LIGHT_GRAY_COLOR));
		panel.add(value);
	}

	private static JLabel valueLabel()
	{
		JLabel label = UiUtil.smallLabel("-", Color.WHITE);
		label.setHorizontalAlignment(JLabel.RIGHT);
		return label;
	}

	private static String describe(Timestep step)
	{
		switch (step)
		{
			case ONE_DAY:
				return "Last 24 hours (5 minute averages)";
			case TWO_WEEKS:
				return "Last 2 weeks (hourly averages)";
			case THREE_MONTHS:
				return "Last 3 months (6 hour averages)";
			default:
				return "Last year (daily averages)";
		}
	}
}
