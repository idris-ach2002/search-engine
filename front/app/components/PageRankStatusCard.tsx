import type { PageRankStatus } from "../types/admin";
import { formatDate, formatNumber } from "../lib/format";
import { ErrorNotice } from "./ErrorNotice";
import { Icon } from "./Icon";
import { Metric } from "./Metric";

interface PageRankStatusCardProps {
  status: PageRankStatus | null;
  error?: string;
  loading: boolean;
  rebuilding: boolean;
  onRebuild: () => void;
}

/** Shows PageRank availability and convergence information. */
export function PageRankStatusCard({
  status,
  error,
  loading,
  rebuilding,
  onRebuild,
}: PageRankStatusCardProps) {
  const available = Boolean(status?.available && !status.stale);

  return (
    <article className="dashboard-card">
      <div className="card-title-row">
        <span className="card-icon amber-icon"><Icon name="activity" /></span>
        <div><h3>PageRank</h3><span>Centralité des ouvrages</span></div>
        <span className={`status-pill ${available ? "status-good" : "status-warn"}`}>
          {available ? "DISPONIBLE" : "À RECALCULER"}
        </span>
      </div>
      {error ? (
        <ErrorNotice>{error}</ErrorNotice>
      ) : status ? (
        <>
          <div className="card-stats three-stats">
            <Metric label="SCORES" value={formatNumber(status.storedScores)} />
            <Metric label="ITÉRATIONS" value={formatNumber(status.iterations)} />
            <Metric
              label="CONVERGENCE"
              value={status.converged == null ? "—" : status.converged ? "Oui" : "Non"}
            />
          </div>
          <div className="card-foot">Dernier calcul · {formatDate(status.computedAt)}</div>
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
        {rebuilding ? "Reconstruction…" : "Recalculer PageRank"}
      </button>
    </article>
  );
}
