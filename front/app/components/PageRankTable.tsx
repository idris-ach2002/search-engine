import type { RankedBook } from "../types/admin";
import { formatScore } from "../lib/format";
import { ErrorNotice } from "./ErrorNotice";

interface PageRankTableProps {
  books: RankedBook[];
  error?: string;
  loading: boolean;
  onOpenBook: (bookId: number) => void;
}

/** Renders the global PageRank leaderboard with links to book details. */
export function PageRankTable({
  books,
  error,
  loading,
  onOpenBook,
}: PageRankTableProps) {
  if (error) return <ErrorNotice>{error}</ErrorNotice>;
  if (books.length === 0) {
    return (
      <div className="empty-table">
        {loading ? "Chargement du classement…" : "Aucun score PageRank disponible."}
      </div>
    );
  }

  return (
    <div className="ranking-table">
      <div className="ranking-row ranking-header">
        <span>RANG</span>
        <span>OUVRAGE</span>
        <span>AUTEUR</span>
        <span>GUTENBERG</span>
        <span>SCORE PAGERANK</span>
      </div>
      {books.map((book, index) => (
        <button
          className="ranking-row"
          key={book.bookId}
          onClick={() => onOpenBook(book.bookId)}
        >
          <span className="ranking-number">{String(index + 1).padStart(2, "0")}</span>
          <strong>{book.title}</strong>
          <span>{book.author || "Auteur inconnu"}</span>
          <span className="mono">{book.gutenbergId}</span>
          <span className="ranking-score">{formatScore(book.pageRankScore)}</span>
        </button>
      ))}
    </div>
  );
}
