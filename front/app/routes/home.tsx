import { useState } from "react";
import { AppSidebar } from "../components/AppSidebar";
import { BookDetailsDrawer } from "../components/BookDetailsDrawer";
import { MonitoringDashboard } from "../components/MonitoringDashboard";
import { SearchPage } from "../components/SearchPage";
import { TopBar } from "../components/TopBar";
import { useApiHealth } from "../hooks/useApiHealth";
import type { ActiveView } from "../types/app";

/** Route metadata for the document search application. */
export function meta() {
  return [
    { title: "DAAR — Recherche de livres" },
    {
      name: "description",
      content: "Moteur de recherche documentaire — KMP, automates et graphe Jaccard.",
    },
  ];
}

/** Root view switcher; page-level concerns are delegated to focused components. */
export default function Home() {
  const [activeView, setActiveView] = useState<ActiveView>("search");
  const [selectedBookId, setSelectedBookId] = useState<number | null>(null);
  const apiAvailability = useApiHealth();

  return (
    <div className="app-shell">
      <AppSidebar
        activeView={activeView}
        apiAvailability={apiAvailability}
        onNavigate={setActiveView}
      />
      <main className="main-content">
        <TopBar activeView={activeView} apiAvailability={apiAvailability} />
        {activeView === "search" ? (
          <SearchPage onOpenBook={setSelectedBookId} />
        ) : (
          <MonitoringDashboard onOpenBook={setSelectedBookId} />
        )}
      </main>
      <BookDetailsDrawer
        bookId={selectedBookId}
        onClose={() => setSelectedBookId(null)}
        onOpenBook={setSelectedBookId}
      />
    </div>
  );
}
