package com.droppy;

// P(at least 1 drop in N kills) = 1 - (1 - dropRate)^N
public class DropChanceCalculator
{
    public static double calculateChance(double dropRate, int killCount)
    {
        if (dropRate <= 0 || killCount <= 0)
        {
            return 0.0;
        }

        if (dropRate >= 1.0)
        {
            return 1.0;
        }

        return 1.0 - Math.pow(1.0 - dropRate, killCount);
    }

    public static String formatPercent(double probability)
    {
        if (probability <= 0)
        {
            return "0%";
        }

        double percent = probability * 100.0;

        if (percent >= 99.5)
        {
            return "100%";
        }

        if (percent < 1)
        {
            return "<1%";
        }

        // Whole numbers only - keeps it short
        return String.format("%.0f%%", percent);
    }

    // n = log(1 - P) / log(1 - r)
    public static int killsForProbability(double dropRate, double targetProbability)
    {
        if (dropRate <= 0 || dropRate >= 1.0 || targetProbability <= 0 || targetProbability >= 1.0)
        {
            return -1;
        }

        return (int) Math.ceil(Math.log(1.0 - targetProbability) / Math.log(1.0 - dropRate));
    }

    public static String expectedKills(double dropRate)
    {
        if (dropRate <= 0)
        {
            return "N/A";
        }
        return String.format("%,d kc", Math.round(1.0 / dropRate));
    }

    public static String formatDropRate(double dropRate)
    {
        if (dropRate <= 0)
        {
            return "N/A";
        }
        if (dropRate >= 1.0)
        {
            return "Always";
        }

        long denominator = Math.round(1.0 / dropRate);
        return "1/" + String.format("%,d", denominator);
    }
}
