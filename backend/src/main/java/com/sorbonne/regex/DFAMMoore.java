package com.sorbonne.regex;

import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;

/**
 * Minimisation d'un DFA par raffinements successifs de Moore.
 *
 * <p>L'automate du projet est partiel et travaille sur un alphabet de 256
 * valeurs. Comme l'implémentation de Hopcroft, Moore indexe d'abord les états
 * par entiers, regroupe les caractères qui ont partout le même rôle symbolique
 * et complète la fonction de transition avec un puits implicite. Les états
 * inaccessibles ne participent pas au raffinement.</p>
 *
 * <p>Le raffinement n'alloue aucune signature objet : une signature est lue
 * directement dans les tableaux de transitions et regroupée dans une table de
 * hachage primitive réutilisée à chaque tour. Pour {@code n} états accessibles
 * et {@code k <= 256} classes de caractères, un tour coûte {@code O(kn)} en
 * moyenne et Moore effectue au plus {@code n - 1} raffinements utiles, soit
 * {@code O(kn^2)} dans le pire cas. L'index occupe {@code O(kN)} mémoire pour
 * {@code N} états fournis plus le puits ; le raffinement ajoute {@code O(n)}.</p>
 */
public final class DFAMMoore {
    private static final int SYMBOL_COUNT = 256;

    private DFAMMoore() {
    }

    /** Minimise sans afficher les partitions intermédiaires. */
    public static Automaton minimize(Automaton dfa) {
        return minimize(dfa, false);
    }

    /**
     * Minimise un automate déterministe par Moore.
     *
     * @param dfa automate déterministe non nul, sans epsilon
     * @param debug affiche les partitions après chaque raffinement
     * @return nouvel automate minimal, accessible et déterministe
     * @throws NullPointerException si l'automate est nul ou sans état initial
     * @throws IllegalArgumentException si une transition est hors alphabet,
     *                                  epsilon, ambiguë ou référence un état absent
     * @throws IllegalStateException si plusieurs états sont initiaux
     */
    public static Automaton minimize(Automaton dfa, boolean debug) {
        IndexedDfa indexed = new IndexedDfa(Objects.requireNonNull(dfa, "Le DFA ne doit pas être nul"));
        Partition partition = new Partition(indexed, debug);
        partition.refine();
        return partition.materialize();
    }

    /** Fonction de transition dense sur des classes de caractères symboliques. */
    private static final class IndexedDfa {
        private final State[] originalStates;
        private final int realStateCount;
        private final int stateCount;
        private final int sink;
        private final int initial;
        private final boolean[] finals;
        /** delta[classe][état] = destination. */
        private final int[][] delta;
        /** Caractères distingués globalement, en ordre croissant. */
        private final char[] significant;
        private final boolean hasOtherClass;
        private final int otherClass;
        private final boolean[] reachable;
        private final int reachableCount;

        private IndexedDfa(Automaton dfa) {
            State start = Objects.requireNonNull(dfa.getInitialState(), "Le DFA doit posséder un état initial");
            originalStates = dfa.getStates().toArray(State[]::new);
            realStateCount = originalStates.length;
            sink = realStateCount;
            stateCount = realStateCount + 1;

            Map<State, Integer> ids = new IdentityHashMap<>(Math.max(16, realStateCount * 2));
            finals = new boolean[stateCount];
            for (int state = 0; state < realStateCount; state++) {
                State original = originalStates[state];
                ids.put(original, state);
                finals[state] = original.getStatus().isFinal();
            }
            Integer startId = ids.get(start);
            if (startId == null) {
                throw new IllegalArgumentException("L'état initial n'appartient pas au DFA");
            }
            initial = startId;

            BitSet significantBits = new BitSet(SYMBOL_COUNT);
            for (Transition transition : dfa.getTransitions()) {
                requireKnownEndpoints(ids, transition);
                switch (transition.getType()) {
                    case EPSILON -> throw new IllegalArgumentException(
                            "Moore exige un automate déterministe sans transition epsilon");
                    case CHARACTER -> {
                        requireByteSymbol(transition.getSymbol());
                        significantBits.set(transition.getSymbol());
                    }
                    case ANY -> {
                        for (char excluded : transition.getExcludedSymbols()) {
                            requireByteSymbol(excluded);
                            significantBits.set(excluded);
                        }
                    }
                }
            }

            int significantCount = significantBits.cardinality();
            hasOtherClass = significantCount < SYMBOL_COUNT;
            int alphabetSize = significantCount + (hasOtherClass ? 1 : 0);
            significant = new char[significantCount];
            int[] classOf = new int[SYMBOL_COUNT];
            Arrays.fill(classOf, -1);
            int position = 0;
            for (int code = significantBits.nextSetBit(0); code >= 0; code = significantBits.nextSetBit(code + 1)) {
                significant[position] = (char) code;
                classOf[code] = position++;
            }
            otherClass = hasOtherClass ? significantCount : -1;

            char[] representatives = new char[alphabetSize];
            System.arraycopy(significant, 0, representatives, 0, significantCount);
            if (hasOtherClass) {
                representatives[otherClass] = (char) significantBits.nextClearBit(0);
            }

            delta = new int[alphabetSize][stateCount];
            for (int[] row : delta) {
                Arrays.fill(row, -1);
            }
            for (Transition transition : dfa.getTransitions()) {
                int source = ids.get(transition.getSource());
                int destination = ids.get(transition.getDestination());
                switch (transition.getType()) {
                    case EPSILON -> throw new AssertionError("transition epsilon déjà rejetée");
                    case CHARACTER -> assign(delta[classOf[transition.getSymbol()]], source, destination);
                    case ANY -> {
                        Set<Character> excluded = transition.getExcludedSymbols();
                        for (int symbol = 0; symbol < alphabetSize; symbol++) {
                            if (!excluded.contains(representatives[symbol])) {
                                assign(delta[symbol], source, destination);
                            }
                        }
                    }
                }
            }

            for (int symbol = 0; symbol < alphabetSize; symbol++) {
                int[] transitions = delta[symbol];
                for (int state = 0; state < realStateCount; state++) {
                    if (transitions[state] < 0) {
                        transitions[state] = sink;
                    }
                }
                transitions[sink] = sink;
            }

            reachable = new boolean[stateCount];
            int[] queue = new int[stateCount];
            int head = 0;
            int tail = 0;
            reachable[initial] = true;
            queue[tail++] = initial;
            while (head < tail) {
                int state = queue[head++];
                for (int symbol = 0; symbol < alphabetSize; symbol++) {
                    int destination = delta[symbol][state];
                    if (!reachable[destination]) {
                        reachable[destination] = true;
                        queue[tail++] = destination;
                    }
                }
            }
            reachableCount = tail;
        }

        private static void requireKnownEndpoints(Map<State, Integer> ids, Transition transition) {
            if (!ids.containsKey(transition.getSource()) || !ids.containsKey(transition.getDestination())) {
                throw new IllegalArgumentException("Une transition référence un état absent du DFA");
            }
        }

        private static void requireByteSymbol(char symbol) {
            if (symbol >= SYMBOL_COUNT) {
                throw new IllegalArgumentException("Symbole hors alphabet 8 bits : " + (int) symbol);
            }
        }

        private static void assign(int[] transitions, int source, int destination) {
            if (transitions[source] >= 0) {
                throw new IllegalArgumentException(
                        "Automate non déterministe : plusieurs transitions acceptent le même caractère");
            }
            transitions[source] = destination;
        }
    }

    /** Partition courante des états accessibles. */
    private static final class Partition {
        private final IndexedDfa input;
        private final boolean debug;
        private int[] blockOf;
        private int[] nextBlockOf;
        private int blockCount;
        /** Table de hachage primitive : case -> état représentant, -1 si vide. */
        private final int[] representatives;

        private Partition(IndexedDfa input, boolean debug) {
            this.input = input;
            this.debug = debug;
            blockOf = new int[input.stateCount];
            nextBlockOf = new int[input.stateCount];
            Arrays.fill(blockOf, -1);
            Arrays.fill(nextBlockOf, -1);

            boolean hasFinal = false;
            boolean hasNonFinal = false;
            for (int state = 0; state < input.stateCount; state++) {
                if (input.reachable[state]) {
                    hasFinal |= input.finals[state];
                    hasNonFinal |= !input.finals[state];
                }
            }
            int nonFinalBlock = hasNonFinal ? 0 : -1;
            int finalBlock = hasFinal ? (hasNonFinal ? 1 : 0) : -1;
            blockCount = (hasFinal ? 1 : 0) + (hasNonFinal ? 1 : 0);
            for (int state = 0; state < input.stateCount; state++) {
                if (input.reachable[state]) {
                    blockOf[state] = input.finals[state] ? finalBlock : nonFinalBlock;
                }
            }

            int capacity = 1;
            int target = Math.max(4, input.reachableCount * 4);
            while (capacity < target) {
                capacity <<= 1;
            }
            representatives = new int[capacity];
            if (debug) {
                printPartition();
            }
        }

        /** Raffine jusqu'à ce qu'aucun bloc ne soit séparé. */
        private void refine() {
            while (true) {
                Arrays.fill(representatives, -1);
                Arrays.fill(nextBlockOf, -1);
                int newBlockCount = 0;
                int mask = representatives.length - 1;

                for (int state = 0; state < input.stateCount; state++) {
                    if (!input.reachable[state]) {
                        continue;
                    }
                    int slot = signatureHash(state) & mask;
                    while (true) {
                        int representative = representatives[slot];
                        if (representative < 0) {
                            representatives[slot] = state;
                            nextBlockOf[state] = newBlockCount++;
                            break;
                        }
                        if (sameSignature(state, representative)) {
                            nextBlockOf[state] = nextBlockOf[representative];
                            break;
                        }
                        slot = (slot + 1) & mask;
                    }
                }

                int[] swap = blockOf;
                blockOf = nextBlockOf;
                nextBlockOf = swap;
                if (debug) {
                    printPartition();
                }
                if (newBlockCount == blockCount) {
                    blockCount = newBlockCount;
                    return;
                }
                blockCount = newBlockCount;
            }
        }

        /** Hash de la partition source et des classes de destination. */
        private int signatureHash(int state) {
            int hash = 0x811c9dc5 ^ blockOf[state];
            for (int symbol = 0; symbol < input.delta.length; symbol++) {
                hash ^= blockOf[input.delta[symbol][state]] + 0x9e3779b9;
                hash *= 0x01000193;
            }
            hash ^= hash >>> 16;
            return hash;
        }

        /** Compare deux signatures sans construire de tableau temporaire. */
        private boolean sameSignature(int left, int right) {
            if (blockOf[left] != blockOf[right]) {
                return false;
            }
            for (int symbol = 0; symbol < input.delta.length; symbol++) {
                if (blockOf[input.delta[symbol][left]] != blockOf[input.delta[symbol][right]]) {
                    return false;
                }
            }
            return true;
        }

        /** Reconstruit un DFA partiel compact à partir de la partition stable. */
        private Automaton materialize() {
            boolean[] hasRealState = new boolean[blockCount];
            int[] representative = new int[blockCount];
            StringBuilder[] labels = new StringBuilder[blockCount];
            Arrays.fill(representative, -1);
            for (int state = 0; state < input.realStateCount; state++) {
                if (!input.reachable[state]) {
                    continue;
                }
                int block = blockOf[state];
                hasRealState[block] = true;
                if (representative[block] < 0) {
                    representative[block] = state;
                    labels[block] = new StringBuilder("[");
                } else {
                    labels[block].append(',');
                }
                labels[block].append(input.originalStates[state].getLabel());
            }

            int initialBlock = blockOf[input.initial];

            Automaton result = new Automaton();
            State[] outputStates = new State[blockCount];
            for (int block = 0; block < blockCount; block++) {
                if (!hasRealState[block]) {
                    continue;
                }
                int state = representative[block];
                boolean initial = block == initialBlock;
                boolean accepting = input.finals[state];
                State output = new State(labels[block].append(']').toString(), status(initial, accepting));
                outputStates[block] = output;
                result.addState(output);
            }

            int significantCount = input.significant.length;
            for (int block = 0; block < blockCount; block++) {
                State source = outputStates[block];
                if (source == null) {
                    continue;
                }
                int state = representative[block];
                int baselineBlock = chooseBaselineBlock(state);
                State baseline = baselineBlock >= 0 ? outputStates[baselineBlock] : null;

                if (baseline != null) {
                    java.util.LinkedHashSet<Character> exclusions = new java.util.LinkedHashSet<>();
                    for (int symbol = 0; symbol < significantCount; symbol++) {
                        if (blockOf[input.delta[symbol][state]] != baselineBlock) {
                            exclusions.add(input.significant[symbol]);
                        }
                    }
                    result.add(Transition.anyExcept(source, baseline, exclusions));
                }

                for (int symbol = 0; symbol < significantCount; symbol++) {
                    int destinationBlock = blockOf[input.delta[symbol][state]];
                    if (destinationBlock == baselineBlock) {
                        continue;
                    }
                    State destination = outputStates[destinationBlock];
                    if (destination != null) {
                        result.add(new Transition(source, destination, input.significant[symbol]));
                    }
                }
            }
            return result;
        }

        /** Choisit une destination de base afin de minimiser les arcs littéraux. */
        private int chooseBaselineBlock(int state) {
            if (input.hasOtherClass) {
                return blockOf[input.delta[input.otherClass][state]];
            }
            int[] frequencies = new int[blockCount];
            int bestBlock = -1;
            int bestCount = -1;
            for (int symbol = 0; symbol < input.delta.length; symbol++) {
                int block = blockOf[input.delta[symbol][state]];
                int count = ++frequencies[block];
                if (count > bestCount) {
                    bestCount = count;
                    bestBlock = block;
                }
            }
            return bestBlock;
        }

        private void printPartition() {
            System.out.println("Partition :");
            for (int block = 0; block < blockCount; block++) {
                StringBuilder line = new StringBuilder("  B").append(block).append(" = {");
                boolean first = true;
                for (int state = 0; state < input.stateCount; state++) {
                    if (input.reachable[state] && blockOf[state] == block) {
                        if (!first) {
                            line.append(", ");
                        }
                        line.append(state == input.sink ? "__MOORE_SINK__" : input.originalStates[state].getLabel());
                        first = false;
                    }
                }
                System.out.println(line.append('}'));
            }
            System.out.println();
        }

        private static Status status(boolean initial, boolean accepting) {
            if (initial && accepting) {
                return Status.ENTER_FINAL;
            }
            if (initial) {
                return Status.ENTER;
            }
            return accepting ? Status.FINAL : Status.INTERMEDIATE;
        }
    }
}
