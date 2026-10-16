package com.droppy;

import com.google.gson.Gson;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BundledDropDataTest
{
    private static final Pattern FRACTION = Pattern.compile("([0-9.]+)/([0-9.]+)");

    @Test
    public void bundledFractionsMatchTheirNumericRates()
    {
        WikiDropFetcher fetcher = new WikiDropFetcher(new Gson());
        assertTrue(fetcher.getAllMonsterData().size() >= 100);

        int checked = 0;
        for (MonsterDropData source : fetcher.getAllMonsterData())
        {
            for (DropEntry drop : source.getDrops())
            {
                assertTrue(drop.getDropRate() > 0 && drop.getDropRate() < 1);
                assertTrue(drop.getRollsPerKill() >= 1);

                if (drop.getRarityDisplay() != null)
                {
                    Matcher fraction = FRACTION.matcher(drop.getRarityDisplay().replace(",", ""));
                    assertTrue(source.getMonsterName() + ": " + drop, fraction.matches());
                    double displayedRate = Double.parseDouble(fraction.group(1))
                        / Double.parseDouble(fraction.group(2));
                    assertEquals(source.getMonsterName() + ": " + drop,
                        displayedRate, drop.getDropRate(), 1e-12);
                    checked++;
                }
            }
        }
        assertTrue(checked >= 1000);
    }

    @Test
    public void bundledDataIncludesMultiRollAndExpandedRates()
    {
        WikiDropFetcher fetcher = new WikiDropFetcher(new Gson());
        assertTrue(fetcher.getDropData("Easy Treasure Trails").getDrops().size() >= 100);
        assertNotNull(fetcher.getDropData("Reward Chest (The Gauntlet)"));
        assertNotNull(fetcher.getDropData("Corrupted Gauntlet"));

        DropEntry fang = find(fetcher, "Zulrah", "Tanzanite fang");
        assertEquals(2, fang.getRollsPerKill());
        assertEquals(1.0 / 1024.0, fang.getDropRate(), 1e-12);

        DropEntry gloves = find(fetcher, "Grotesque Guardians", "Granite gloves");
        assertEquals(2, gloves.getRollsPerKill());

        assertNotNull(find(fetcher, "Alchemical Hydra", "Hydra's eye"));
    }

    private static DropEntry find(WikiDropFetcher fetcher, String sourceName, String itemName)
    {
        MonsterDropData source = fetcher.getDropData(sourceName);
        assertNotNull(sourceName, source);
        return source.getDrops().stream()
            .filter(drop -> drop.getItemName().equals(itemName))
            .findFirst()
            .orElseThrow(() -> new AssertionError(sourceName + ": " + itemName));
    }
}
