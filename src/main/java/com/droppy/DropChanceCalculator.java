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

        return -Math.expm1(killCount * Math.log1p(-dropRate));
    }

    public static String formatPercent(double probability)
    {
        if (probability <= 0)
        {
            return "0%";
        }
        if (probability >= 1.0)
        {
            return "100%";
        }

        double percent = probability * 100.0;

        if (percent < 0.01)
        {
            return "<0.01%";
        }

        if (percent >= 99.995)
        {
            return ">99.99%";
        }

        // Full precision
        if (percent >= 10.0)
        {
            return String.format("%.2f%%", percent);
        }
        return String.format("%.4f%%", percent);
    }

    // n = log(1 - P) / log(1 - r)
    public static int killsForProbability(double dropRate, double targetProbability)
    {
        if (dropRate <= 0 || dropRate >= 1.0 || targetProbability <= 0 || targetProbability >= 1.0)
        {
            return -1;
        }

        double kills = Math.ceil(Math.log1p(-targetProbability) / Math.log1p(-dropRate));
        return kills > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) kills;
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
