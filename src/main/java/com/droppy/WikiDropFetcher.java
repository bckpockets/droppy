package com.droppy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches monster drop tables from the OSRS Wiki API and parses drop rates.
 */
@Slf4j
public class WikiDropFetcher
{
    private static final String WIKI_API_URL = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "Droppy RuneLite Plugin - Drop Chance Calculator";

    // Cache of fetched monster data
    private final Map<String, MonsterDropData> cache = new ConcurrentHashMap<>();

    // Pattern to match DropsLine templates in wiki markup
    // {{DropsLine|name=Item|quantity=1|rarity=1/512|raritynotes=...}}
    private static final Pattern DROPS_LINE_PATTERN = Pattern.compile(
        "\\{\\{DropsLine\\s*\\|([^}]+)\\}\\}",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern to parse rarity fractions like 1/512, 3/256, etc.
    private static final Pattern FRACTION_PATTERN = Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)"
    );

    // Known rarity keywords mapped to approximate rates
    private static final Map<String, Double> RARITY_KEYWORDS = new HashMap<>();

    static
    {
        RARITY_KEYWORDS.put("always", 1.0);
        RARITY_KEYWORDS.put("common", 1.0 / 16.0);
        RARITY_KEYWORDS.put("uncommon", 1.0 / 64.0);
        RARITY_KEYWORDS.put("rare", 1.0 / 128.0);
        RARITY_KEYWORDS.put("very rare", 1.0 / 512.0);
    }

    /**
     * Fetches drop data for a monster. Uses cache if available and not stale.
     */
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
            String wikiText = fetchWikiText(normalizedName);
            if (wikiText == null || wikiText.isEmpty())
            {
                log.warn("No wiki text found for monster: {}", monsterName);
                return null;
            }

            List<DropEntry> drops = parseDropsFromWikiText(wikiText);
            if (drops.isEmpty())
            {
                log.warn("No drops parsed for monster: {}", monsterName);
                return null;
            }

            MonsterDropData data = new MonsterDropData(monsterName, normalizedName, drops);
            cache.put(normalizedName, data);
            return data;
        }
        catch (Exception e)
        {
            log.error("Error fetching drops for {}: {}", monsterName, e.getMessage());
            return cached; // Return stale cache if available
        }
    }

    /**
     * Fetches the raw wikitext for a given page from the OSRS Wiki API.
     */
    private String fetchWikiText(String pageName) throws Exception
    {
        String encodedPage = URLEncoder.encode(pageName, StandardCharsets.UTF_8.toString());
        String urlStr = WIKI_API_URL
            + "?action=parse"
            + "&page=" + encodedPage
            + "&prop=wikitext"
            + "&format=json";

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200)
        {
            log.warn("Wiki API returned status {} for page {}", responseCode, pageName);
            return null;
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                response.append(line);
            }
        }

        // Parse JSON to extract wikitext
        JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
        if (json.has("error"))
        {
            log.warn("Wiki API error for page {}: {}", pageName,
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
        if (content == null)
        {
            return null;
        }

        return content.getAsString();
    }

    /**
     * Parses DropsLine templates from wiki markup to extract drop entries.
     */
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
            if (dropRate <= 0)
            {
                continue;
            }

            // Skip "always" drops (bones, ashes, etc.) - not interesting for collection log
            if (dropRate >= 1.0)
            {
                continue;
            }

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

            drops.add(new DropEntry(name.trim(), dropRate, itemId, true));
        }

        return drops;
    }

    /**
     * Parses template parameters from a DropsLine string like "name=Item|quantity=1|rarity=1/512"
     */
    private Map<String, String> parseTemplateParams(String paramString)
    {
        Map<String, String> params = new HashMap<>();
        // Split on | but not inside nested templates {{ }}
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

    /**
     * Parses a rarity string into a decimal drop rate.
     * Handles fractions (1/512), keywords (Rare), and special formats.
     */
    private double parseRarity(String rarity)
    {
        if (rarity == null || rarity.isEmpty())
        {
            return -1;
        }

        String cleaned = rarity.trim()
            .replaceAll("<!--.*?-->", "")  // Remove HTML comments
            .replaceAll("\\{\\{.*?\\}\\}", "")  // Remove nested templates
            .replaceAll("~", "")  // Remove approximate symbols
            .trim();

        // Check for fraction format: X/Y
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

        // Check for keyword rarity
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

    /**
     * Normalizes a monster name for wiki lookup.
     * Capitalizes first letter of each word, replaces spaces with underscores for URL.
     */
    private String normalizeName(String name)
    {
        if (name == null || name.isEmpty())
        {
            return name;
        }

        // Capitalize first letter of each word
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

    /**
     * Clears the cache entirely.
     */
    public void clearCache()
    {
        cache.clear();
    }

    /**
     * Searches for monsters matching a query by checking the wiki for search results.
     */
    public List<String> searchMonsters(String query)
    {
        List<String> results = new ArrayList<>();

        try
        {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String urlStr = WIKI_API_URL
                + "?action=opensearch"
                + "&search=" + encoded
                + "&limit=10"
                + "&namespace=0"
                + "&format=json";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);

            if (conn.getResponseCode() != 200)
            {
                return results;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    response.append(line);
                }
            }

            // opensearch returns: [query, [results], [descriptions], [urls]]
            com.google.gson.JsonArray arr = JsonParser.parseString(response.toString()).getAsJsonArray();
            if (arr.size() >= 2)
            {
                com.google.gson.JsonArray names = arr.get(1).getAsJsonArray();
                for (JsonElement el : names)
                {
                    results.add(el.getAsString());
                }
            }
        }
        catch (Exception e)
        {
            log.error("Error searching monsters: {}", e.getMessage());
        }

        return results;
    }
}
