import type { SearchBook, SearchResponseBase } from "../types/search";
import { formatNumber, formatPercent, formatScore } from "../lib/format";

interface SearchResultCardProps {
  book: SearchBook;
  position: number;
  ranking: Pick<SearchResponseBase, "lexicalWeight" | "pageRankWeight" | "pageRankApplied">;
  onOpenBook: (bookId: number) => void;
}

/** One ranked search result, including its expandable score decomposition. */
export function SearchResultCard({
  book,
  position,
  ranking,
  onOpenBook,
}: SearchResultCardProps) {
  return (
    <article className="result-card">
      <div className="book-cover">
        <span>{String(book.title || "L").slice(0, 1).toUpperCase()}</span>
        <small>GUTENBERG</small>
      </div>
      <button className="result-main" onClick={() => onOpenBook(book.bookId)}>
        <span className="result-rank">
          #{String(position).padStart(2, "0")} · GUTENBERG {book.gutenbergId}
        </span>
        <strong className="result-title">{book.title}</strong>
        <span className="result-author">{book.author || "Auteur inconnu"}</span>
        <span className="result-meta">
          {formatNumber(book.wordCount)} mots <i /> {book.language?.toUpperCase() || "—"} <i />{" "}
          {formatNumber(book.matchedTerms)} termes correspondants
        </span>
      </button>
      <details className="score-details">
        <summary aria-label="Détail du score final">
          <span className="score-label">SCORE FINAL</span>
          <strong>{formatScore(book.finalScore)}</strong>
        </summary>
        <div className="score-popover">
          <div className="score-popover-title">Décomposition du score</div>
          <div className="score-line">
            <span>Lexical <b>{formatPercent(ranking.lexicalWeight)}</b></span>
            <strong>{formatScore(book.normalizedLexicalScore)}</strong>
          </div>
          <div className="score-bar">
            <i style={{ width: `${Math.min(book.normalizedLexicalScore * 100, 100)}%` }} />
          </div>
          <div className="score-line">
            <span>PageRank <b>{formatPercent(ranking.pageRankWeight)}</b></span>
            <strong>{formatScore(book.normalizedPageRankScore)}</strong>
          </div>
          <div className="score-bar pagerank-bar">
            <i style={{ width: `${Math.min(book.normalizedPageRankScore * 100, 100)}%` }} />
          </div>
          {!ranking.pageRankApplied && (
            <small>PageRank indisponible : classement lexical utilisé.</small>
          )}
        </div>
      </details>
    </article>
  );
}
