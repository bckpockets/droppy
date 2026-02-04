package com.droppy;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
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
    private static final Color INFO_COLOR = new Color(100, 200, 255);

    private static final String CURRENT_TAB = "CURRENT";
    private static final String SEARCH_TAB = "SEARCH";

    private final DroppyConfig config;
    private final WikiDropFetcher wikiDropFetcher;
    private final PlayerDataManager playerDataManager;
    private final KillCountManager killCountManager;
    private final ItemManager itemManager;

    private JButton currentTabBtn;
    private JButton searchTabBtn;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    private JLabel syncStatusLabel;

    // Current tab
    private JPanel currentDropsPanel;
    private JLabel currentMonsterTitle;
    private JLabel currentKcLabel;
    private JLabel currentStatusLabel;
    private String currentFightMonster;

    // Search tab
    private JTextField searchField;
    private JPanel searchResultsPanel;
    private JPanel searchDropsPanel;
    private JLabel searchMonsterTitle;
    private JLabel searchKcLabel;
    private JLabel searchStatusLabel;
    private String searchedMonster;

    public DroppyPanel(DroppyConfig config, WikiDropFetcher wikiDropFetcher,
                       PlayerDataManager playerDataManager,
                       KillCountManager killCountManager, ItemManager itemManager)
    {
        this.config = config;
        this.wikiDropFetcher = wikiDropFetcher;
        this.playerDataManager = playerDataManager;
        this.killCountManager = killCountManager;
        this.itemManager = itemManager;

        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);
        setBorder(new EmptyBorder(0, 0, 0, 0));

        buildPanel();
    }

    private void buildPanel()
    {
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(HEADER_COLOR);

        // Title
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

        // Tabs
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

        syncStatusLabel = new JLabel("Flip through collection log to sync, then tracking is live");
        syncStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        syncStatusLabel.setForeground(INFO_COLOR);
        syncStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        syncStatusLabel.setBorder(new EmptyBorder(4, 10, 4, 10));
        topPanel.add(syncStatusLabel);

        add(topPanel, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(BACKGROUND_COLOR);

        cardPanel.add(buildCurrentTab(), CURRENT_TAB);
        cardPanel.add(buildSearchTab(), SEARCH_TAB);

        add(cardPanel, BorderLayout.CENTER);

        cardLayout.show(cardPanel, CURRENT_TAB);
    }

    private JPanel buildCurrentTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_COLOR);

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

        currentDropsPanel = new JPanel();
        currentDropsPanel.setLayout(new BoxLayout(currentDropsPanel, BoxLayout.Y_AXIS));
        currentDropsPanel.setBackground(BACKGROUND_COLOR);

        JScrollPane scrollPane = new JScrollPane(currentDropsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        panel.add(scrollPane, BorderLayout.CENTER);

        currentStatusLabel = new JLabel("Kill a monster to see drop chances", SwingConstants.CENTER);
        currentStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        currentStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        currentStatusLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(currentStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildSearchTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_COLOR);

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

        searchResultsPanel = new JPanel();
        searchResultsPanel.setLayout(new BoxLayout(searchResultsPanel, BoxLayout.Y_AXIS));
        searchResultsPanel.setBackground(ITEM_BG_COLOR);
        searchResultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchHeader.add(searchResultsPanel);

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

        searchDropsPanel = new JPanel();
        searchDropsPanel.setLayout(new BoxLayout(searchDropsPanel, BoxLayout.Y_AXIS));
        searchDropsPanel.setBackground(BACKGROUND_COLOR);

        JScrollPane scrollPane = new JScrollPane(searchDropsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        panel.add(scrollPane, BorderLayout.CENTER);

        searchStatusLabel = new JLabel("Search for any monster above", SwingConstants.CENTER);
        searchStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        searchStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        searchStatusLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(searchStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

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

    // Called when player attacks or gets loot from a monster.
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

        switchTab(CURRENT_TAB);

        new Thread(() ->
        {
            MonsterDropData data = wikiDropFetcher.fetchMonsterDrops(monsterName);
            SwingUtilities.invokeLater(() -> populateDrops(
                monsterName, data, currentDropsPanel, currentMonsterTitle,
                currentKcLabel, currentStatusLabel));
        }).start();
    }

    public void refreshCurrent()
    {
        if (currentFightMonster != null)
        {
            String name = currentFightMonster;
            currentFightMonster = null;
            setCurrentMonster(name);
        }
    }

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

        int totalKc = killCountManager.getKillCount(monsterName);
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
            // Only show items that are in the collection log
            if (!playerDataManager.isClogItem(drop.getItemName()))
            {
                continue;
            }

            if (config.showOnlyUnobtained() && playerDataManager.hasItem(drop.getItemName()))
            {
                continue;
            }

            JPanel row = createDropRow(monsterName, drop);
            dropsPanel.add(row);
            dropsPanel.add(Box.createVerticalStrut(1));
            count++;
        }

        if (count == 0)
        {
            statusLabel.setText("Sync collection log to see drops");
        }
        else
        {
            statusLabel.setText(count + " collection log items");
        }
        dropsPanel.revalidate();
        dropsPanel.repaint();
    }

    private JPanel createDropRow(String monsterName, DropEntry drop)
    {
        boolean obtained = playerDataManager.hasItem(drop.getItemName());
        int kc = playerDataManager.getKcSinceLastDrop(monsterName, drop.getItemName());
        double chance = DropChanceCalculator.calculateChance(drop.getDropRate(), kc);
        String chanceStr = DropChanceCalculator.formatPercent(chance);

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

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ITEM_BG_COLOR);
        row.setBorder(new EmptyBorder(5, 6, 5, 6));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        // Item icon - prefer wiki ID, fallback to clog-scraped ID
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(36, 36));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);

        int itemId = drop.getItemId();
        if (itemId <= 0)
        {
            itemId = playerDataManager.getClogItemId(drop.getItemName());
        }

        if (itemId > 0 && itemManager != null)
        {
            AsyncBufferedImage itemImage = itemManager.getImage(itemId);
            if (itemImage != null)
            {
                iconLabel.setIcon(new ImageIcon(itemImage));
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

        // Name + rate + progress bar
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

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

        String rateText = drop.getRarityDisplay() != null
            ? drop.getRarityDisplay()
            : DropChanceCalculator.formatDropRate(drop.getDropRate());
        String kcText = kc > 0
            ? " \u2022 " + String.format("%,d", kc) + " kc"
            : "";
        JLabel infoLabel = new JLabel(rateText + kcText);
        infoLabel.setFont(FontManager.getRunescapeSmallFont());
        infoLabel.setForeground(LOW_CHANCE_COLOR);
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(infoLabel);

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

        // Percentage
        JLabel pctLabel = new JLabel(chanceStr);
        pctLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        pctLabel.setForeground(chanceColor);
        pctLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        pctLabel.setVerticalAlignment(SwingConstants.CENTER);
        pctLabel.setPreferredSize(new Dimension(58, 36));
        row.add(pctLabel, BorderLayout.EAST);

        row.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseEntered(java.awt.event.MouseEvent e) { row.setBackground(ITEM_BG_HOVER); }
            public void mouseExited(java.awt.event.MouseEvent e) { row.setBackground(ITEM_BG_COLOR); }
        });

        return row;
    }

    public void onCollectionLogOpened()
    {
        SwingUtilities.invokeLater(() ->
        {
            syncStatusLabel.setText("Collection log open - browse pages to sync");
            syncStatusLabel.setForeground(INFO_COLOR);
        });
    }

    public void onCollectionLogSynced(int totalSyncedPages)
    {
        SwingUtilities.invokeLater(() ->
        {
            syncStatusLabel.setText("Synced " + totalSyncedPages + " pages - tracking drops live");
            syncStatusLabel.setForeground(OBTAINED_COLOR);

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
