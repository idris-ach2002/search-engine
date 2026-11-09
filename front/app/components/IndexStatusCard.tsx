import type { IndexStatus } from "../types/admin";
import { formatNumber } from "../lib/format";
import { ErrorNotice } from "./ErrorNotice";
import { Icon } from "./Icon";
import { Metric } from "./Metric";

interface IndexStatusCardProps {
  status: IndexStatus | null;
  error?: string;
  loading: boolean;
}

/** Displays corpus coverage and vocabulary size for the inverted index. */
export function IndexStatusCard({ status, error, loading }: IndexStatusCardProps) {
  const upToDate = status != null && status.unindexedBooks === 0;

  return (
    <article className="dashboard-card">
      <div className="card-title-row">
        <span className="card-icon"><Icon name="grid" /></span>
        <div><h3>Index inversé</h3><span>Indexation lexicale</span></div>
        <span className={`status-pill ${upToDate ? "status-good" : "status-warn"}`}>
          {status ? (upToDate ? "À JOUR" : "INCOMPLET") : "—"}
        </span>
      </div>
      {error ? (
        <ErrorNotice>{error}</ErrorNotice>
      ) : status ? (
        <>
          <div className="card-stats">
            <Metric
              label="LIVRES INDEXÉS"
              value={`${formatNumber(status.indexedBooks)} / ${formatNumber(status.readyBooks)}`}
            />
            <Metric label="TERMES UNIQUES" value={formatNumber(status.terms)} />
          </div>
          <div className="card-foot">
            {formatNumber(status.bookTerms)} postings <span>·</span>{" "}
            {formatNumber(status.unindexedBooks)} à indexer
          </div>
        </>
      ) : (
        <div className="card-placeholder">
          {loading ? "Chargement…" : "Données indisponibles"}
        </div>
      )}
    </article>
  );
}
