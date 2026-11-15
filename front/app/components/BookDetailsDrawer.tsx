import { useBookDetails } from "../hooks/useBookDetails";
import { formatNumber, formatScore } from "../lib/format";
import { ErrorNotice } from "./ErrorNotice";
import { Icon } from "./Icon";
import { Metric } from "./Metric";

interface BookDetailsDrawerProps {
  bookId: number | null;
  onClose: () => void;
  onOpenBook: (bookId: number) => void;
}

/** Accessible side drawer for book metadata and its direct graph neighbors. */
export function BookDetailsDrawer({
  bookId,
  onClose,
  onOpenBook,
}: BookDetailsDrawerProps) {
  const { book, suggestions, bookError, suggestionsError, loading } =
    useBookDetails(bookId);

  if (bookId == null) return null;

  return (
    <div className="drawer-layer">
      <button
        className="drawer-backdrop"
        aria-label="Fermer la fiche"
        onClick={onClose}
      />
      <aside
        className="book-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="drawer-title"
      >
        <div className="drawer-topline">
          <span className="eyebrow">FICHE OUVRAGE</span>
          <button className="icon-button" aria-label="Fermer" onClick={onClose}>
            <Icon name="close" />
          </button>
        </div>
        {loading && (
          <div className="drawer-loading">
            <span className="spinner dark-spinner" /> Chargement de l’ouvrage…
          </div>
        )}
        {bookError && <ErrorNotice>{bookError}</ErrorNotice>}

        {book && (
          <>
            <div className="drawer-book-hero">
              <div className="drawer-cover">
                <Icon name="book" size={32} />
                <span>GUTENBERG</span>
              </div>
              <div className="drawer-book-meta">
                <span className="result-rank">GUTENBERG {book.gutenbergId}</span>
                <h2 id="drawer-title">{book.title}</h2>
                <p>{book.author || "Auteur inconnu"}</p>
                <span className="status-pill status-good">{book.status}</span>
              </div>
            </div>

            <div className="drawer-metadata">
              <Metric label="IDENTIFIANT" value={`#${book.id}`} />
              <Metric label="MOTS" value={formatNumber(book.wordCount)} />
              <Metric label="LANGUE" value={book.language?.toUpperCase() || "—"} />
            </div>

            <section className="drawer-neighbors">
              <div className="eyebrow">VOISINS DU GRAPHE JACCARD</div>
              <h3>Dans le même univers</h3>
              {suggestionsError && <ErrorNotice>{suggestionsError}</ErrorNotice>}
              {!suggestions && !suggestionsError && (
                <div className="drawer-loading">Calcul des proximités…</div>
              )}
              {suggestions?.suggestions.length === 0 && (
                <div className="side-empty">Aucun voisin trouvé dans le graphe.</div>
              )}
              {suggestions?.suggestions.map((neighbor) => (
                <button
                  className="neighbor-card"
                  key={neighbor.bookId}
                  onClick={() => onOpenBook(neighbor.bookId)}
                >
                  <span className="neighbor-cover">
                    {neighbor.title.slice(0, 1).toUpperCase()}
                  </span>
                  <span className="neighbor-info">
                    <strong>{neighbor.title}</strong>
                    <small>{neighbor.author || "Auteur inconnu"}</small>
                    <span>
                      Intersection {formatNumber(neighbor.intersectionSize)} <b>·</b>{" "}
                      Union {formatNumber(neighbor.unionSize)}
                    </span>
                  </span>
                  <span className="jaccard-score">
                    {formatScore(neighbor.jaccardScore)}
                    <small>JACCARD</small>
                  </span>
                </button>
              ))}
            </section>
          </>
        )}
      </aside>
    </div>
  );
}
