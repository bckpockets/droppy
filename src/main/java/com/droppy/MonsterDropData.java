package com.droppy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds all drop data for a specific monster including its drops and wiki page name.
 */
public class MonsterDropData
{
    private final String monsterName;
    private final String wikiPage;
    private final List<DropEntry> drops;
    private long fetchedAt;

    public MonsterDropData(String monsterName, String wikiPage, List<DropEntry> drops)
    {
        this.monsterName = monsterName;
        this.wikiPage = wikiPage;
        this.drops = new ArrayList<>(drops);
        this.fetchedAt = System.currentTimeMillis();
    }

    public String getMonsterName()
    {
        return monsterName;
    }

    public String getWikiPage()
    {
        return wikiPage;
    }

    public List<DropEntry> getDrops()
    {
        return Collections.unmodifiableList(drops);
    }

    public long getFetchedAt()
    {
        return fetchedAt;
    }

    public boolean isStale()
    {
        // Cache for 1 hour
        return System.currentTimeMillis() - fetchedAt > 3_600_000;
    }
}
