import type { SimilarityStatus } from "../types/admin";
import { formatDate, formatNumber, formatScore } from "../lib/format";
import { ErrorNotice } from "./ErrorNotice";
import { Icon } from "./Icon";
import { Metric } from "./Metric";

interface SimilarityStatusCardProps {
  status: SimilarityStatus | null;
  error?: string;
  loading: boolean;
  rebuilding: boolean;
  onRebuild: () => void;
}

/** Shows graph freshness and exposes its explicit rebuild action. */
export function SimilarityStatusCard({
  status,
  error,
  loading,
  rebuilding,
  onRebuild,
}: SimilarityStatusCardProps) {
  const built = Boolean(status?.storedEdges);

  return (
    <article className="dashboard-card">
      <div className="card-title-row">
        <span className="card-icon violet-icon"><Icon name="activity" /></span>
        <div><h3>Graphe Jaccard</h3><span>Similarité entre les livres</span></div>
        <span className={`status-pill ${built ? "status-good" : "status-warn"}`}>
          {built ? "CALCULÉ" : "À CALCULER"}
        </span>
      </div>
      {error ? (
        <ErrorNotice>{error}</ErrorNotice>
      ) : status ? (
        <>
          <div className="card-stats three-stats">
            <Metric label="LIVRES INDEXÉS" value={formatNumber(status.indexedBooks)} />
            <Metric label="ARÊTES" value={formatNumber(status.storedEdges)} />
            <Metric label="SEUIL JACCARD" value={formatScore(status.minimumSimilarity)} />
          </div>
          <div className="card-foot">Dernier calcul · {formatDate(status.lastBuiltAt)}</div>
        </>
      ) : (
        <div className="card-placeholder">
          {loading ? "Chargement…" : "Données indisponibles"}
        </div>
      )}
      <button
        className="card-action"
        onClick={onRebuild}
        disabled={rebuilding || loading}
      >
        <Icon name="refresh" size={15} />
        {rebuilding ? "Reconstruction…" : "Reconstruire le graphe"}
      </button>
    </article>
  );
}
