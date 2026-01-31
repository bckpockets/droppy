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

    @Override
    public String toString()
    {
        return itemName + " (1/" + (dropRate > 0 ? Math.round(1.0 / dropRate) : "?") + ")";
    }
}
