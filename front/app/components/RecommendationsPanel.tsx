import type { SearchSuggestions } from "../types/search";
import { formatScore } from "../lib/format";
import { ErrorNotice } from "./ErrorNotice";
import { Icon } from "./Icon";

interface RecommendationsPanelProps {
  suggestions: SearchSuggestions | null;
  loading: boolean;
  error: string;
  onOpenBook: (bookId: number) => void;
}

/** Search-seed and Jaccard recommendation sidebar. */
export function RecommendationsPanel({
  suggestions,
  loading,
  error,
  onOpenBook,
}: RecommendationsPanelProps) {
  return (
    <aside className="discovery-column">
      <div className="discovery-header">
        <div>
          <span className="eyebrow">GRAPHE JACCARD</span>
          <h2><Icon name="spark" size={18} /> Découvrir aussi</h2>
        </div>
        <span className="discovery-count">{suggestions?.suggestions.length ?? "—"}</span>
      </div>
      <p className="discovery-copy">
        Des ouvrages proches de vos résultats, révélés par leurs termes partagés.
      </p>
      {loading && (
        <div className="suggestion-loading">
          <span className="spinner dark-spinner" /> Recherche de proximités…
        </div>
      )}
      {error && <ErrorNotice>{error}</ErrorNotice>}

      {!loading && suggestions && (
        <>
          {suggestions.seeds.length > 0 && (
            <div className="seed-list">
              <div className="mini-label">
                GRAINES DE RECHERCHE <span>{suggestions.usedSeedCount}</span>
              </div>
              {suggestions.seeds.map((seed) => (
                <button
                  className="seed-item"
                  key={seed.bookId}
                  onClick={() => onOpenBook(seed.bookId)}
                >
                  <span className="seed-marker" />
                  <span>
                    <strong>{seed.title}</strong>
                    <small>{seed.author || "Auteur inconnu"}</small>
                  </span>
                </button>
              ))}
            </div>
          )}

          {suggestions.suggestions.length > 0 ? (
            <div className="recommendation-list">
              {suggestions.suggestions.map((suggestion, index) => (
                <button
                  className="recommendation-card"
                  key={suggestion.bookId}
                  onClick={() => onOpenBook(suggestion.bookId)}
                >
                  <span className="recommendation-number">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <span className="recommendation-body">
                    <strong>{suggestion.title}</strong>
                    <small>{suggestion.author || "Auteur inconnu"}</small>
                    <span className="recommendation-reason">
                      <i /> Jaccard {formatScore(suggestion.bestJaccardScore)} <b>·</b>{" "}
                      {suggestion.supportingSeeds} graine
                      {suggestion.supportingSeeds > 1 ? "s" : ""}
                    </span>
                  </span>
                  <Icon name="arrow" size={15} />
                </button>
              ))}
            </div>
          ) : (
            <div className="side-empty">
              {suggestions.seeds.length
                ? "Aucune suggestion pour ces graines."
                : "Aucune graine disponible pour cette recherche."}
            </div>
          )}

          <div className="recommendation-footnote">
            Calculé en {suggestions.elapsedMillis} ms · {suggestions.usedSeedCount} graines analysées
          </div>
        </>
      )}

      {!loading && !suggestions && !error && (
        <div className="side-empty">
          Lancez une recherche pour explorer les voisinages du graphe.
        </div>
      )}
    </aside>
  );
}
