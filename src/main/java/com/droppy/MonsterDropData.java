package com.droppy;

import java.util.List;
import lombok.Getter;

/**
 * Holds all drop data for a specific monster including its drops and wiki page name.
 */
@Getter
public class MonsterDropData
{
    private final String monsterName;
    private final String wikiPage;
    private final List<DropEntry> drops;
    private final long fetchedAt;

    public MonsterDropData(String monsterName, String wikiPage, List<DropEntry> drops)
    {
        this.monsterName = monsterName;
        this.wikiPage = wikiPage;
        this.drops = List.copyOf(drops);
        this.fetchedAt = System.currentTimeMillis();
    }

    public boolean isStale()
    {
        return System.currentTimeMillis() - fetchedAt > 3_600_000;
    }
}
