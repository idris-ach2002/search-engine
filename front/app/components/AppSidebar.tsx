import type { ActiveView, ApiAvailability } from "../types/app";
import { Icon } from "./Icon";

interface AppSidebarProps {
  activeView: ActiveView;
  apiAvailability: ApiAvailability;
  onNavigate: (view: ActiveView) => void;
}

/** Primary app navigation and persistent backend status indicator. */
export function AppSidebar({
  activeView,
  apiAvailability,
  onNavigate,
}: AppSidebarProps) {
  const availabilityLabel = {
    checking: "Connexion API…",
    online: "API disponible",
    offline: "API indisponible",
  }[apiAvailability];

  return (
    <aside className="sidebar">
      <a className="brand" href="/" aria-label="DAAR, accueil">
        <span className="brand-mark"><Icon name="book" size={21} /></span>
        <span className="brand-copy">
          <strong>DAAR</strong>
          <small>BOOK INDEX</small>
        </span>
      </a>
      <div className="sidebar-caption">ESPACE DE TRAVAIL</div>
      <nav className="main-nav" aria-label="Navigation principale">
        <button
          className={`nav-item${activeView === "search" ? " active" : ""}`}
          aria-current={activeView === "search" ? "page" : undefined}
          onClick={() => onNavigate("search")}
        >
          <Icon name="search" />
          <span>Recherche</span>
          <span className="nav-shortcut">⌘ K</span>
        </button>
        <button
          className={`nav-item${activeView === "monitor" ? " active" : ""}`}
          aria-current={activeView === "monitor" ? "page" : undefined}
          onClick={() => onNavigate("monitor")}
        >
          <Icon name="grid" />
          <span>Monitoring</span>
        </button>
      </nav>
      <div className="sidebar-bottom">
        <div className={`connection-indicator api-${apiAvailability}`}>
          <i /> {availabilityLabel}
        </div>
        <div className="sidebar-version">PROJET DAAR <span>v1.0</span></div>
      </div>
    </aside>
  );
}
