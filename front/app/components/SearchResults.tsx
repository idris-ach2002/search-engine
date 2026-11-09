import type { SearchResponse } from "../types/search";
import { formatNumber } from "../lib/format";
import { EmptyState } from "./EmptyState";
import { Icon } from "./Icon";
import { SearchObservability } from "./SearchObservability";
import { SearchResultCard } from "./SearchResultCard";

interface SearchResultsProps {
  search: SearchResponse;
  loading: boolean;
  onOpenBook: (bookId: number) => void;
  onPageChange: (page: number) => void;
}

/** Results list, algorithm trace, and server-backed pagination controls. */
export function SearchResults({
  search,
  loading,
  onOpenBook,
  onPageChange,
}: SearchResultsProps) {
  return (
    <section className="results-section">
      <div className="section-heading results-heading">
        <div>
          <div className="eyebrow">RÉSULTATS CLASSÉS</div>
          <h2>{formatNumber(search.totalElements)} ouvrages trouvés</h2>
        </div>
        <span className="result-page">
          Page {search.page + 1} / {Math.max(search.totalPages, 1)}
        </span>
      </div>

      <SearchObservability search={search} />

      <div className="result-list">
        {search.content.length === 0 ? (
          <EmptyState
            icon={<Icon name="search" size={22} />}
            title="Aucun ouvrage trouvé"
            description="Essayez un autre terme ou une expression différente."
          />
        ) : (
          search.content.map((book, index) => (
            <SearchResultCard
              key={book.bookId}
              book={book}
              position={search.page * search.size + index + 1}
              ranking={search}
              onOpenBook={onOpenBook}
            />
          ))
        )}
      </div>

      {search.totalPages > 1 && (
        <div className="pagination">
          <span>{formatNumber(search.totalElements)} résultats au total</span>
          <div>
            <button
              disabled={search.page === 0 || loading}
              onClick={() => onPageChange(search.page - 1)}
            >
              Précédent
            </button>
            <button
              disabled={search.page + 1 >= search.totalPages || loading}
              onClick={() => onPageChange(search.page + 1)}
            >
              Suivant <Icon name="arrow" size={15} />
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
