import type { ActiveView, ApiAvailability } from "../types/app";

interface TopBarProps {
  activeView: ActiveView;
  apiAvailability: ApiAvailability;
}

/** Shows the current destination and backend availability in the page header. */
export function TopBar({ activeView, apiAvailability }: TopBarProps) {
  const pageName = activeView === "search" ? "Recherche" : "Monitoring";
  const availabilityLabel = {
    checking: "Connexion API…",
    online: "API disponible",
    offline: "API indisponible",
  }[apiAvailability];

  return (
    <header className="topbar">
      <div className="breadcrumb">
        <span>DAAR</span>
        <span className="breadcrumb-separator">/</span>
        <strong>{pageName}</strong>
      </div>
      <div className="topbar-right">
        <span className={`environment-tag api-${apiAvailability}`}>
          <i /> {availabilityLabel}
        </span>
      </div>
    </header>
  );
}
