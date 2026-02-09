package com.droppy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WikiScraper
{
    private static final String WIKI_API_BASE = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "Droppy RuneLite Plugin - Drop Table Scraper (https://github.com/bckpockets/droppy)";

    private static final String[] ALL_CLOG_PAGES = {
        // Bosses
        "Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor", "Barrows Chests",
        "Bryophyta", "Callisto and Artio", "Cerberus", "Chaos Elemental", "Chaos Fanatic",
        "Commander Zilyana", "Corporeal Beast", "Crazy Archaeologist", "Dagannoth Kings",
        "Deranged Archaeologist", "Duke Sucellus", "General Graardor", "Giant Mole",
        "Grotesque Guardians", "Hespori", "Hueycoatl", "Kalphite Queen", "King Black Dragon",
        "Kraken", "Kree'arra", "K'ril Tsutsaroth", "Leviathan", "Mimic", "Nex",
        "Nightmare", "Obor", "Phantom Muspah", "Royal Titans", "Sarachnis", "Scorpia",
        "Scurrius", "Skotizo", "Sol Heredit", "Spindel", "The Hueycoatl",
        "Thermonuclear Smoke Devil", "Tormented Demons", "Vardorvis", "Venenatis and Spindel",
        "Vet'ion and Calvar'ion", "Vorkath", "Whisperer", "Wintertodt", "Zalcano", "Zulrah",
        // Raids
        "Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut",
        // Clues
        "Beginner Treasure Trails", "Easy Treasure Trails", "Medium Treasure Trails",
        "Hard Treasure Trails", "Elite Treasure Trails", "Master Treasure Trails",
        "Shared Treasure Trail Rewards",
        // Minigames
        "Barbarian Assault", "Brimhaven Agility Arena", "Castle Wars", "Creature Creation",
        "Fishing Trawler", "Gnome Restaurant", "Guardians of the Rift", "Hallowed Sepulchre",
        "Last Man Standing", "Magic Training Arena", "Mahogany Homes", "Pest Control",
        "Pyramid Plunder", "Rogues' Den", "Shades of Mort'ton", "Soul Wars",
        "Tai Bwo Wannai Cleanup", "Temple Trekking", "Tithe Farm", "Trouble Brewing",
        "Volcanic Mine",
        // Other
        "Aerial Fishing", "All Pets", "Champion's Challenge", "Chaos Druids",
        "Chompy Bird Hunting", "Colosseum", "Cyclopes", "Defenders of Varrock",
        "Fossil Island Notes", "Glough's Experiments", "Gorak", "Graceful",
        "Miscellaneous", "Monkey Backpacks", "Motherlode Mine", "My Notes",
        "Random Events", "Revenants", "Rooftop Agility", "Shayzien Armour",
        "Shooting Stars", "Skilling Pets", "Slayer", "The Gauntlet", "TzHaar", "Undead Druids"
    };

    // Clog page name -> wiki page(s) to scrape drops from
    private static final Map<String, List<String>> WIKI_PAGES = new HashMap<>();

    // Runtime aliases: loot event name -> clog page name
    private static final Map<String, String> PAGE_ALIASES = new HashMap<>();

    private static final String[] SUBPAGE_SUFFIXES = {
        "/Loot", "/Rewards", "/Unique drop table", "/Unique loot"
    };

    private static final Pattern DROPS_LINE_START = Pattern.compile(
        "\\{\\{DropsLine(?:Reward|Skill)?\\s*\\|",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FRACTION_PATTERN = Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)"
    );

    private static final Pattern PLINK_PATTERN = Pattern.compile(
        "\\{\\{plink\\|([^}|]+)", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CLOG_SECTION_HEADER = Pattern.compile(
        "^===\\s*(.+?)\\s*===$", Pattern.MULTILINE
    );

    private static final Map<String, Double> RARITY_KEYWORDS = new HashMap<>();

    static
    {
        RARITY_KEYWORDS.put("always", 1.0);
        RARITY_KEYWORDS.put("common", 1.0 / 16.0);
        RARITY_KEYWORDS.put("uncommon", 1.0 / 64.0);
        RARITY_KEYWORDS.put("rare", 1.0 / 128.0);
        RARITY_KEYWORDS.put("very rare", 1.0 / 512.0);

        // --- Boss name mismatches ---
        WIKI_PAGES.put("Barrows Chests", List.of("Chest (Barrows)"));
        WIKI_PAGES.put("Crazy Archaeologist", List.of("Crazy archaeologist"));
        WIKI_PAGES.put("Deranged Archaeologist", List.of("Deranged archaeologist"));
        WIKI_PAGES.put("Hueycoatl", List.of("The Hueycoatl"));
        WIKI_PAGES.put("Leviathan", List.of("The Leviathan"));
        WIKI_PAGES.put("Mimic", List.of("The Mimic"));
        WIKI_PAGES.put("Nightmare", List.of("The Nightmare"));
        WIKI_PAGES.put("Sol Heredit", List.of("Rewards Chest (Fortis Colosseum)"));
        WIKI_PAGES.put("Thermonuclear Smoke Devil", List.of("Thermonuclear smoke devil"));
        WIKI_PAGES.put("Tormented Demons", List.of("Tormented demon"));
        WIKI_PAGES.put("Whisperer", List.of("The Whisperer"));
        WIKI_PAGES.put("Colosseum", List.of("Rewards Chest (Fortis Colosseum)"));

        // --- Multi-monster clog pages ---
        WIKI_PAGES.put("Callisto and Artio", List.of("Callisto", "Artio"));
        WIKI_PAGES.put("Dagannoth Kings", List.of("Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"));
        WIKI_PAGES.put("Venenatis and Spindel", List.of("Venenatis", "Spindel"));
        WIKI_PAGES.put("Vet'ion and Calvar'ion", List.of("Vet'ion", "Calvar'ion"));

        // --- Raids (drops are on chest pages, use DropsLineReward) ---
        WIKI_PAGES.put("Chambers of Xeric", List.of("Ancient chest"));
        WIKI_PAGES.put("Theatre of Blood", List.of("Monumental chest"));
        WIKI_PAGES.put("Tombs of Amascut", List.of("Chest (Tombs of Amascut)"));

        // --- Clues ---
        WIKI_PAGES.put("Beginner Treasure Trails", List.of("Reward casket (beginner)"));
        WIKI_PAGES.put("Easy Treasure Trails", List.of("Reward casket (easy)"));
        WIKI_PAGES.put("Medium Treasure Trails", List.of("Reward casket (medium)"));
        WIKI_PAGES.put("Hard Treasure Trails", List.of("Reward casket (hard)"));
        WIKI_PAGES.put("Elite Treasure Trails", List.of("Reward casket (elite)"));
        WIKI_PAGES.put("Master Treasure Trails", List.of("Reward casket (master)"));

        // --- Other name mismatches ---
        WIKI_PAGES.put("Chaos Druids", List.of("Chaos druid"));
        WIKI_PAGES.put("Cyclopes", List.of("Cyclops"));
        WIKI_PAGES.put("Glough's Experiments", List.of("Demonic gorilla"));
        WIKI_PAGES.put("Revenants", List.of("Revenant imp"));
        WIKI_PAGES.put("Undead Druids", List.of("Undead druid"));
        WIKI_PAGES.put("TzHaar", List.of("TzTok-Jad", "TzKal-Zuk"));
        WIKI_PAGES.put("Slayer", List.of(
            "Cave horror", "Basilisk Knight", "Cockatrice", "Crawling Hand",
            "Kurask", "Turoth", "Abyssal demon"
        ));
        WIKI_PAGES.put("Wintertodt", List.of("Supply crate (Wintertodt)"));

        // --- Minigames with specific chest/reward pages ---
        WIKI_PAGES.put("Royal Titans", List.of("Branda the Fire Queen", "Eldric the Ice King"));
        WIKI_PAGES.put("Hallowed Sepulchre", List.of("Grand Hallowed Coffin", "Coffin (Hallowed Sepulchre)"));
        WIKI_PAGES.put("Pyramid Plunder", List.of("Grand Gold Chest", "Sarcophagus (Pyramid Plunder)"));
        WIKI_PAGES.put("Soul Wars", List.of("Spoils of war"));
        WIKI_PAGES.put("Creature Creation", List.of("Unicow", "Newtroost"));
        WIKI_PAGES.put("Shades of Mort'ton", List.of("Gold key red", "Gold key brown", "Gold key crimson", "Gold key black", "Gold key purple"));
        WIKI_PAGES.put("Revenants", List.of("Template:Revenants/Drops"));

        // --- Runtime aliases (loot events -> data key) ---
        PAGE_ALIASES.put("chambers of xeric", "Chambers of Xeric");
        PAGE_ALIASES.put("chambers of xeric: challenge mode", "Chambers of Xeric");
        PAGE_ALIASES.put("theatre of blood", "Theatre of Blood");
        PAGE_ALIASES.put("theatre of blood: hard mode", "Theatre of Blood");
        PAGE_ALIASES.put("tombs of amascut", "Tombs of Amascut");
        PAGE_ALIASES.put("tombs of amascut: expert mode", "Tombs of Amascut");
        PAGE_ALIASES.put("the gauntlet", "The Gauntlet");
        PAGE_ALIASES.put("the corrupted gauntlet", "The Gauntlet");
        PAGE_ALIASES.put("clue scroll (beginner)", "Beginner Treasure Trails");
        PAGE_ALIASES.put("clue scroll (easy)", "Easy Treasure Trails");
        PAGE_ALIASES.put("clue scroll (medium)", "Medium Treasure Trails");
        PAGE_ALIASES.put("clue scroll (hard)", "Hard Treasure Trails");
        PAGE_ALIASES.put("clue scroll (elite)", "Elite Treasure Trails");
        PAGE_ALIASES.put("clue scroll (master)", "Master Treasure Trails");

        // --- Loot event name mismatches ---
        PAGE_ALIASES.put("barrows", "Barrows Chests");
        PAGE_ALIASES.put("the nightmare", "Nightmare");
        PAGE_ALIASES.put("phosani's nightmare", "Nightmare");
        PAGE_ALIASES.put("the leviathan", "Leviathan");
        PAGE_ALIASES.put("the whisperer", "Whisperer");
        PAGE_ALIASES.put("the mimic", "Mimic");
        PAGE_ALIASES.put("supply crate (wintertodt)", "Wintertodt");
        PAGE_ALIASES.put("reward cart (wintertodt)", "Wintertodt");
        PAGE_ALIASES.put("hallowed sack", "Hallowed Sepulchre");
        PAGE_ALIASES.put("spoils of war", "Soul Wars");
    }

    private static final String PRICES_API_BASE = "https://prices.runescape.wiki/api/v1/osrs/mapping";

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Map<String, Integer> itemIdMapping = new HashMap<>();

    public static void main(String[] args) throws Exception
    {
        new WikiScraper().run();
    }

    private void run() throws Exception
    {
        JsonObject root = new JsonObject();
        JsonObject monsters = new JsonObject();
        JsonObject aliases = new JsonObject();

        int scraped = 0;
        int failed = 0;

        fetchItemIdMapping();
        Map<String, Set<String>> clogItems = fetchCollectionLogItems();

        // Diagnostic: show which pages don't have matching clog sections
        Set<String> unmatchedPages = new LinkedHashSet<>();
        Set<String> usedSections = new HashSet<>();
        for (String page : ALL_CLOG_PAGES)
        {
            if (findClogItems(page, clogItems) != null)
            {
                usedSections.add(page);
            }
            else
            {
                unmatchedPages.add(page);
            }
        }
        if (!unmatchedPages.isEmpty())
        {
            System.out.println("Pages without matching clog section (" + unmatchedPages.size() + "):");
            for (String p : unmatchedPages) System.out.println("  - " + p);
        }
        Set<String> unusedSections = new LinkedHashSet<>(clogItems.keySet());
        unusedSections.removeAll(usedSections);
        // Remove sections that were matched via prefix lookup
        for (String page : ALL_CLOG_PAGES)
        {
            unusedSections.remove(page);
            unusedSections.remove("The " + page);
        }
        if (!unusedSections.isEmpty())
        {
            System.out.println("Clog sections without matching page (" + unusedSections.size() + "):");
            for (String s : unusedSections) System.out.println("  - " + s);
        }
        System.out.println();

        for (String page : ALL_CLOG_PAGES)
        {
            System.out.println("Scraping: " + page);

            try
            {
                List<String> wikiPages = WIKI_PAGES.getOrDefault(page, List.of(page));
                List<DropEntry> allDrops = new ArrayList<>();

                for (String wikiPage : wikiPages)
                {
                    List<DropEntry> drops = scrapeWikiPage(wikiPage);

                    if (!drops.isEmpty())
                    {
                        allDrops.addAll(drops);

                        // For multi-page entries, also store each individual monster
                        if (wikiPages.size() > 1)
                        {
                            String key = normalizeName(wikiPage);
                            if (!monsters.has(key))
                            {
                                Set<String> pageItems = findClogItems(page, clogItems);
                                List<DropEntry> clogDrops = pageItems != null
                                    ? filterToClogItems(drops, pageItems)
                                    : drops;
                                if (!clogDrops.isEmpty())
                                {
                                    clogDrops = resolveItemIds(clogDrops);
                                    monsters.add(key, buildMonsterJson(wikiPage, wikiPage, clogDrops));
                                    System.out.println("    + " + wikiPage + " (" + clogDrops.size() + " clog items)");
                                }
                            }
                        }
                    }

                    Thread.sleep(500);
                }

                List<DropEntry> deduped = deduplicateDrops(allDrops);

                Set<String> pageItems = findClogItems(page, clogItems);
                if (pageItems != null)
                {
                    deduped = filterToClogItems(deduped, pageItems);
                }

                deduped = resolveItemIds(deduped);

                if (!deduped.isEmpty())
                {
                    String wikiPage = wikiPages.size() == 1 ? wikiPages.get(0) : page;
                    monsters.add(normalizeName(page), buildMonsterJson(page, wikiPage, deduped));
                    System.out.println("  Found " + deduped.size() + " clog items");
                    scraped++;
                }
                else
                {
                    System.out.println("  WARNING: No drops found for " + page);
                    failed++;
                }
            }
            catch (Exception e)
            {
                System.out.println("  ERROR: " + e.getMessage());
                failed++;
            }
        }

        for (Map.Entry<String, String> entry : PAGE_ALIASES.entrySet())
        {
            aliases.addProperty(
                normalizeName(entry.getKey()),
                normalizeName(entry.getValue())
            );
        }

        root.add("monsters", monsters);
        root.add("aliases", aliases);

        Path output = Paths.get("src/main/resources/com/droppy/drops.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, gson.toJson(root));

        System.out.println();
        System.out.println("Done! Scraped " + scraped + " clog pages (" + monsters.size()
            + " total entries), " + failed + " without drops.");
        System.out.println("Output: " + output.toAbsolutePath());
    }

    private List<DropEntry> scrapeWikiPage(String wikiPage)
    {
        List<DropEntry> drops = tryFetchDrops(wikiPage);

        // If main page had no drops, try subpages
        if (drops.isEmpty())
        {
            for (String suffix : SUBPAGE_SUFFIXES)
            {
                drops = tryFetchDrops(wikiPage + suffix);
                if (!drops.isEmpty())
                {
                    break;
                }
            }
        }

        return drops;
    }

    private List<DropEntry> tryFetchDrops(String wikiPage)
    {
        try
        {
            String wikiText = fetchWikiText(wikiPage);
            if (wikiText == null || wikiText.isEmpty())
            {
                return List.of();
            }
            return parseDropsFromWikiText(wikiText);
        }
        catch (Exception e)
        {
            return List.of();
        }
    }

    private String fetchWikiText(String pageName) throws IOException
    {
        HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
            .addQueryParameter("action", "parse")
            .addQueryParameter("page", pageName)
            .addQueryParameter("prop", "wikitext")
            .addQueryParameter("format", "json")
            .addQueryParameter("redirects", "1")
            .build();

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                return null;
            }

            String body = response.body().string();
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();

            if (json.has("error"))
            {
                return null;
            }

            JsonObject parse = json.getAsJsonObject("parse");
            if (parse == null)
            {
                return null;
            }

            JsonObject wikitext = parse.getAsJsonObject("wikitext");
            if (wikitext == null)
            {
                return null;
            }

            JsonElement content = wikitext.get("*");
            return content != null ? content.getAsString() : null;
        }
    }

    private JsonObject buildMonsterJson(String displayName, String wikiPage, List<DropEntry> drops)
    {
        JsonObject obj = new JsonObject();
        obj.addProperty("monsterName", displayName);
        obj.addProperty("wikiPage", wikiPage);

        JsonArray dropsArr = new JsonArray();
        for (DropEntry drop : drops)
        {
            JsonObject d = new JsonObject();
            d.addProperty("itemName", drop.getItemName());
            d.addProperty("dropRate", drop.getDropRate());
            d.addProperty("itemId", drop.getItemId());
            if (drop.getRarityDisplay() != null)
            {
                d.addProperty("rarityDisplay", drop.getRarityDisplay());
            }
            dropsArr.add(d);
        }
        obj.add("drops", dropsArr);

        return obj;
    }

    private List<DropEntry> deduplicateDrops(List<DropEntry> drops)
    {
        Set<String> seen = new LinkedHashSet<>();
        List<DropEntry> result = new ArrayList<>();
        for (DropEntry drop : drops)
        {
            if (seen.add(drop.getItemName().toLowerCase()))
            {
                result.add(drop);
            }
        }
        return result;
    }

    private Map<String, Set<String>> fetchCollectionLogItems() throws Exception
    {
        Map<String, Set<String>> clogItems = new HashMap<>();

        System.out.println("Fetching Collection Log page...");
        String wikiText = fetchWikiText("Collection_log");
        if (wikiText == null || wikiText.isEmpty())
        {
            System.out.println("WARNING: Could not fetch Collection Log page — no filtering will be applied");
            return clogItems;
        }

        Matcher headerMatcher = CLOG_SECTION_HEADER.matcher(wikiText);
        List<String> names = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();

        while (headerMatcher.find())
        {
            names.add(headerMatcher.group(1).trim());
            positions.add(headerMatcher.start());
        }

        int totalItems = 0;
        for (int i = 0; i < names.size(); i++)
        {
            int start = positions.get(i);
            int end = (i + 1 < positions.size()) ? positions.get(i + 1) : wikiText.length();
            String sectionText = wikiText.substring(start, end);

            Set<String> items = new LinkedHashSet<>();
            Matcher plinkMatcher = PLINK_PATTERN.matcher(sectionText);
            while (plinkMatcher.find())
            {
                items.add(plinkMatcher.group(1).trim());
            }

            if (!items.isEmpty())
            {
                clogItems.put(names.get(i), items);
                totalItems += items.size();
            }
        }

        System.out.println("Found " + clogItems.size() + " collection log sections with " + totalItems + " total items");
        System.out.println();
        return clogItems;
    }

    private static final Map<String, List<String>> CLOG_SECTION_NAMES = new HashMap<>();

    static
    {
        CLOG_SECTION_NAMES.put("Sol Heredit", List.of("Fortis Colosseum"));
        CLOG_SECTION_NAMES.put("Colosseum", List.of("Fortis Colosseum"));
        CLOG_SECTION_NAMES.put("TzHaar", List.of("The Fight Caves", "The Inferno"));
        CLOG_SECTION_NAMES.put("Spindel", List.of("Venenatis and Spindel"));
    }

    private Set<String> findClogItems(String page, Map<String, Set<String>> clogItems)
    {
        List<String> mappedList = CLOG_SECTION_NAMES.get(page);
        if (mappedList != null)
        {
            Set<String> merged = new LinkedHashSet<>();
            for (String m : mappedList)
            {
                Set<String> items = clogItems.get(m);
                if (items != null) merged.addAll(items);
            }
            if (!merged.isEmpty()) return merged;
        }

        Set<String> items = clogItems.get(page);
        if (items != null) return items;

        items = clogItems.get("The " + page);
        if (items != null) return items;

        if (page.startsWith("The "))
        {
            items = clogItems.get(page.substring(4));
            if (items != null) return items;
        }

        String lowerPage = page.toLowerCase();
        for (Map.Entry<String, Set<String>> entry : clogItems.entrySet())
        {
            if (entry.getKey().toLowerCase().equals(lowerPage))
            {
                return entry.getValue();
            }
        }

        return null;
    }

    private List<DropEntry> filterToClogItems(List<DropEntry> drops, Set<String> clogItemNames)
    {
        Set<String> lowerNames = new HashSet<>();
        for (String name : clogItemNames)
        {
            lowerNames.add(name.toLowerCase());
        }

        List<DropEntry> filtered = new ArrayList<>();
        for (DropEntry drop : drops)
        {
            if (lowerNames.contains(drop.getItemName().trim().toLowerCase()))
            {
                filtered.add(drop);
            }
        }
        return filtered;
    }

    private List<DropEntry> parseDropsFromWikiText(String wikiText)
    {
        List<DropEntry> drops = new ArrayList<>();

        Matcher startMatcher = DROPS_LINE_START.matcher(wikiText);

        while (startMatcher.find())
        {
            int contentStart = startMatcher.end();
            int depth = 2; // already inside the opening {{
            int pos = contentStart;

            while (pos < wikiText.length() && depth > 0)
            {
                char c = wikiText.charAt(pos);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                pos++;
            }

            if (depth != 0)
            {
                continue;
            }

            // pos is past the closing }}, content is between | and }}
            String params = wikiText.substring(contentStart, pos - 2);
            Map<String, String> paramMap = parseTemplateParams(params);

            String name = paramMap.get("name");
            String rarity = paramMap.get("rarity");

            if (name == null || name.isEmpty())
            {
                continue;
            }

            double dropRate = parseRarity(rarity);
            if (dropRate <= 0 || dropRate >= 1.0)
            {
                continue;
            }

            String rarityDisplay = formatRarityDisplay(rarity, dropRate);

            int itemId = -1;
            if (paramMap.containsKey("id"))
            {
                try
                {
                    itemId = Integer.parseInt(paramMap.get("id").trim());
                }
                catch (NumberFormatException ignored)
                {
                }
            }

            drops.add(new DropEntry(name.trim(), dropRate, itemId, rarityDisplay));
        }

        return drops;
    }

    private Map<String, String> parseTemplateParams(String paramString)
    {
        Map<String, String> params = new HashMap<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < paramString.length(); i++)
        {
            char c = paramString.charAt(i);
            if (c == '{')
            {
                depth++;
                current.append(c);
            }
            else if (c == '}')
            {
                depth--;
                current.append(c);
            }
            else if (c == '|' && depth == 0)
            {
                parseParam(current.toString(), params);
                current = new StringBuilder();
            }
            else
            {
                current.append(c);
            }
        }

        if (current.length() > 0)
        {
            parseParam(current.toString(), params);
        }

        return params;
    }

    private void parseParam(String param, Map<String, String> params)
    {
        int eqIdx = param.indexOf('=');
        if (eqIdx > 0)
        {
            String key = param.substring(0, eqIdx).trim().toLowerCase();
            String value = param.substring(eqIdx + 1).trim();
            params.put(key, value);
        }
    }

    private double parseRarity(String rarity)
    {
        if (rarity == null || rarity.isEmpty())
        {
            return -1;
        }

        String cleaned = rarity.trim()
            .replaceAll("<!--.*?-->", "")
            .replaceAll("\\{\\{.*?\\}\\}", "")
            .replaceAll("~", "")
            .trim();

        Matcher fractionMatcher = FRACTION_PATTERN.matcher(cleaned);
        if (fractionMatcher.find())
        {
            double numerator = Double.parseDouble(fractionMatcher.group(1));
            double denominator = Double.parseDouble(fractionMatcher.group(2));
            if (denominator > 0)
            {
                return numerator / denominator;
            }
        }

        String lower = cleaned.toLowerCase();
        for (Map.Entry<String, Double> entry : RARITY_KEYWORDS.entrySet())
        {
            if (lower.contains(entry.getKey()))
            {
                return entry.getValue();
            }
        }

        return -1;
    }

    private String formatRarityDisplay(String rarity, double dropRate)
    {
        if (rarity == null || rarity.isEmpty())
        {
            long denom = Math.round(1.0 / dropRate);
            return "1/" + String.format("%,d", denom);
        }

        String cleaned = rarity.trim()
            .replaceAll("<!--.*?-->", "")
            .replaceAll("\\{\\{.*?\\}\\}", "")
            .replaceAll("~", "")
            .trim();

        Matcher fractionMatcher = FRACTION_PATTERN.matcher(cleaned);
        if (fractionMatcher.find())
        {
            String numStr = fractionMatcher.group(1);
            String denStr = fractionMatcher.group(2);

            try
            {
                double num = Double.parseDouble(numStr);
                double den = Double.parseDouble(denStr);
                if (num == Math.floor(num) && den == Math.floor(den))
                {
                    long n = (long) num;
                    long d = (long) den;

                    if (n <= 10)
                    {
                        return String.format("%,d", n) + "/" + String.format("%,d", d);
                    }

                    long simpleDenom = Math.round(1.0 / dropRate);
                    return "1/" + String.format("%,d", simpleDenom);
                }
            }
            catch (NumberFormatException ignored)
            {
            }

            return numStr + "/" + denStr;
        }

        long denom = Math.round(1.0 / dropRate);
        return "1/" + String.format("%,d", denom);
    }

    private void fetchItemIdMapping()
    {
        System.out.println("Fetching item ID mapping from prices API...");
        try
        {
            Request request = new Request.Builder()
                .url(PRICES_API_BASE)
                .header("User-Agent", USER_AGENT)
                .build();

            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    System.out.println("WARNING: Could not fetch item ID mapping");
                    return;
                }

                String body = response.body().string();
                JsonArray items = gson.fromJson(body, JsonArray.class);
                for (JsonElement el : items)
                {
                    JsonObject item = el.getAsJsonObject();
                    if (item.has("id") && item.has("name"))
                    {
                        String name = item.get("name").getAsString().toLowerCase().trim();
                        int id = item.get("id").getAsInt();
                        itemIdMapping.put(name, id);
                    }
                }

                System.out.println("Loaded " + itemIdMapping.size() + " item ID mappings");
            }
        }
        catch (Exception e)
        {
            System.out.println("WARNING: Failed to fetch item ID mapping: " + e.getMessage());
        }
    }

    private List<DropEntry> resolveItemIds(List<DropEntry> drops)
    {
        List<DropEntry> resolved = new ArrayList<>();
        for (DropEntry drop : drops)
        {
            int itemId = drop.getItemId();
            if (itemId <= 0)
            {
                String lookupName = drop.getItemName().toLowerCase().trim();
                Integer mappedId = itemIdMapping.get(lookupName);
                if (mappedId != null)
                {
                    itemId = mappedId;
                }
            }
            resolved.add(new DropEntry(drop.getItemName(), drop.getDropRate(), itemId, drop.getRarityDisplay()));
        }
        return resolved;
    }

    private String normalizeName(String name)
    {
        if (name == null || name.isEmpty())
        {
            return name;
        }

        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++)
        {
            if (i > 0)
            {
                sb.append(" ");
            }
            if (!words[i].isEmpty())
            {
                sb.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1)
                {
                    sb.append(words[i].substring(1));
                }
            }
        }
        return sb.toString();
    }
}
