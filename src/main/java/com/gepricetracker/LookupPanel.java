package com.gepricetracker;

import com.gepricetracker.wiki.WikiModels.LatestPrice;
import com.gepricetracker.wiki.WikiPriceClient;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.LinkBrowser;
import net.runelite.http.api.item.ItemPrice;

/**
 * "Lookup" tab: item search / favorites -> results list -> item detail with live prices and a history graph.
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
	private final ConfigManager configManager;

	private final Set<Integer> favoriteIds = new LinkedHashSet<>();
	private final IconTextField searchBar = new IconTextField();
	private final CardLayout cards = new CardLayout();
	private final JPanel cardPanel = new JPanel(cards);
	private final JPanel resultsList = new JPanel(new GridBagLayout());

	// Detail view
	private JLabel backButton;
	private final JLabel detailStar = new JLabel();
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
	private boolean showingFavorites = false;

	LookupPanel(ItemManager itemManager, ClientThread clientThread, WikiPriceClient wikiClient,
				GePriceTrackerConfig config, ConfigManager configManager)
	{
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.wikiClient = wikiClient;
		this.config = config;
		this.configManager = configManager;
		this.timestep = config.defaultTimestep();

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		loadFavorites();

		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(0, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.addActionListener(e -> search());
		searchBar.addClearListener(() ->
		{
			searchSeq++;
			searchBar.setIcon(IconTextField.Icon.SEARCH);
			showDefaultOrFavorites();
		});

		JButton favBtn = new JButton("★");
		favBtn.setFont(FontManager.getRunescapeBoldFont());
		favBtn.setPreferredSize(new Dimension(30, 30));
		favBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		favBtn.setForeground(Color.YELLOW);
		favBtn.setToolTipText("View Favorites");
		favBtn.setFocusPainted(false);
		favBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		favBtn.addActionListener(e ->
		{
			searchSeq++;
			searchBar.setText("");
			searchBar.setIcon(IconTextField.Icon.SEARCH);
			loadAndShowFavorites();
		});

		JPanel searchWrapper = new JPanel(new BorderLayout(4, 0));
		searchWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchWrapper.setBorder(new EmptyBorder(10, 0, 8, 0));
		searchWrapper.add(searchBar, BorderLayout.CENTER);
		searchWrapper.add(favBtn, BorderLayout.EAST);

		resultsList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		cardPanel.add(ScrollableColumn.wrap(resultsList), RESULTS_CARD);
		cardPanel.add(ScrollableColumn.wrap(buildDetailView()), DETAIL_CARD);

		add(searchWrapper, BorderLayout.NORTH);
		add(cardPanel, BorderLayout.CENTER);

		showDefaultOrFavorites();
	}

	void onConfigChanged()
	{
		if (current != null)
		{
			refreshWiki();
		}
	}

	// ---------------------------------------------------------------- favorites

	private boolean isFavorite(int itemId)
	{
		return favoriteIds.contains(itemId);
	}

	private void toggleFavorite(int itemId)
	{
		if (favoriteIds.contains(itemId))
		{
			favoriteIds.remove(itemId);
		}
		else
		{
			favoriteIds.add(itemId);
		}
		saveFavorites();

		if (showingFavorites)
		{
			loadAndShowFavorites();
		}
	}

	private void loadFavorites()
	{
		favoriteIds.clear();
		String str = configManager.getConfiguration(GePriceTrackerConfig.GROUP, "favorites");
		if (str != null && !str.trim().isEmpty())
		{
			for (String part : str.split(","))
			{
				try
				{
					int id = Integer.parseInt(part.trim());
					if (id > 0)
					{
						favoriteIds.add(id);
					}
				}
				catch (NumberFormatException ignored)
				{
				}
			}
		}
	}

	private void saveFavorites()
	{
		String str = favoriteIds.stream()
			.map(String::valueOf)
			.collect(Collectors.joining(","));
		configManager.setConfiguration(GePriceTrackerConfig.GROUP, "favorites", str);
	}

	private void showDefaultOrFavorites()
	{
		if (!favoriteIds.isEmpty())
		{
			loadAndShowFavorites();
		}
		else
		{
			showingFavorites = true;
			showEmptyFavorites();
		}
	}

	private void loadAndShowFavorites()
	{
		showingFavorites = true;
		if (favoriteIds.isEmpty())
		{
			showEmptyFavorites();
			return;
		}

		final int seq = ++searchSeq;
		List<Integer> ids = new ArrayList<>(favoriteIds);
		clientThread.invokeLater(() ->
		{
			List<SearchResult> results = new ArrayList<>();
			for (int id : ids)
			{
				ItemComposition comp = itemManager.getItemComposition(id);
				if (comp != null)
				{
					long gePrice = itemManager.getItemPrice(id);
					results.add(new SearchResult(id, comp.getName(), gePrice, comp.getHaPrice()));
				}
			}

			SwingUtilities.invokeLater(() ->
			{
				if (seq != searchSeq || !showingFavorites)
				{
					return;
				}
				showFavorites(results);
			});
		});
	}

	private void showFavorites(List<SearchResult> favorites)
	{
		resultsList.removeAll();
		int y = 0;

		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		headerRow.setBorder(new EmptyBorder(5, 6, 5, 6));

		JLabel title = new JLabel("★ Favorite Items (" + favorites.size() + ")");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.YELLOW);
		headerRow.add(title, BorderLayout.CENTER);

		resultsList.add(headerRow, UiUtil.row(y++, 0));

		for (SearchResult result : favorites)
		{
			resultsList.add(buildResultRow(result), UiUtil.row(y++, 2));
		}

		resultsList.revalidate();
		resultsList.repaint();
		cards.show(cardPanel, RESULTS_CARD);
	}

	private void showEmptyFavorites()
	{
		resultsList.removeAll();
		JPanel msg = new JPanel();
		msg.setLayout(new BoxLayout(msg, BoxLayout.Y_AXIS));
		msg.setOpaque(false);
		msg.setBorder(new EmptyBorder(12, 6, 12, 6));

		JLabel title = new JLabel("No favorite items yet");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel hint = UiUtil.wrappingLabel("Search an item and click the star (☆) to pin it here for quick access.");
		hint.setBorder(new EmptyBorder(6, 0, 0, 0));
		hint.setAlignmentX(Component.LEFT_ALIGNMENT);

		msg.add(title);
		msg.add(hint);

		resultsList.add(msg, UiUtil.row(0, 0));
		resultsList.revalidate();
		resultsList.repaint();
		cards.show(cardPanel, RESULTS_CARD);
	}

	// ---------------------------------------------------------------- search

	private void search()
	{
		String query = searchBar.getText().trim();
		if (query.isEmpty())
		{
			showDefaultOrFavorites();
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
		showingFavorites = false;
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
		showingFavorites = false;
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

		JLabel star = new JLabel();
		star.setFont(FontManager.getRunescapeBoldFont());
		star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		star.setBorder(new EmptyBorder(0, 4, 0, 2));

		Runnable updateStar = () ->
		{
			boolean fav = isFavorite(result.getId());
			star.setText(fav ? "★" : "☆");
			star.setForeground(fav ? Color.YELLOW : ColorScheme.LIGHT_GRAY_COLOR);
			star.setToolTipText(fav ? "Remove from favorites" : "Add to favorites");
		};
		updateStar.run();

		star.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				e.consume();
				toggleFavorite(result.getId());
				if (showingFavorites)
				{
					loadAndShowFavorites();
				}
				else
				{
					updateStar.run();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (!isFavorite(result.getId()))
				{
					star.setForeground(Color.YELLOW);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (!isFavorite(result.getId()))
				{
					star.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				}
			}
		});

		row.add(icon, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);
		row.add(star, BorderLayout.EAST);

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

		backButton = UiUtil.smallLabel("< Back to results", ColorScheme.LIGHT_GRAY_COLOR);
		backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		backButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				current = null;
				if (showingFavorites)
				{
					loadAndShowFavorites();
				}
				else
				{
					cards.show(cardPanel, RESULTS_CARD);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				backButton.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				backButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		panel.add(backButton, UiUtil.row(y++, 0));

		// Header: icon + name + favorite star
		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(6, 6, 6, 6));
		detailIcon.setPreferredSize(new Dimension(36, 32));
		detailName.setForeground(Color.WHITE);
		detailName.setFont(FontManager.getRunescapeBoldFont());

		detailStar.setFont(FontManager.getRunescapeBoldFont());
		detailStar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		detailStar.setBorder(new EmptyBorder(0, 6, 0, 4));
		detailStar.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (current != null)
				{
					toggleFavorite(current.getId());
					updateDetailStar();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (current != null && !isFavorite(current.getId()))
				{
					detailStar.setForeground(Color.YELLOW);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (current != null && !isFavorite(current.getId()))
				{
					detailStar.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				}
			}
		});

		header.add(detailIcon, BorderLayout.WEST);
		header.add(detailName, BorderLayout.CENTER);
		header.add(detailStar, BorderLayout.EAST);
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

	private void updateDetailStar()
	{
		if (current == null)
		{
			return;
		}
		boolean fav = isFavorite(current.getId());
		detailStar.setText(fav ? "★" : "☆");
		detailStar.setForeground(fav ? Color.YELLOW : ColorScheme.LIGHT_GRAY_COLOR);
		detailStar.setToolTipText(fav ? "Remove from favorites" : "Add to favorites");
	}

	private void showDetail(SearchResult result)
	{
		current = result;
		backButton.setText(showingFavorites ? "< Back to favorites" : "< Back to results");
		detailIcon.setIcon(null);
		itemManager.getImage(result.getId()).addTo(detailIcon);
		detailName.setText("<html>" + result.getName() + "</html>");
		geValue.setText(UiUtil.formatGp(result.getGePrice()));
		haValue.setText(UiUtil.formatGp(result.getHaPrice()));

		updateDetailStar();

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
