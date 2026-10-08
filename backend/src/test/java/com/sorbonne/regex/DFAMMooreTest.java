package com.sorbonne.regex;

import static org.junit.jupiter.api.Assertions.*;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import java.util.Set;
import com.sorbonne.support.AutomatonBuilder;
import com.sorbonne.support.SearchGenerators;
import org.junit.jupiter.api.Test;

/**
 * Vérifie la minimisation d'un automate déterministe par l'algorithme de Moore.
 *
 * <p>Les scénarios couvrent le contrat de la méthode provisoire, la fusion
 * d'états équivalents, les transitions absentes et la conservation du langage
 * reconnu.</p>
 */
class DFAMMooreTest {
    /** Crée une suite de tests indépendante. */
    DFAMMooreTest() {
    }

    /**
     * Vérifie que Moore construit un automate réellement minimisé.
     *
     * <p>Scénario : le DFA de {@code a|bc*} contient deux états finaux
     * équivalents après la lecture de {@code b}. Résultat attendu : ces états
     * sont fusionnés dans un nouvel automate de trois états.</p>
     */
    @Test
    void minimizesEquivalentStatesInRegexDfa() throws Exception {
        Automaton dfa = DFA.convert(NFA.buildNFA(RegexParser.parse("a|bc*")));
        Automaton minimized = DFAMMoore.minimize(dfa, false);

        assertNotSame(dfa, minimized);
        assertEquals(3, minimized.getStates().size());
        assertEquals(2, minimized.getFinalStates().size());
        for (String word : SearchGenerators.words("abc", 3)) {
            assertEquals(
                    SearchGenerators.accepts(dfa, word),
                    SearchGenerators.accepts(minimized, word),
                    word);
        }
    }

    /**
     * Vérifie le rejet d'un automate nul par le point d'intégration.
     *
     * <p>Résultat attendu : une {@link NullPointerException} est levée dès
     * l'appel.</p>
     */
    @Test
    @SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
    void rejectsNullAtIntegrationPoint() {
        assertThrows(NullPointerException.class, () -> DFAMMoore.minimize(null, false));
    }

    /**
     * Vérifie la fusion de deux états ayant le même comportement futur.
     *
     * <p>Scénario : les états {@code a} et {@code b} atteignent tous deux
     * l'état final sur {@code x}. Résultat attendu : Moore les regroupe dans
     * un seul état et le langage reconnu reste inchangé.</p>
     */
    @Test
    void mergesEquivalentStates() {
        Automaton dfa = new AutomatonBuilder()
                .state("s", Status.ENTER)
                .state("a", Status.INTERMEDIATE)
                .state("b", Status.INTERMEDIATE)
                .state("f", Status.FINAL)
                .character("s", "a", 'a')
                .character("s", "b", 'b')
                .character("a", "f", 'x')
                .character("b", "f", 'x')
                .build();

        Automaton minimized = DFAMMoore.minimize(dfa, false);

        assertEquals(3, minimized.getStates().size());
        assertEquals(1, minimized.getFinalStates().size());
        assertTrue(SearchGenerators.accepts(minimized, "ax"));
        assertTrue(SearchGenerators.accepts(minimized, "bx"));
        assertFalse(SearchGenerators.accepts(minimized, "a"));
        assertFalse(SearchGenerators.accepts(minimized, "abx"));
        assertTrue(minimized.getStates().stream()
                .anyMatch(state -> state.getLabel().contains("a") && state.getLabel().contains("b")));
        DFATest.assertDeterministic(minimized);
    }

    /**
     * Vérifie qu'une transition absente est traitée par un puits virtuel.
     *
     * <p>Scénario : le DFA n'accepte que {@code "a"} et ne possède aucune
     * transition sortante depuis son état final. Résultat attendu : le puits
     * utilisé par Moore n'est pas exposé dans l'automate final.</p>
     */
    @Test
    void omitsSinkOnlyStateForPartialDfa() {
        Automaton dfa = AutomatonBuilder.literal("a");

        Automaton minimized = DFAMMoore.minimize(dfa, false);

        assertEquals(2, minimized.getStates().size());
        assertEquals(1, minimized.getFinalStates().size());
        assertTrue(minimized.getTransitions().stream().noneMatch(t -> t.isEpsilon()));
        assertTrue(SearchGenerators.accepts(minimized, "a"));
        assertFalse(SearchGenerators.accepts(minimized, ""));
        assertFalse(SearchGenerators.accepts(minimized, "aa"));
    }

    /**
     * Vérifie la conservation du langage sur tous les petits mots ASCII.
     *
     * <p>Scénario : un DFA issu de {@code a|bc*} est minimisé puis comparé au
     * DFA original. Résultat attendu : les deux automates donnent la même
     * réponse pour chaque mot de longueur au plus trois.</p>
     */
    @Test
    void preservesLanguageForSmallAsciiWords() throws Exception {
        Automaton dfa = DFA.convert(NFA.buildNFA(RegexParser.parse("a|bc*")));
        Automaton minimized = DFAMMoore.minimize(dfa, false);

        for (String word : SearchGenerators.words("abc", 3)) {
            assertEquals(
                    SearchGenerators.accepts(dfa, word),
                    SearchGenerators.accepts(minimized, word),
                    word);
        }
    }

    /** Les états non accessibles ne doivent pas survivre à la minimisation. */
    @Test
    void removesUnreachableStates() {
        Automaton dfa = new AutomatonBuilder()
                .state("start", Status.ENTER)
                .state("accept", Status.FINAL)
                .state("unreachable", Status.FINAL)
                .character("start", "accept", 'a')
                .character("unreachable", "unreachable", 'a')
                .build();

        Automaton minimized = DFAMMoore.minimize(dfa);
        assertEquals(2, minimized.getStates().size());
        for (String word : SearchGenerators.words("ab", 3)) {
            assertEquals(SearchGenerators.accepts(dfa, word), SearchGenerators.accepts(minimized, word), word);
        }
    }

    /** Moore conserve les classes symboliques compactes au lieu d'émettre 256 arcs. */
    @Test
    void preservesWildcardClassesAndCompactsTransitions() {
        Automaton dfa = new AutomatonBuilder()
                .state("start", Status.ENTER)
                .state("left", Status.FINAL)
                .state("right", Status.FINAL)
                .character("start", "left", 'a')
                .anyExcept("start", "right", Set.of('a'))
                .build();

        Automaton minimized = DFAMMoore.minimize(dfa);
        assertEquals(2, minimized.getStates().size());
        State start = minimized.getInitialState();
        assertEquals(1, minimized.getOutgoingTransitions(start).size());
        for (String word : SearchGenerators.words("abxÿ", 2)) {
            assertEquals(SearchGenerators.accepts(dfa, word), SearchGenerators.accepts(minimized, word), word);
        }
    }

    /** Les mêmes préconditions de déterminisme que Hopcroft sont désormais vérifiées. */
    @Test
    void rejectsEpsilonAndOverlappingTransitions() {
        Automaton epsilon = new AutomatonBuilder()
                .state("q0", Status.ENTER)
                .state("q1", Status.FINAL)
                .epsilon("q0", "q1")
                .build();
        assertThrows(IllegalArgumentException.class, () -> DFAMMoore.minimize(epsilon));

        Automaton overlap = new AutomatonBuilder()
                .state("q0", Status.ENTER)
                .state("q1", Status.FINAL)
                .state("q2", Status.FINAL)
                .character("q0", "q1", 'a')
                .any("q0", "q2")
                .build();
        assertThrows(IllegalArgumentException.class, () -> DFAMMoore.minimize(overlap));
    }

    /**
     * Vérifie qu'un automate sans état initial est rejeté.
     *
     * <p>Résultat attendu : une {@link NullPointerException} est levée avant
     * toute construction de l'automate minimisé.</p>
     */
    @Test
    void rejectsDfaWithoutInitialState() {
        assertThrows(
                NullPointerException.class,
                () -> DFAMMoore.minimize(new Automaton(), false));
    }
}
