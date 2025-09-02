package com.lostinbedrock.discordmod;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationParser {
    private static final Pattern P = Pattern.compile("(?i)\\s*(\\d+)\\s*([wdhms])");

    public static long parseMillis(String input) {
        if (input == null || input.trim().isEmpty()) return -1L;
        long total = 0L;
        Matcher m = P.matcher(input);
        boolean any = false;
        while (m.find()) {
            any = true;
            long num = Long.parseLong(m.group(1));
            char unit = m.group(2).toLowerCase(Locale.ROOT).charAt(0);
            switch (unit) {
                case 'w' -> total += num * 7L * 24L * 60L * 60L * 1000L;
                case 'd' -> total += num * 24L * 60L * 60L * 1000L;
                case 'h' -> total += num * 60L * 60L * 1000L;
                case 'm' -> total += num * 60L * 1000L;
                case 's' -> total += num * 1000L;
            }
        }
        return any ? total : -1L;
    }
}
