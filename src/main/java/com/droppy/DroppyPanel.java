package com.droppy;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
public class DroppyPanel extends PluginPanel
{
    private static final Color BACKGROUND_COLOR = ColorScheme.DARK_GRAY_COLOR;
    private static final Color HEADER_COLOR = new Color(30, 30, 30);
    private static final Color ITEM_BG_COLOR = new Color(45, 45, 45);
    private static final Color ITEM_BG_HOVER = new Color(55, 55, 55);
    private static final Color OBTAINED_COLOR = new Color(0, 180, 0);
    private static final Color HIGH_CHANCE_COLOR = new Color(255, 200, 0);
    private static final Color LOW_CHANCE_COLOR = new Color(180, 180, 180);
    private static final Color VERY_HIGH_CHANCE_COLOR = new Color(255, 80, 80);
    private static final Color TAB_ACTIVE_COLOR = new Color(70, 130, 230);
    private static final Color TAB_INACTIVE_COLOR = new Color(60, 60, 60);

    private static final String CURRENT_TAB = "CURRENT";
    private static final String SEARCH_TAB = "SEARCH";

    private final DroppyConfig config;
    private final WikiDropFetcher wikiDropFetcher;
    private final PlayerDataManager playerDataManager;
    private final ItemManager itemManager;

    // Tab buttons
    private JButton currentTabBtn;
    private JButton searchTabBtn;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    // Sync status
    private JLabel syncStatusLabel;

    // Current tab components
    private JPanel currentDropsPanel;
    private JLabel currentMonsterTitle;
    private JLabel currentKcLabel;
    private JLabel currentStatusLabel;
    private String currentFightMonster;

    // Search tab components
    private JTextField searchField;
    private JPanel searchResultsPanel;
    private JPanel searchDropsPanel;
    private JLabel searchMonsterTitle;
    private JLabel searchKcLabel;
    private JLabel searchStatusLabel;
    private String searchedMonster;

    public DroppyPanel(DroppyConfig config, WikiDropFetcher wikiDropFetcher,
                       PlayerDataManager playerDataManager, ItemManager itemManager)
    {
        this.config = config;
        this.wikiDropFetcher = wikiDropFetcher;
        this.playerDataManager = playerDataManager;
        this.itemManager = itemManager;

        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);
        setBorder(new EmptyBorder(0, 0, 0, 0));

        buildPanel();
    }

    private void buildPanel()
    {
        // ===== Top: Title + Tabs =====
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(HEADER_COLOR);

        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(HEADER_COLOR);
        titleBar.setBorder(new EmptyBorder(10, 10, 6, 10));

        JLabel titleLabel = new JLabel("Droppy");
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        titleLabel.setForeground(Color.WHITE);
        titleBar.add(titleLabel, BorderLayout.WEST);

        JLabel subtitleLabel = new JLabel("Drop % Calculator");
        subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
        subtitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        titleBar.add(subtitleLabel, BorderLayout.EAST);
        topPanel.add(titleBar);

        // Tab bar
        JPanel tabBar = new JPanel(new GridBagLayout());
        tabBar.setBackground(HEADER_COLOR);
        tabBar.setBorder(new EmptyBorder(0, 0, 0, 0));

        GridBagConstraints tabGbc = new GridBagConstraints();
        tabGbc.fill = GridBagConstraints.HORIZONTAL;
        tabGbc.weightx = 0.5;
        tabGbc.gridy = 0;
        tabGbc.insets = new Insets(0, 0, 0, 0);

        currentTabBtn = createTabButton("Current", true);
        currentTabBtn.addActionListener(e -> switchTab(CURRENT_TAB));
        tabGbc.gridx = 0;
        tabBar.add(currentTabBtn, tabGbc);

        searchTabBtn = createTabButton("Search", false);
        searchTabBtn.addActionListener(e -> switchTab(SEARCH_TAB));
        tabGbc.gridx = 1;
        tabBar.add(searchTabBtn, tabGbc);

        topPanel.add(tabBar);

        // Sync status bar
        syncStatusLabel = new JLabel("Open collection log in-game to sync data");
        syncStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        syncStatusLabel.setForeground(HIGH_CHANCE_COLOR);
        syncStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        syncStatusLabel.setBorder(new EmptyBorder(4, 10, 4, 10));
        topPanel.add(syncStatusLabel);

        add(topPanel, BorderLayout.NORTH);

        // ===== Card layout for the two tab contents =====
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(BACKGROUND_COLOR);

        cardPanel.add(buildCurrentTab(), CURRENT_TAB);
        cardPanel.add(buildSearchTab(), SEARCH_TAB);

        add(cardPanel, BorderLayout.CENTER);

        cardLayout.show(cardPanel, CURRENT_TAB);
    }

    // ======================= CURRENT TAB =======================

    private JPanel buildCurrentTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_COLOR);

        // Monster header
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(HEADER_COLOR);
        headerPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

        currentMonsterTitle = new JLabel("Waiting for combat...");
        currentMonsterTitle.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        currentMonsterTitle.setForeground(Color.WHITE);
        currentMonsterTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(currentMonsterTitle);

        currentKcLabel = new JLabel("");
        currentKcLabel.setFont(FontManager.getRunescapeSmallFont());
        currentKcLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        currentKcLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(currentKcLabel);

        panel.add(headerPanel, BorderLayout.NORTH);

        // Drops list
        currentDropsPanel = new JPanel();
        currentDropsPanel.setLayout(new BoxLayout(currentDropsPanel, BoxLayout.Y_AXIS));
        currentDropsPanel.setBackground(BACKGROUND_COLOR);

        JScrollPane scrollPane = new JScrollPane(currentDropsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Status
        currentStatusLabel = new JLabel("Kill a monster to see drop chances", SwingConstants.CENTER);
        currentStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        currentStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        currentStatusLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(currentStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

    // ======================= SEARCH TAB =======================

    private JPanel buildSearchTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_COLOR);

        // Search header with field + results
        JPanel searchHeader = new JPanel();
        searchHeader.setLayout(new BoxLayout(searchHeader, BoxLayout.Y_AXIS));
        searchHeader.setBackground(HEADER_COLOR);
        searchHeader.setBorder(new EmptyBorder(8, 10, 8, 10));

        searchField = new JTextField();
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setToolTipText("Search for a monster (e.g., Zulrah, Vorkath)");
        searchField.setBackground(ITEM_BG_COLOR);
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(4, 8, 4, 8)
        ));
        searchField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                {
                    String query = searchField.getText().trim();
                    if (!query.isEmpty())
                    {
                        loadSearchMonster(query);
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e)
            {
                String query = searchField.getText().trim();
                if (query.length() >= 2 && e.getKeyCode() != KeyEvent.VK_ENTER)
                {
                    performSearch(query);
                }
                else if (query.isEmpty())
                {
                    clearSearchResults();
                }
            }
        });
        searchHeader.add(searchField);

        // Autocomplete results
        searchResultsPanel = new JPanel();
        searchResultsPanel.setLayout(new BoxLayout(searchResultsPanel, BoxLayout.Y_AXIS));
        searchResultsPanel.setBackground(ITEM_BG_COLOR);
        searchResultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchHeader.add(searchResultsPanel);

        // Monster title/kc below search
        searchHeader.add(Box.createVerticalStrut(6));

        searchMonsterTitle = new JLabel("");
        searchMonsterTitle.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        searchMonsterTitle.setForeground(Color.WHITE);
        searchMonsterTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchHeader.add(searchMonsterTitle);

        searchKcLabel = new JLabel("");
        searchKcLabel.setFont(FontManager.getRunescapeSmallFont());
        searchKcLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        searchKcLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchHeader.add(searchKcLabel);

        panel.add(searchHeader, BorderLayout.NORTH);

        // Drops list
        searchDropsPanel = new JPanel();
        searchDropsPanel.setLayout(new BoxLayout(searchDropsPanel, BoxLayout.Y_AXIS));
        searchDropsPanel.setBackground(BACKGROUND_COLOR);

        JScrollPane scrollPane = new JScrollPane(searchDropsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Status
        searchStatusLabel = new JLabel("Search for any monster above", SwingConstants.CENTER);
        searchStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        searchStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        searchStatusLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(searchStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

    // ======================= TAB SWITCHING =======================

    private void switchTab(String tab)
    {
        cardLayout.show(cardPanel, tab);
        boolean isCurrent = CURRENT_TAB.equals(tab);
        styleTabButton(currentTabBtn, isCurrent);
        styleTabButton(searchTabBtn, !isCurrent);
    }

    private JButton createTabButton(String text, boolean active)
    {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(0, 28));
        styleTabButton(btn, active);
        return btn;
    }

    private void styleTabButton(JButton btn, boolean active)
    {
        if (active)
        {
            btn.setBackground(TAB_ACTIVE_COLOR);
            btn.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.WHITE));
        }
        else
        {
            btn.setBackground(TAB_INACTIVE_COLOR);
            btn.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, TAB_INACTIVE_COLOR));
        }
    }

    // ======================= CURRENT TAB LOGIC =======================

    /**
     * Called by the plugin when the player kills / is fighting a monster.
     * Automatically loads drop data and switches to the Current tab.
     */
    public void setCurrentMonster(String monsterName)
    {
        if (monsterName == null || monsterName.equals(currentFightMonster))
        {
            return;
        }

        currentFightMonster = monsterName;
        currentMonsterTitle.setText(monsterName);
        currentStatusLabel.setText("Loading drops...");
        currentDropsPanel.removeAll();
        currentDropsPanel.revalidate();
        currentDropsPanel.repaint();

        // Auto-switch to Current tab
        switchTab(CURRENT_TAB);

        new Thread(() ->
        {
            MonsterDropData data = wikiDropFetcher.fetchMonsterDrops(monsterName);
            SwingUtilities.invokeLater(() -> populateDrops(
                monsterName, data, currentDropsPanel, currentMonsterTitle,
                currentKcLabel, currentStatusLabel));
        }).start();
    }

    /**
     * Refreshes the Current tab with latest KC data.
     */
    public void refreshCurrent()
    {
        if (currentFightMonster != null)
        {
            setCurrentMonster(null); // reset so re-entry works
            String name = currentFightMonster;
            currentFightMonster = null;
            setCurrentMonster(name);
        }
    }

    /**
     * Force-refreshes the current tab for the given monster (e.g. after KC update).
     */
    public void refreshCurrentForMonster(String monsterName)
    {
        if (monsterName != null && monsterName.equalsIgnoreCase(currentFightMonster))
        {
            currentFightMonster = null;
            setCurrentMonster(monsterName);
        }
    }

    public String getCurrentFightMonster()
    {
        return currentFightMonster;
    }

    // ======================= SEARCH TAB LOGIC =======================

    private void performSearch(String query)
    {
        new Thread(() ->
        {
            List<String> results = wikiDropFetcher.searchMonsters(query);
            SwingUtilities.invokeLater(() ->
            {
                searchResultsPanel.removeAll();
                for (String result : results)
                {
                    JButton btn = new JButton(result);
                    btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
                    btn.setAlignmentX(Component.LEFT_ALIGNMENT);
                    btn.setBackground(ITEM_BG_COLOR);
                    btn.setForeground(Color.WHITE);
                    btn.setBorderPainted(false);
                    btn.setFocusPainted(false);
                    btn.setHorizontalAlignment(SwingConstants.LEFT);
                    btn.setFont(FontManager.getRunescapeSmallFont());
                    btn.addActionListener(e ->
                    {
                        searchField.setText(result);
                        clearSearchResults();
                        loadSearchMonster(result);
                    });
                    btn.addMouseListener(new java.awt.event.MouseAdapter()
                    {
                        public void mouseEntered(java.awt.event.MouseEvent evt) { btn.setBackground(ITEM_BG_HOVER); }
                        public void mouseExited(java.awt.event.MouseEvent evt) { btn.setBackground(ITEM_BG_COLOR); }
                    });
                    searchResultsPanel.add(btn);
                }
                searchResultsPanel.revalidate();
                searchResultsPanel.repaint();
            });
        }).start();
    }

    private void clearSearchResults()
    {
        searchResultsPanel.removeAll();
        searchResultsPanel.revalidate();
        searchResultsPanel.repaint();
    }

    private void loadSearchMonster(String monsterName)
    {
        searchedMonster = monsterName;
        searchStatusLabel.setText("Loading drops for " + monsterName + "...");
        searchDropsPanel.removeAll();
        searchDropsPanel.revalidate();
        searchDropsPanel.repaint();

        new Thread(() ->
        {
            MonsterDropData data = wikiDropFetcher.fetchMonsterDrops(monsterName);
            SwingUtilities.invokeLater(() -> populateDrops(
                monsterName, data, searchDropsPanel, searchMonsterTitle,
                searchKcLabel, searchStatusLabel));
        }).start();
    }

    public void refreshSearch()
    {
        if (searchedMonster != null)
        {
            loadSearchMonster(searchedMonster);
        }
    }

    public String getSearchedMonster()
    {
        return searchedMonster;
    }

    // ======================= SHARED DROP RENDERING =======================

    /**
     * Populates a drops panel with item entries for a given monster.
     * Used by both the Current and Search tabs.
     */
    private void populateDrops(String monsterName, MonsterDropData data,
                               JPanel dropsPanel, JLabel titleLabel,
                               JLabel kcLabel, JLabel statusLabel)
    {
        dropsPanel.removeAll();

        if (data == null || data.getDrops().isEmpty())
        {
            statusLabel.setText("No drop data found for " + monsterName);
            titleLabel.setText(monsterName);
            kcLabel.setText("");
            dropsPanel.revalidate();
            dropsPanel.repaint();
            return;
        }

        titleLabel.setText(data.getMonsterName());

        int totalKc = playerDataManager.getKillCount(monsterName);
        int kcSinceDrop = playerDataManager.getKcSinceLastDrop(monsterName);
        if (totalKc > 0)
        {
            kcLabel.setText("Total KC: " + String.format("%,d", totalKc)
                + "  |  Since drop: " + String.format("%,d", kcSinceDrop));
        }
        else
        {
            kcLabel.setText("No KC tracked yet");
        }

        int count = 0;
        for (DropEntry drop : data.getDrops())
        {
            if (config.showOnlyUnobtained() && playerDataManager.hasItem(drop.getItemName()))
            {
                continue;
            }

            JPanel row = createDropRow(monsterName, drop);
            dropsPanel.add(row);
            dropsPanel.add(Box.createVerticalStrut(1));
            count++;
        }

        statusLabel.setText(count + " drops loaded");
        dropsPanel.revalidate();
        dropsPanel.repaint();
    }

    /**
     * Creates a single drop item row with icon, name, drop rate, percentage bar.
     */
    private JPanel createDropRow(String monsterName, DropEntry drop)
    {
        boolean obtained = playerDataManager.hasItem(drop.getItemName());
        int kc = playerDataManager.getKcSinceLastDrop(monsterName, drop.getItemName());
        double chance = DropChanceCalculator.calculateChance(drop.getDropRate(), kc);
        String chanceStr = DropChanceCalculator.formatPercent(chance);

        // Color based on chance
        Color chanceColor;
        if (obtained)
        {
            chanceColor = OBTAINED_COLOR;
        }
        else if (chance >= 0.9)
        {
            chanceColor = VERY_HIGH_CHANCE_COLOR;
        }
        else if (config.highlightThreshold() > 0 && chance >= (config.highlightThreshold() / 100.0))
        {
            chanceColor = HIGH_CHANCE_COLOR;
        }
        else
        {
            chanceColor = new Color(70, 130, 230);
        }

        // Main row panel
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ITEM_BG_COLOR);
        row.setBorder(new EmptyBorder(5, 6, 5, 6));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        // ---- Left: Item icon (36x36) ----
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(36, 36));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);

        if (drop.getItemId() > 0 && itemManager != null)
        {
            AsyncBufferedImage itemImage = itemManager.getImage(drop.getItemId());
            if (itemImage != null)
            {
                iconLabel.setIcon(new ImageIcon(itemImage));
                // When async image loads, update the icon
                itemImage.onLoaded(() ->
                {
                    iconLabel.setIcon(new ImageIcon(itemImage));
                    iconLabel.revalidate();
                    iconLabel.repaint();
                });
            }
        }
        else
        {
            // Placeholder: colored square with first letter
            BufferedImage placeholder = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = placeholder.createGraphics();
            g.setColor(new Color(60, 60, 60));
            g.fillRoundRect(0, 0, 32, 32, 6, 6);
            g.setColor(Color.GRAY);
            g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
            String letter = drop.getItemName().isEmpty() ? "?" : drop.getItemName().substring(0, 1).toUpperCase();
            g.drawString(letter, 10, 22);
            g.dispose();
            iconLabel.setIcon(new ImageIcon(placeholder));
        }

        row.add(iconLabel, BorderLayout.WEST);

        // ---- Center: Name + rate + progress bar ----
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // Top line: item name
        JLabel nameLabel = new JLabel();
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (obtained)
        {
            nameLabel.setText("\u2713 " + drop.getItemName());
            nameLabel.setForeground(OBTAINED_COLOR);
        }
        else
        {
            nameLabel.setText(drop.getItemName());
            nameLabel.setForeground(Color.WHITE);
        }
        centerPanel.add(nameLabel);

        // Middle line: rate + kc info
        String rateText = DropChanceCalculator.formatDropRate(drop.getDropRate());
        String kcText = kc > 0
            ? " \u2022 " + String.format("%,d", kc) + " kc"
            : "";
        JLabel infoLabel = new JLabel(rateText + kcText);
        infoLabel.setFont(FontManager.getRunescapeSmallFont());
        infoLabel.setForeground(LOW_CHANCE_COLOR);
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(infoLabel);

        // Bottom line: progress bar
        JPanel barPanel = new JPanel(new BorderLayout(4, 0));
        barPanel.setOpaque(false);
        barPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        barPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));

        JProgressBar bar = new JProgressBar(0, 10000);
        bar.setValue((int) (chance * 10000));
        bar.setStringPainted(false);
        bar.setPreferredSize(new Dimension(0, 10));
        bar.setBackground(new Color(30, 30, 30));
        bar.setForeground(chanceColor);
        barPanel.add(bar, BorderLayout.CENTER);

        centerPanel.add(Box.createVerticalStrut(2));
        centerPanel.add(barPanel);

        row.add(centerPanel, BorderLayout.CENTER);

        // ---- Right: Big percentage ----
        JLabel pctLabel = new JLabel(chanceStr);
        pctLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        pctLabel.setForeground(chanceColor);
        pctLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        pctLabel.setVerticalAlignment(SwingConstants.CENTER);
        pctLabel.setPreferredSize(new Dimension(58, 36));
        row.add(pctLabel, BorderLayout.EAST);

        // Hover
        row.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseEntered(java.awt.event.MouseEvent e) { row.setBackground(ITEM_BG_HOVER); }
            public void mouseExited(java.awt.event.MouseEvent e) { row.setBackground(ITEM_BG_COLOR); }
        });

        return row;
    }

    // ======================= COLLECTION LOG SYNC STATUS =======================

    /**
     * Called when the collection log interface is opened in-game.
     */
    public void onCollectionLogOpened()
    {
        SwingUtilities.invokeLater(() ->
        {
            syncStatusLabel.setText("Collection log open - browse pages to sync");
            syncStatusLabel.setForeground(new Color(100, 200, 255));
        });
    }

    /**
     * Called after a collection log page is scraped from the widget.
     */
    public void onCollectionLogSynced(int totalSyncedPages)
    {
        SwingUtilities.invokeLater(() ->
        {
            syncStatusLabel.setText("Synced " + totalSyncedPages + " collection log pages");
            syncStatusLabel.setForeground(OBTAINED_COLOR);

            // Refresh displayed data since obtained items / KC may have changed
            if (currentFightMonster != null)
            {
                String monster = currentFightMonster;
                currentFightMonster = null;
                setCurrentMonster(monster);
            }
            if (searchedMonster != null)
            {
                loadSearchMonster(searchedMonster);
            }
        });
    }
}
