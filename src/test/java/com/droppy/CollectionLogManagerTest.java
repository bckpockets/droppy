package com.droppy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CollectionLogManagerTest
{
    @Test
    public void mixedCollectionLogCountersDoNotBecomeSharedKc()
    {
        assertEquals("The Gauntlet",
            CollectionLogManager.sourceForCounter("The Gauntlet", "Gauntlet completions"));
        assertEquals("The Corrupted Gauntlet",
            CollectionLogManager.sourceForCounter("The Gauntlet", "Corrupted completions"));
        assertEquals("Dagannoth Rex",
            CollectionLogManager.sourceForCounter("Dagannoth Kings", "Rex kills"));
        assertNull(CollectionLogManager.sourceForCounter("The Gauntlet", "Total"));
    }
}
