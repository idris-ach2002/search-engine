package com.sorbonne.backend.search.service;

import com.sorbonne.automata.Automaton;
import com.sorbonne.backend.search.repository.SearchIndexRepository.IndexedTerm;
import com.sorbonne.regex.DFA;
import com.sorbonne.regex.DFAM;
import com.sorbonne.regex.NFA;
import com.sorbonne.regex.RegexParser;
import com.sorbonne.regex.SyntaxTree;
import com.sorbonne.search.NativeSearch;
import com.sorbonne.search.PreparedSearch;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RegexTermMatcher {

    private final RegexPatternNormalizer patternNormalizer;

    public RegexTermMatcher(RegexPatternNormalizer patternNormalizer) {
        this.patternNormalizer = patternNormalizer;
    }

    /**
     * Compiles the expression with the Project 1 pipeline:
     * syntax tree -> NFA -> search DFA -> Hopcroft minimization -> native table.
     * The compiled matcher is then reused for every indexed term in the request.
     */
    public MatchResult match(String regex, List<IndexedTerm> terms) {
        String normalizedRegex = patternNormalizer.normalize(regex);

        try {
            SyntaxTree syntaxTree = RegexParser.parse(normalizedRegex);
            Automaton nfa = NFA.buildNFA(syntaxTree);
            Automaton dfa = DFA.forSearch(nfa);
            Automaton minimizedDfa = DFAM.minimize(dfa);
            PreparedSearch prepared = NativeSearch.fromSearchDfa(minimizedDfa);

            List<IndexedTerm> matches = terms.stream()
                    .filter(term -> prepared.search(term.value()))
                    .toList();

            return new MatchResult(
                    normalizedRegex,
                    matches,
                    nfa.getStates().size(),
                    dfa.getStates().size(),
                    minimizedDfa.getStates().size()
            );
        } catch (Exception exception) {
            throw invalidRegex(exception);
        }
    }

    private static IllegalArgumentException invalidRegex(Exception exception) {
        if (exception instanceof IllegalArgumentException illegalArgumentException
                && illegalArgumentException.getMessage() != null
                && illegalArgumentException.getMessage().startsWith("Regex ")) {
            return illegalArgumentException;
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return new IllegalArgumentException("Invalid regex: " + message, exception);
    }

    public record MatchResult(
            String normalizedRegex,
            List<IndexedTerm> terms,
            int nfaStates,
            int dfaStates,
            int minimizedDfaStates
    ) {
    }
}
