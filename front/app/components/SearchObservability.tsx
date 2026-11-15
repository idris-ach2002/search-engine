import type { SearchResponse } from "../types/search";
import { formatNumber } from "../lib/format";
import { Metric } from "./Metric";
import { Icon } from "./Icon";

interface SearchObservabilityProps {
  search: SearchResponse;
}

/** Presents measured search costs and, for RegEx, automaton state counts. */
export function SearchObservability({ search }: SearchObservabilityProps) {
  return (
    <div className="observability">
      <div className="observability-heading">
        <Icon name="activity" size={16} />
        <span>TRACE ALGORITHMIQUE</span>
        <span className="trace-live">MESURÉ EN TEMPS RÉEL</span>
      </div>
      <div className="metrics-grid">
        <Metric label="TEMPS D’EXÉCUTION" value={`${search.elapsedMillis} ms`} accent />
        <Metric label="TERMES MATCHÉS" value={formatNumber(search.matchedIndexTerms)} />
        {"regex" in search && (
          <>
            <Metric label="ÉTATS NFA" value={formatNumber(search.nfaStates)} />
            <Metric label="ÉTATS DFA" value={formatNumber(search.dfaStates)} />
            <Metric
              label="HOPCROFT"
              value={formatNumber(search.minimizedDfaStates)}
              note="états minimisés"
            />
          </>
        )}
      </div>
    </div>
  );
}
