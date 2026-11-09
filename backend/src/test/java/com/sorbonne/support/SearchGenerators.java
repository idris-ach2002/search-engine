package com.sorbonne.support;

import java.util.Random;

/** Small, reproducible strings for KMP regression tests. */
public final class SearchGenerators {
    private SearchGenerators() {
    }

    public static String text(Random random, int maxLength, String alphabet) {
        int length = random.nextInt(maxLength + 1);
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return result.toString();
    }
}
