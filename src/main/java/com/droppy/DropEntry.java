package com.droppy;

import lombok.Value;

/**
 * Represents a single drop from a monster's drop table.
 */
@Value
public class DropEntry
{
    String itemName;
    double dropRate;
    int itemId;
    boolean collectionLog;
    /**
     * Human-readable rarity string from the wiki (e.g. "3/128", "1/512").
     * Preserves the original fraction rather than collapsing to 1/X form.
     */
    String rarityDisplay;

    @Override
    public String toString()
    {
        return itemName + " (" + (rarityDisplay != null ? rarityDisplay : "?") + ")";
    }
}
