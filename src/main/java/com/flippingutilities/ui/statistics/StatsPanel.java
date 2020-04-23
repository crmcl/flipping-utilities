/*
 * Copyright (c) 2020, Belieal <https://github.com/Belieal>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.flippingutilities.ui.statistics;

import com.flippingutilities.FlippingItem;
import com.flippingutilities.FlippingPlugin;
import com.flippingutilities.OfferInfo;
import com.flippingutilities.ui.UIUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.text.StyleContext;
import lombok.Getter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ComboBoxListRenderer;
import net.runelite.client.util.QuantityFormatter;

public class StatsPanel extends JPanel
{
	private static final String[] TIME_INTERVAL_STRINGS = {"Past Hour", "Past 4 Hours", "Past Day", "Past Week", "Past Month", "Session", "All"};
	private static final String[] SORT_BY_STRINGS = {"Most Recent", "Most Total Profit", "Most Profit Each", "Highest ROI", "Highest Quantity"};
	private static final Dimension ICON_SIZE = new Dimension(16, 16);

	private static final Border TOP_PANEL_BORDER = new CompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BRAND_ORANGE),
		BorderFactory.createEmptyBorder(4, 2, 2, 2));

	private static final Border TOTAL_PROFIT_CONTAINER_BORDER = new CompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 2, 0, ColorScheme.LIGHT_GRAY_COLOR),
		BorderFactory.createEmptyBorder(2, 5, 5, 5));

	private static final Font BIG_PROFIT_FONT = StyleContext.getDefaultStyleContext()
		.getFont(FontManager.getRunescapeBoldFont().getName(), Font.PLAIN, 28);

	private FlippingPlugin plugin;
	private ItemManager itemManager;

	//Holds the buttons that control time intervals
	private JPanel topPanel = new JPanel(new BorderLayout());

	//Holds the sub info labels.
	private JPanel subInfoContainer = new JPanel();

	private JPanel statItemContainer = new JPanel(new GridBagLayout());

	//Constraints for statItemContainer.
	private final GridBagConstraints constraints = new GridBagConstraints();

	//Combo box that selects the time interval that startOfInterval contains.
	private JComboBox<String> timeIntervalList = new JComboBox<>(TIME_INTERVAL_STRINGS);

	//Sorting selector
	private JComboBox<String> sortBox = new JComboBox<>(SORT_BY_STRINGS);

	//Represents the total profit made in the selected time interval.
	private JLabel totalProfitVal = new JLabel();

	//Sets the visible state for subinfo
	private JLabel arrowIcon = new JLabel(UIUtilities.OPEN_ICON);

	/* Subinfo text labels */
	private final JLabel hourlyProfitText = new JLabel("Hourly Profit: ");
	private final JLabel roiText = new JLabel("ROI: ");
	private final JLabel totalRevenueText = new JLabel("Total Revenue: ");
	private final JLabel totalExpenseText = new JLabel("Total Expense: ");
	private final JLabel sessionTimeText = new JLabel("Session Time: ");

	private final JLabel[] textLabelArray = {hourlyProfitText, roiText, totalRevenueText, totalExpenseText, sessionTimeText};

	/* Subinfo value labels */
	private final JLabel hourlyProfitVal = new JLabel("", SwingConstants.RIGHT);
	private final JLabel roiVal = new JLabel("", SwingConstants.RIGHT);
	private final JLabel totalRevenueVal = new JLabel("", SwingConstants.RIGHT);
	private final JLabel totalExpenseVal = new JLabel("", SwingConstants.RIGHT);
	private final JLabel sessionTimeVal = new JLabel("", SwingConstants.RIGHT);

	private final JLabel[] valLabelArray = {hourlyProfitVal, roiVal, totalRevenueVal, totalExpenseVal, sessionTimeVal};

	private final JPanel hourlyProfitPanel = new JPanel(new BorderLayout());
	private final JPanel roiPanel = new JPanel(new BorderLayout());
	private final JPanel totalRevenuePanel = new JPanel(new BorderLayout());
	private final JPanel totalExpensePanel = new JPanel(new BorderLayout());
	private final JPanel sessionTimePanel = new JPanel(new BorderLayout());

	private final JPanel[] subInfoPanelArray = {hourlyProfitPanel, roiPanel, totalRevenuePanel, totalExpensePanel, sessionTimePanel};

	//Data acquired from history manager of all items
	private long totalProfit;
	private long totalExpenses;
	private long totalRevenues;

	//Contains the unix time of the start of the interval.
	@Getter
	private Instant startOfInterval = Instant.now();

	@Getter
	private String selectedInterval;

	@Getter
	private String selectedSort;

	//Time when the panel was created. Assume this is the start of session.
	private Instant sessionTime;

	private ArrayList<StatItemPanel> activePanels = new ArrayList<>();

	/**
	 * The statistics panel shows various stats about trades the user has made over a selectable time interval.
	 * This represents the front-end Statistics Tab.
	 * It is shown when it has been selected by the tab manager.
	 *
	 * @param plugin      Used to access the config and list of trades.
	 * @param itemManager Accesses the RuneLite item cache.
	 */
	public StatsPanel(final FlippingPlugin plugin, final ItemManager itemManager)
	{
		super(false);

		this.plugin = plugin;
		this.itemManager = itemManager;


		setLayout(new BorderLayout());

		//Record start of session time.
		sessionTime = Instant.now();

		//Constraints for statItems later on.
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		//Start off with "Session" selected in the combobox.
		timeIntervalList.setSelectedItem("Session");
		timeIntervalList.setRenderer(new ComboBoxListRenderer());
		timeIntervalList.setMinimumSize(new Dimension(0, 35));
		timeIntervalList.setFocusable(false);
		timeIntervalList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		timeIntervalList.addActionListener(event ->
		{
			selectedInterval = (String) timeIntervalList.getSelectedItem();

			//Handle new time interval selected
			setTimeInterval(selectedInterval);
		});

		//Holds the time interval selector beneath the tab manager.
		topPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		topPanel.setBorder(TOP_PANEL_BORDER);
		topPanel.add(new JLabel("Time Interval: "), BorderLayout.WEST);
		topPanel.add(timeIntervalList, BorderLayout.CENTER);

		//Title text for the big total profit label.
		final JLabel profitText = new JLabel("Total Profit: ", SwingConstants.CENTER);
		profitText.setForeground(ColorScheme.GRAND_EXCHANGE_ALCH);
		profitText.setFont(FontManager.getRunescapeBoldFont());

		//Profit total over the selected time interval
		totalProfitVal.setFont(BIG_PROFIT_FONT);
		totalProfitVal.setHorizontalAlignment(SwingConstants.CENTER);
		totalProfitVal.setToolTipText("");

		arrowIcon.setPreferredSize(ICON_SIZE);

		//Make sure the profit label is centered
		JLabel padLabel = new JLabel();
		padLabel.setPreferredSize(ICON_SIZE);

		//Formats the profit text and value.
		JPanel profitTextAndVal = new JPanel(new BorderLayout());
		profitTextAndVal.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		profitTextAndVal.add(totalProfitVal, BorderLayout.CENTER);
		profitTextAndVal.add(profitText, BorderLayout.NORTH);

		//Contains the total profit information.
		JPanel totalProfitContainer = new JPanel(new BorderLayout());
		totalProfitContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		totalProfitContainer.setBorder(TOTAL_PROFIT_CONTAINER_BORDER);

		//totalProfitContainer.add(, BorderLayout.NORTH);
		totalProfitContainer.add(profitTextAndVal, BorderLayout.CENTER);
		totalProfitContainer.add(arrowIcon, BorderLayout.EAST);
		totalProfitContainer.add(padLabel, BorderLayout.WEST);

		//Controls the collapsible sub info function
		MouseAdapter collapseOnClick = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					if (subInfoContainer.isVisible())
					{
						//Collapse sub info
						arrowIcon.setIcon(UIUtilities.CLOSE_ICON);
						subInfoContainer.setVisible(false);
					}
					else
					{
						//Expand sub info
						arrowIcon.setIcon(UIUtilities.OPEN_ICON);
						subInfoContainer.setVisible(true);
					}
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				totalProfitContainer.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				profitTextAndVal.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				totalProfitContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
				profitTextAndVal.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
			}
		};

		//Since the totalProfitVal's tooltip consumes the mouse event
		totalProfitContainer.addMouseListener(collapseOnClick);
		totalProfitVal.addMouseListener(collapseOnClick);

		/* Subinfo represents the less-used general historical stats */
		subInfoContainer.setLayout(new DynamicGridLayout(textLabelArray.length, valLabelArray.length));

		//All labels should already be sorted in their arrays.
		for (int i = 0; i < subInfoPanelArray.length; i++)
		{
			textLabelArray[i].setForeground(ColorScheme.GRAND_EXCHANGE_ALCH);

			subInfoPanelArray[i].add(textLabelArray[i], BorderLayout.WEST);
			subInfoPanelArray[i].add(valLabelArray[i], BorderLayout.EAST);
		}

		subInfoContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		subInfoContainer.setBorder(new EmptyBorder(9, 5, 5, 5));

		//Wraps the total profit labels.
		JPanel totalProfitWrapper = new JPanel(new BorderLayout());
		totalProfitWrapper.add(totalProfitContainer, BorderLayout.NORTH);
		totalProfitWrapper.add(subInfoContainer, BorderLayout.SOUTH);
		totalProfitWrapper.setBorder(new EmptyBorder(5, 5, 5, 5));

		//Holds all the main content of the panel.
		JPanel contentWrapper = new JPanel(new BorderLayout());
		contentWrapper.add(totalProfitWrapper, BorderLayout.NORTH);

		/* The following represents the formatting behind the StatItems that appear at the bottom of the page.
		 These are designed similarly to the FlippingItemPanels and contains information about individual flips. */
		/* Sorting selector */
		JPanel sortPanel = new JPanel(new BorderLayout());

		JLabel sortLabel = new JLabel("Sort by: ");

		sortBox.setSelectedItem("Most Recent");
		sortBox.setRenderer(new ComboBoxListRenderer());
		sortBox.setMinimumSize(new Dimension(0, 35));
		sortBox.setFocusable(false);
		sortBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		sortBox.addActionListener(event ->
		{
			selectedSort = (String) sortBox.getSelectedItem();

			if (selectedSort == null)
			{
				return;
			}

			SwingUtilities.invokeLater(() -> rebuild(plugin.getTradesList()));
			plugin.updateConfig();
		});

		sortPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sortPanel.setBorder(new EmptyBorder(2, 5, 2, 5));

		sortPanel.add(sortLabel, BorderLayout.WEST);
		sortPanel.add(sortBox, BorderLayout.CENTER);

		JPanel statItemWrapper = new JPanel(new BorderLayout());
		statItemWrapper.add(statItemContainer, BorderLayout.NORTH);

		JScrollPane scrollWrapper = new JScrollPane(statItemWrapper);
		scrollWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollWrapper.setBorder(new EmptyBorder(3, 5, 5, 5));
		scrollWrapper.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
		scrollWrapper.getVerticalScrollBar().setBorder(new EmptyBorder(0, 3, 0, 0));

		//itemContainer holds the StatItems along with its sorting selector.
		JPanel itemContainer = new JPanel(new BorderLayout());
		itemContainer.add(sortPanel, BorderLayout.NORTH);
		itemContainer.add(scrollWrapper, BorderLayout.CENTER);

		contentWrapper.add(itemContainer, BorderLayout.CENTER);

		add(contentWrapper, BorderLayout.CENTER);
		add(topPanel, BorderLayout.NORTH);
	}

	/**
	 * Removes old stat items and builds new ones based on the passed trade list.
	 * Items are initialized with their sub info containers collapsed.
	 *
	 * @param tradesList The list of flipping items that get shown on the stat panel.
	 */
	public void rebuild(ArrayList<FlippingItem> tradesList)
	{
		SwingUtilities.invokeLater(() ->
		{
			//Remove old stats
			statItemContainer.removeAll();
			activePanels = new ArrayList<>();

			sortTradeList(tradesList);

			int index = 0;
			for (FlippingItem item : tradesList)
			{
				ArrayList<OfferInfo> itemTradeHistory = new ArrayList<>(item.getIntervalHistory(startOfInterval));

				//Make sure the item has stats we can use
				if (itemTradeHistory.isEmpty() || item.countItemsFlipped(itemTradeHistory) == 0)
				{
					continue;
				}

				StatItemPanel newPanel = new StatItemPanel(plugin, itemManager, item);

				if (index++ > 0)
				{
					JPanel marginWrapper = new JPanel(new BorderLayout());
					marginWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
					marginWrapper.setBorder(new EmptyBorder(5, 0, 0, 0));
					marginWrapper.add(newPanel, BorderLayout.NORTH);
					statItemContainer.add(marginWrapper, constraints);
				}
				else
				{
					//First item in the wrapper
					statItemContainer.add(newPanel, constraints);
				}
				activePanels.add(newPanel);
				constraints.gridy++;
			}
			updateDisplays();
		});

		revalidate();
		repaint();
	}

	/**
	 * Updates all profit labels on the stat panel using their respective update methods.
	 * Gets called on startup, after the tradesList has been initialized, and after every new registered trade.
	 */
	public void updateDisplays()
	{

		subInfoContainer.removeAll();

		boolean useAltColor = true;
		for (JPanel panel : subInfoPanelArray)
		{
			panel.setBorder(new EmptyBorder(4, 2, 4, 2));
			subInfoContainer.add(panel);
			panel.setBackground(useAltColor ? UIUtilities.DARK_GRAY_ALT_ROW_COLOR : ColorScheme.DARKER_GRAY_COLOR);

			useAltColor = !useAltColor;
		}

		totalProfit = 0;
		totalExpenses = 0;
		totalRevenues = 0;

		ArrayList<FlippingItem> tradesList = plugin.getTradesList();

		for (FlippingItem item : tradesList)
		{
			totalProfit += item.currentProfit(item.getIntervalHistory(startOfInterval));
			totalExpenses += item.getCashflow(startOfInterval, true);
			totalRevenues += item.getCashflow(startOfInterval, false);
		}

		for (StatItemPanel panel : activePanels)
		{
			panel.updateDisplays();
		}

		updateTotalProfitDisplay();
		updateSubInfoFont();
		updateHourlyProfitDisplay();
		updateRoiDisplay();
		updateRevenueAndExpenseDisplay();
		updateSessionTime();
	}

	/**
	 * Responsible for updating the total profit label at the very top.
	 * Sets the new total profit value from the items in tradesList from {@link FlippingPlugin#getTradesList()}.
	 */
	private void updateTotalProfitDisplay()
	{
		if (plugin.getTradesList() == null)
		{
			totalProfitVal.setText("0");
			totalProfitVal.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
			totalProfitVal.setToolTipText("Total Profit: 0 gp");
			return;
		}

		totalProfitVal.setText(((totalProfit >= 0) ? "" : "-") + UIUtilities.quantityToRSDecimalStack(Math.abs(totalProfit), true) + " gp");
		totalProfitVal.setToolTipText("Total Profit: " + QuantityFormatter.formatNumber(totalProfit) + " gp");
		totalProfitVal.setForeground(totalProfit >= 0 ? ColorScheme.GRAND_EXCHANGE_PRICE : UIUtilities.OUTDATED_COLOR);
	}

	/**
	 * Updates the hourly profit value display. Also checks and sets the font color according to profit/loss.
	 */
	private void updateHourlyProfitDisplay()
	{
		//Doesn't really make sense to show profit/hr for anything else
		//unless we store session time over longer periods of time.
		if (Objects.equals(timeIntervalList.getSelectedItem(), "Session"))
		{
			double divisor = (Instant.now().getEpochSecond() - startOfInterval.getEpochSecond()) * 1.0 / (60 * 60);

			String profitString = UIUtilities.quantityToRSDecimalStack((long) (totalProfit / divisor), true);
			hourlyProfitVal.setText(profitString + " gp/hr");
		}
		else
		{
			subInfoContainer.remove(hourlyProfitPanel);
		}

		hourlyProfitVal.setForeground(totalProfit >= 0 ? ColorScheme.GRAND_EXCHANGE_PRICE : UIUtilities.OUTDATED_COLOR);
	}

	/**
	 * Updates the total ROI value display. Also checks and sets the font color according to profit/loss.
	 */
	private void updateRoiDisplay()
	{
		float roi = (float) totalProfit / totalExpenses * 100;

		if (totalExpenses == 0)
		{
			roiVal.setText("0.00%");
			roiVal.setForeground(Color.RED);
			return;
		}
		else
		{
			roiVal.setText(String.format("%.2f", (float) totalProfit / totalExpenses * 100) + "%");
		}

		roiVal.setForeground(UIUtilities.gradiatePercentage(roi, plugin.getConfig().roiGradientMax()));
	}

	/**
	 * Updates both the revenue and expense display along with setting their font colors.
	 */
	private void updateRevenueAndExpenseDisplay()
	{
		totalRevenueVal.setText(UIUtilities.quantityToRSDecimalStack(totalRevenues, true) + " gp");
		totalRevenueVal.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);

		totalExpenseVal.setText(UIUtilities.quantityToRSDecimalStack(totalExpenses, true) + " gp");
		totalExpenseVal.setForeground(UIUtilities.OUTDATED_COLOR);
	}

	/**
	 * Chooses the font that is used for the sub information based on user config.
	 */
	private void updateSubInfoFont()
	{
		Font font = null;
		switch (plugin.getConfig().subInfoFontStyle())
		{
			case SMALL_FONT:
				font = FontManager.getRunescapeSmallFont();
				break;

			case REGULAR_FONT:
				font = FontManager.getRunescapeFont();
				break;

			case BOLD_FONT:
				font = FontManager.getRunescapeBoldFont();
				break;
		}

		for (int i = 0; i < textLabelArray.length; i++)
		{
			textLabelArray[i].setFont(font);
			valLabelArray[i].setFont(font);
		}
	}

	private void updateSessionTime()
	{
		sessionTimeVal.setText(UIUtilities.formatDuration(sessionTime));
		sessionTimeVal.setPreferredSize(new Dimension(200, 0));
		sessionTimeVal.setForeground(ColorScheme.GRAND_EXCHANGE_ALCH);
		if (!Objects.equals(timeIntervalList.getSelectedItem(), "Session"))
		{
			subInfoContainer.remove(sessionTimePanel);
		}
	}

	/**
	 * Gets called every time the time interval combobox has its selection changed.
	 * Sets the start interval of the profit calculation.
	 *
	 * @param selectedInterval The string from TIME_INTERVAL_STRINGS that is selected in the time interval combobox
	 */
	public void setTimeInterval(String selectedInterval)
	{
		if (selectedInterval == null)
		{
			return;
		}

		Instant timeNow = Instant.now();

		switch (selectedInterval)
		{
			case "Past Hour":
				startOfInterval = timeNow.minus(1, ChronoUnit.HOURS);
				break;
			case "Past 4 Hours":
				startOfInterval = timeNow.minus(4, ChronoUnit.HOURS);
				break;
			case "Past Day":
				startOfInterval = timeNow.minus(1, ChronoUnit.DAYS);
				break;
			//Apparently Instant doesn't support weeks and months.
			case "Past Week":
				startOfInterval = timeNow.minus(7, ChronoUnit.DAYS);
				break;
			case "Past Month":
				startOfInterval = timeNow.minus(30, ChronoUnit.DAYS);
				break;
			case "Session":
				startOfInterval = sessionTime;
				break;
			case "All":
				startOfInterval = Instant.EPOCH;
				break;
			default:
				break;
		}

		timeIntervalList.setSelectedItem(selectedInterval);
		SwingUtilities.invokeLater(() -> rebuild(plugin.getTradesList()));
		plugin.updateConfig();
	}

	/**
	 * Sorts the to-be-built tradeList items according to the selectedSort string.
	 *
	 * @param tradeList The soon-to-be drawn tradeList whose items are getting sorted.
	 */
	public void sortTradeList(ArrayList<FlippingItem> tradeList)
	{
		if (selectedSort == null || tradeList.isEmpty())
		{
			return;
		}

		switch (selectedSort)
		{
			case "Most Recent":
				tradeList.sort((item1, item2) ->
				{
					if (item1 == null || item2 == null)
					{
						return -1;
					}

					return item1.getLatestActivityTime().compareTo(item2.getLatestActivityTime());
				});
				break;

			case "Most Total Profit":
				tradeList.sort(Comparator.comparing(item -> item.currentProfit(item.getIntervalHistory(startOfInterval))));
				break;

			case "Most Profit Each":
				tradeList.sort(Comparator.comparing(item ->
				{
					ArrayList<OfferInfo> intervalHistory = item.getIntervalHistory(startOfInterval);
					int quantity = item.countItemsFlipped(intervalHistory);

					if (quantity == 0)
					{
						return 0;
					}

					return (int) item.currentProfit(intervalHistory) / quantity;
				}));
				break;

			case "Highest ROI":
				tradeList.sort((item1, item2) ->
				{
					ArrayList<OfferInfo> intervalHistory1 = item1.getIntervalHistory(startOfInterval);
					ArrayList<OfferInfo> intervalHistory2 = item2.getIntervalHistory(startOfInterval);

					long totalExpense1 = item1.getCashflow(intervalHistory1, true);
					long totalExpense2 = item2.getCashflow(intervalHistory2, true);

					if (totalExpense1 == 0 || totalExpense2 == 0)
					{
						return -1;
					}

					return Float.compare((float) item1.currentProfit(intervalHistory1) / totalExpense1, (float) item2.currentProfit(intervalHistory2) / totalExpense2);
				});
				break;

			case "Highest Quantity":
				tradeList.sort(Comparator.comparing(item -> item.countItemsFlipped(item.getIntervalHistory(startOfInterval))));
				break;

			default:
				throw new IllegalStateException("Unexpected value: " + selectedSort);
		}
		Collections.reverse(tradeList);
	}

}
