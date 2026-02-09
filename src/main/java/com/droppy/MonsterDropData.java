package com.droppy;

import java.util.List;
import lombok.Getter;

@Getter
public class MonsterDropData
{
    private final String monsterName;
    private final String wikiPage;
    private final List<DropEntry> drops;

    public MonsterDropData(String monsterName, String wikiPage, List<DropEntry> drops)
    {
        this.monsterName = monsterName;
        this.wikiPage = wikiPage;
        this.drops = List.copyOf(drops);
    }
}
