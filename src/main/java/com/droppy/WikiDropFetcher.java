package com.droppy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class WikiDropFetcher
{
    private static final String STATIC_DATA_BASE = "https://bckpockets.github.io/droppy/";
    private static final String WIKI_API_BASE = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "Droppy RuneLite Plugin (https://github.com/bckpockets/droppy)";

    private final OkHttpClient httpClient;
    private final Map<String, MonsterDropData> cache = new ConcurrentHashMap<>();

    private static final Pattern DROPS_LINE_PATTERN = Pattern.compile(
        "\\{\\{DropsLine\\s*\\|([^}]+)\\}\\}",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FRACTION_PATTERN = Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)"
    );

    private static final Map<String, Double> RARITY_KEYWORDS = new HashMap<>();

    // In-game source name -> wiki page with the actual drop table.
    // Only needed when the loot event name doesn't match the wiki page.
    private static final Map<String, String> PAGE_ALIASES = new HashMap<>();

    // Subpages to try when the main page has no {{DropsLine}} entries
    private static final String[] SUBPAGE_SUFFIXES = {"/Loot", "/Rewards"};

    static
    {
        RARITY_KEYWORDS.put("always", 1.0);
        RARITY_KEYWORDS.put("common", 1.0 / 16.0);
        RARITY_KEYWORDS.put("uncommon", 1.0 / 64.0);
        RARITY_KEYWORDS.put("rare", 1.0 / 128.0);
        RARITY_KEYWORDS.put("very rare", 1.0 / 512.0);

        // Raids - loot tables are on subpages
        PAGE_ALIASES.put("chambers of xeric", "Chambers of Xeric");
        PAGE_ALIASES.put("chambers of xeric: challenge mode", "Chambers of Xeric");
        PAGE_ALIASES.put("theatre of blood", "Theatre of Blood");
        PAGE_ALIASES.put("theatre of blood: hard mode", "Theatre of Blood");
        PAGE_ALIASES.put("tombs of amascut", "Tombs of Amascut");
        PAGE_ALIASES.put("tombs of amascut: expert mode", "Tombs of Amascut");

        // Gauntlet
        PAGE_ALIASES.put("the gauntlet", "The Gauntlet");
        PAGE_ALIASES.put("the corrupted gauntlet", "The Gauntlet");

        // Clue scrolls
        PAGE_ALIASES.put("clue scroll (beginner)", "Reward casket (beginner)");
        PAGE_ALIASES.put("clue scroll (easy)", "Reward casket (easy)");
        PAGE_ALIASES.put("clue scroll (medium)", "Reward casket (medium)");
        PAGE_ALIASES.put("clue scroll (hard)", "Reward casket (hard)");
        PAGE_ALIASES.put("clue scroll (elite)", "Reward casket (elite)");
        PAGE_ALIASES.put("clue scroll (master)", "Reward casket (master)");
    }

    public WikiDropFetcher(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public MonsterDropData fetchMonsterDrops(String monsterName)
    {
        String normalizedName = normalizeName(monsterName);

        MonsterDropData cached = cache.get(normalizedName);
        if (cached != null && !cached.isStale())
        {
            return cached;
        }

        try
        {
            // Check aliases first (e.g. "Clue Scroll (Hard)" -> "Reward casket (hard)")
            String alias = PAGE_ALIASES.get(monsterName.toLowerCase().trim());
            String pageName = alias != null ? alias : normalizedName;

            // Try static JSON first (faster, more reliable)
            List<DropEntry> drops = tryFetchFromStatic(pageName);

            // Fall back to wiki API if static data unavailable
            if (drops.isEmpty())
            {
                drops = tryFetchDrops(pageName);

                // If main page had no drops, try subpages like /Loot, /Rewards
                if (drops.isEmpty())
                {
                    for (String suffix : SUBPAGE_SUFFIXES)
                    {
                        drops = tryFetchDrops(pageName + suffix);
                        if (!drops.isEmpty())
                        {
                            break;
                        }
                    }
                }
            }

            if (drops.isEmpty())
            {
                log.warn("No drops found for: {} (tried {} and subpages)", monsterName, pageName);
                return null;
            }

            MonsterDropData data = new MonsterDropData(monsterName, pageName, drops);
            cache.put(normalizedName, data);
            return data;
        }
        catch (Exception e)
        {
            log.error("Error fetching drops for {}: {}", monsterName, e.getMessage());
            return cached;
        }
    }

    private List<DropEntry> tryFetchFromStatic(String pageName)
    {
        try
        {
            String filename = normalizeToFilename(pageName);
            String url = STATIC_DATA_BASE + filename + ".json";

            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();

            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    return List.of();
                }

                String body = response.body().string();
                JsonObject json = new JsonParser().parse(body).getAsJsonObject();

                JsonArray dropsArray = json.getAsJsonArray("drops");
                if (dropsArray == null)
                {
                    return List.of();
                }

                List<DropEntry> drops = new ArrayList<>();
                for (JsonElement el : dropsArray)
                {
                    JsonObject dropObj = el.getAsJsonObject();
                    String name = dropObj.get("name").getAsString();
                    double rate = dropObj.get("rate").getAsDouble();
                    String rateDisplay = dropObj.get("rateDisplay").getAsString();
                    int itemId = dropObj.has("itemId") ? dropObj.get("itemId").getAsInt() : -1;

                    drops.add(new DropEntry(name, rate, itemId, rateDisplay));
                }

                log.debug("Loaded {} drops from static data for {}", drops.size(), pageName);
                return drops;
            }
        }
        catch (Exception e)
        {
            log.debug("Static data not available for {}: {}", pageName, e.getMessage());
            return List.of();
        }
    }

    private String normalizeToFilename(String name)
    {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private List<DropEntry> tryFetchDrops(String pageName)
    {
        try
        {
            String wikiText = fetchWikiText(pageName);
            if (wikiText == null || wikiText.isEmpty())
            {
                return List.of();
            }
            List<DropEntry> drops = parseDropsFromWikiText(wikiText);
            return drops;
        }
        catch (Exception e)
        {
            log.debug("No drops on page {}: {}", pageName, e.getMessage());
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
            .build();

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                log.warn("Wiki API returned status {} for page {}", response.code(), pageName);
                return null;
            }

            String body = response.body().string();
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();

            if (json.has("error"))
            {
                log.warn("Wiki API error for {}: {}", pageName,
                    json.getAsJsonObject("error").get("info").getAsString());
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

    public List<String> searchMonsters(String query)
    {
        List<String> results = new ArrayList<>();

        try
        {
            HttpUrl url = HttpUrl.parse(WIKI_API_BASE).newBuilder()
                .addQueryParameter("action", "opensearch")
                .addQueryParameter("search", query)
                .addQueryParameter("limit", "10")
                .addQueryParameter("namespace", "0")
                .addQueryParameter("format", "json")
                .build();

            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();

            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    return results;
                }

                JsonArray arr = new JsonParser().parse(response.body().string()).getAsJsonArray();
                if (arr.size() >= 2)
                {
                    JsonArray names = arr.get(1).getAsJsonArray();
                    for (JsonElement el : names)
                    {
                        results.add(el.getAsString());
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.error("Error searching monsters: {}", e.getMessage());
        }

        return results;
    }

    private List<DropEntry> parseDropsFromWikiText(String wikiText)
    {
        List<DropEntry> drops = new ArrayList<>();
        Matcher matcher = DROPS_LINE_PATTERN.matcher(wikiText);

        while (matcher.find())
        {
            String params = matcher.group(1);
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

    // Format drop rate for display. For complex wiki fractions (like 499/250000),
    // simplify to 1/X format if numerator is high.
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

                    // If numerator is small (1-10), keep original format
                    if (n <= 10)
                    {
                        return String.format("%,d", n) + "/" + String.format("%,d", d);
                    }

                    // For large numerators (complex wiki calculations), simplify to 1/X
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

    // Returns cached data without triggering a fetch. Null if not cached or stale.
    public MonsterDropData getCachedData(String monsterName)
    {
        String normalizedName = normalizeName(monsterName);
        MonsterDropData cached = cache.get(normalizedName);
        return (cached != null && !cached.isStale()) ? cached : null;
    }

    public void clearCache()
    {
        cache.clear();
    }
}
