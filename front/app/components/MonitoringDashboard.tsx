import { useEffect } from "react";
import { useAdminDashboard } from "../hooks/useAdminDashboard";
import { ErrorNotice } from "./ErrorNotice";
import { Icon } from "./Icon";
import { CorpusStatusCard } from "./CorpusStatusCard";
import { IndexStatusCard } from "./IndexStatusCard";
import { PageRankStatusCard } from "./PageRankStatusCard";
import { PageRankTable } from "./PageRankTable";
import { SimilarityStatusCard } from "./SimilarityStatusCard";

interface MonitoringDashboardProps {
  onOpenBook: (bookId: number) => void;
}

/** Monitoring view composition for import, index, graph, and ranking status. */
export function MonitoringDashboard({ onOpenBook }: MonitoringDashboardProps) {
  const dashboard = useAdminDashboard();

  useEffect(() => {
    void dashboard.refresh();
  }, [dashboard.refresh]);

  return (
    <div className="page-wrap dashboard-wrap">
      <div className="dashboard-heading">
        <div>
          <div className="eyebrow"><span className="eyebrow-dot" /> CENTRE DE CONTRÔLE</div>
          <h1>État du système.</h1>
          <p className="page-intro">
            Vue opérationnelle du corpus, de l’index et des algorithmes de classement.
          </p>
        </div>
        <button
          className="refresh-button"
          onClick={() => void dashboard.refresh()}
          disabled={dashboard.loading}
        >
          <Icon name="refresh" size={16} />
          {dashboard.loading ? "Actualisation…" : "Actualiser"}
        </button>
      </div>
      {dashboard.actionError && <ErrorNotice>{dashboard.actionError}</ErrorNotice>}

      <section className="dashboard-section">
        <div className="section-heading">
          <div>
            <div className="eyebrow">CORPUS & INDEX</div>
            <h2>Fondations du moteur</h2>
          </div>
          <span className="section-chip"><i /> ÉTAT EN DIRECT</span>
        </div>
        <div className="dashboard-grid">
          <CorpusStatusCard
            status={dashboard.importStatus}
            error={dashboard.errors.import}
            loading={dashboard.loading}
          />
          <IndexStatusCard
            status={dashboard.indexStatus}
            error={dashboard.errors.indexing}
            loading={dashboard.loading}
          />
        </div>
      </section>

      <section className="dashboard-section">
        <div className="section-heading">
          <div>
            <div className="eyebrow">ALGORITHMES DE GRAPHE</div>
            <h2>Similarité & classement</h2>
          </div>
        </div>
        <div className="dashboard-grid">
          <SimilarityStatusCard
            status={dashboard.similarityStatus}
            error={dashboard.errors.similarity}
            loading={dashboard.loading}
            rebuilding={dashboard.action === "similarity"}
            onRebuild={() => void dashboard.rebuild("similarity")}
          />
          <PageRankStatusCard
            status={dashboard.pagerankStatus}
            error={dashboard.errors.pagerank}
            loading={dashboard.loading}
            rebuilding={dashboard.action === "pagerank"}
            onRebuild={() => void dashboard.rebuild("pagerank")}
          />
        </div>
      </section>

      <section className="dashboard-section top-ranking-section">
        <div className="section-heading">
          <div>
            <div className="eyebrow">CLASSEMENT GLOBAL</div>
            <h2>Top PageRank</h2>
          </div>
          <span className="section-chip">TOP 8</span>
        </div>
        <PageRankTable
          books={dashboard.topBooks}
          error={dashboard.errors.top}
          loading={dashboard.loading}
          onOpenBook={onOpenBook}
        />
      </section>

      <footer className="page-footer">
        <span>DAAR · MONITORING DU CORPUS</span>
        <span>INDEX <i /> JACCARD <i /> PAGERANK</span>
      </footer>
    </div>
  );
}
