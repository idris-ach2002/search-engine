import { useSearch } from "../hooks/useSearch";
import { formatNumber } from "../lib/format";
import { ErrorNotice } from "./ErrorNotice";
import { Icon } from "./Icon";
import { RecommendationsPanel } from "./RecommendationsPanel";
import { SearchBox } from "./SearchBox";
import { SearchResults } from "./SearchResults";

interface SearchPageProps {
  onOpenBook: (bookId: number) => void;
}

/** Search view composition; request and validation behavior lives in useSearch. */
export function SearchPage({ onOpenBook }: SearchPageProps) {
  const searchState = useSearch();
  const {
    mode,
    input,
    search,
    suggestions,
    searchError,
    suggestionsError,
    searchLoading,
    suggestionsLoading,
    hasSearched,
    setInput,
    selectMode,
    submitSearch,
    changePage,
  } = searchState;
  const searchLabel = mode === "simple" ? "RECHERCHE KMP" : "MOTEUR D’AUTOMATES";

  return (
    <div className="page-wrap">
      <section className="search-layout">
        <div className="search-column">
          <div className="eyebrow"><span className="eyebrow-dot" /> {searchLabel}</div>
          <h1>Explorez le corpus.</h1>
          <p className="page-intro">
            Trouvez, comparez et découvrez des ouvrages avec la puissance des algorithmes.
          </p>

          <SearchBox
            mode={mode}
            input={input}
            loading={searchLoading}
            onInputChange={setInput}
            onModeChange={selectMode}
            onSubmit={() => void submitSearch()}
          />

          {searchError && <ErrorNotice>{searchError}</ErrorNotice>}
          {searchLoading && (
            <div className="loading-panel">
              <span className="spinner dark-spinner" /> Analyse du corpus en cours…
            </div>
          )}

          {search && !searchLoading && (
            <SearchResults
              search={search}
              loading={searchLoading}
              onOpenBook={onOpenBook}
              onPageChange={(page) => void changePage(page)}
            />
          )}
        </div>

        <RecommendationsPanel
          suggestions={suggestions}
          loading={suggestionsLoading}
          error={suggestionsError}
          onOpenBook={onOpenBook}
        />
      </section>
      <footer className="page-footer">
        <span>DAAR · MOTEUR DE RECHERCHE DOCUMENTAIRE</span>
        <span>KMP <i /> REGEX <i /> JACCARD <i /> PAGERANK</span>
      </footer>
    </div>
  );
}
