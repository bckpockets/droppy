package com.droppy;

import org.junit.Test;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class WikiScraperRarityTest
{
    @Test
    public void qualitativeOrVariableRarityIsNotInventedAsAnExactRate()
    {
        WikiScraper scraper = new WikiScraper();

        assertEquals(-1.0, scraper.parseRarity("Very rare"), 0);
        assertEquals(-1.0, scraper.parseRarity("1/100 or 1/50"), 0);
    }

    @Test
    public void fractionDisplayPreservesTheOriginalRatio()
    {
        WikiScraper scraper = new WikiScraper();

        assertEquals(20.0 / 69.0, scraper.parseRarity("20/69"), 0);
        assertEquals("20/69", scraper.formatRarityDisplay("20/69", 20.0 / 69.0));
        assertEquals("60/100", scraper.formatRarityDisplay("60/100", 0.6));
        assertEquals("1/181.1", scraper.formatRarityDisplay("1/181.1", 1.0 / 181.1));
    }

    @Test
    public void dropLineKeepsItsRollCount()
    {
        WikiScraper scraper = new WikiScraper();
        List<DropEntry> drops = scraper.parseDropsFromWikiText(
            "{{DropsLine|name=Tanzanite fang|quantity=1|rarity=1/1024|rolls=2}}");

        assertEquals(1, drops.size());
        assertEquals(2, drops.get(0).getRollsPerKill());
    }
}
