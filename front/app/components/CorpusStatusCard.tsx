import type { ImportStatus } from "../types/admin";
import { formatNumber, formatPercent } from "../lib/format";
import { ErrorNotice } from "./ErrorNotice";
import { Icon } from "./Icon";

interface CorpusStatusCardProps {
  status: ImportStatus | null;
  error?: string;
  loading: boolean;
}

/** Gutenberg import totals and progress against the documented target corpus. */
export function CorpusStatusCard({ status, error, loading }: CorpusStatusCardProps) {
  const targetBooks = 1664;
  const progress = status ? Math.min(status.readyBooks / targetBooks, 1) : 0;

  return (
    <article className="dashboard-card corpus-card">
      <div className="card-title-row">
        <span className="card-icon"><Icon name="book" /></span>
        <div><h3>Corpus Gutenberg</h3><span>Import des ouvrages</span></div>
        <span className="status-pill status-neutral">SOURCE</span>
      </div>
      {error ? (
        <ErrorNotice>{error}</ErrorNotice>
      ) : status ? (
        <>
          <div className="big-stat">
            {formatNumber(status.readyBooks)}<span> / {formatNumber(targetBooks)}</span>
          </div>
          <div className="progress-track">
            <i style={{ width: `${progress * 100}%` }} />
          </div>
          <div className="progress-caption">
            <span>{formatPercent(progress)} du corpus cible</span>
            <span>{formatNumber(status.failed)} échecs</span>
          </div>
          <div className="card-foot">
            Seuil minimum · {formatNumber(status.minimumWordCount)} mots <span>·</span>{" "}
            {formatNumber(status.rejectedTooShort)} trop courts
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
