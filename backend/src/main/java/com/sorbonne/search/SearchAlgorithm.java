package com.sorbonne.search;

/** Algorithm used by literal searches. */
public interface SearchAlgorithm<P> {
    /** Returns true when the pattern occurs in the text. */
    boolean search(String text, P pattern);
}
