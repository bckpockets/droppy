package com.droppy;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WikiDropFetcher
{
    private final Map<String, MonsterDropData> dropData = new ConcurrentHashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public WikiDropFetcher(Gson gson)
    {
        loadBundledData(gson);
    }

    private void loadBundledData(Gson gson)
    {
        try (InputStream is = getClass().getResourceAsStream("drops.json"))
        {
            if (is == null)
            {
                log.warn("Bundled drops.json not found — run ./gradlew updateDrops to generate it");
                return;
            }

            String json = new String(is.readAllBytes());
            JsonObject root = gson.fromJson(json, JsonObject.class);

            JsonObject aliasObj = root.getAsJsonObject("aliases");
            if (aliasObj != null)
            {
                for (Map.Entry<String, JsonElement> entry : aliasObj.entrySet())
                {
                    aliases.put(entry.getKey(), entry.getValue().getAsString());
                }
            }

            JsonObject monstersObj = root.getAsJsonObject("monsters");
            if (monstersObj != null)
            {
                for (Map.Entry<String, JsonElement> entry : monstersObj.entrySet())
                {
                    JsonObject m = entry.getValue().getAsJsonObject();
                    String monsterName = m.get("monsterName").getAsString();
                    String wikiPage = m.get("wikiPage").getAsString();

                    List<DropEntry> drops = new ArrayList<>();
                    JsonArray dropsArr = m.getAsJsonArray("drops");
                    if (dropsArr != null)
                    {
                        for (JsonElement dropEl : dropsArr)
                        {
                            JsonObject d = dropEl.getAsJsonObject();
                            String rarityDisplay = d.has("rarityDisplay") && !d.get("rarityDisplay").isJsonNull()
                                ? d.get("rarityDisplay").getAsString()
                                : null;
                            drops.add(new DropEntry(
                                d.get("itemName").getAsString(),
                                d.get("dropRate").getAsDouble(),
                                d.get("itemId").getAsInt(),
                                rarityDisplay
                            ));
                        }
                    }

                    dropData.put(entry.getKey(), new MonsterDropData(monsterName, wikiPage, drops));
                }
            }

            log.info("Loaded {} monsters and {} aliases from bundled drop data",
                dropData.size(), aliases.size());
        }
        catch (Exception e)
        {
            log.error("Failed to load bundled drop data: {}", e.getMessage());
        }

        // Loot event name -> clog page name
        addAlias("Barrows", "Barrows Chests");
        addAlias("The Nightmare", "Nightmare");
        addAlias("Phosani's Nightmare", "Nightmare");
        addAlias("The Leviathan", "Leviathan");
        addAlias("The Whisperer", "Whisperer");
        addAlias("The Mimic", "Mimic");
        addAlias("Supply Crate (Wintertodt)", "Wintertodt");
        addAlias("Reward Cart (Wintertodt)", "Wintertodt");
        addAlias("Hallowed Sack", "Hallowed Sepulchre");
        addAlias("Spoils Of War", "Soul Wars");

        // Irregular plurals (NPC name -> clog page name)
        addAlias("Cyclops", "Cyclopes");
    }

    private void addAlias(String from, String to)
    {
        String normalizedFrom = normalizeName(from);
        String normalizedTo = normalizeName(to);
        if (!aliases.containsKey(normalizedFrom))
        {
            aliases.put(normalizedFrom, normalizedTo);
        }
    }

    public MonsterDropData getDropData(String monsterName)
    {
        String normalized = normalizeName(monsterName);

        MonsterDropData data = dropData.get(normalized);
        if (data != null)
        {
            return data;
        }

        // Check aliases (e.g. "The Corrupted Gauntlet" -> "The Gauntlet")
        String aliasTarget = aliases.get(normalized);
        if (aliasTarget != null)
        {
            data = dropData.get(aliasTarget);
            if (data != null)
            {
                return data;
            }
        }

        // Try singular/plural variants
        // e.g. "Tormented Demon" -> "Tormented Demons"
        data = dropData.get(normalized + "s");
        if (data != null)
        {
            return data;
        }

        data = dropData.get(normalized + "es");
        if (data != null)
        {
            return data;
        }

        if (normalized.endsWith("s"))
        {
            data = dropData.get(normalized.substring(0, normalized.length() - 1));
            if (data != null)
            {
                return data;
            }
        }

        if (normalized.endsWith("es"))
        {
            data = dropData.get(normalized.substring(0, normalized.length() - 2));
            if (data != null)
            {
                return data;
            }
        }

        return null;
    }


    public java.util.Collection<MonsterDropData> getAllMonsterData()
    {
        return dropData.values();
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