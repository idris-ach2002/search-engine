import { useCallback, useState } from "react";
import { api } from "../api";
import { getErrorMessage } from "../lib/format";
import type {
  AdminAction,
  AdminErrors,
  ImportStatus,
  IndexStatus,
  PageRankStatus,
  RankedBook,
  SimilarityStatus,
} from "../types/admin";

export function useAdminDashboard() {
  const [indexStatus, setIndexStatus] = useState<IndexStatus | null>(null);
  const [similarityStatus, setSimilarityStatus] = useState<SimilarityStatus | null>(null);
  const [pagerankStatus, setPagerankStatus] = useState<PageRankStatus | null>(null);
  const [importStatus, setImportStatus] = useState<ImportStatus | null>(null);
  const [topBooks, setTopBooks] = useState<RankedBook[]>([]);
  const [errors, setErrors] = useState<AdminErrors>({});
  const [loading, setLoading] = useState(false);
  const [action, setAction] = useState<AdminAction | null>(null);
  const [actionError, setActionError] = useState("");

  const refresh = useCallback(async () => {
    setLoading(true);
    setErrors({});
    const [index, similarity, pagerank, imported, top] = await Promise.allSettled([
      api.indexingStatus(),
      api.similarityStatus(),
      api.pagerankStatus(),
      api.importStatus(),
      api.pagerankTop(),
    ]);

    const nextErrors: AdminErrors = {};
    if (index.status === "fulfilled") setIndexStatus(index.value);
    else nextErrors.indexing = getErrorMessage(index.reason);
    if (similarity.status === "fulfilled") setSimilarityStatus(similarity.value);
    else nextErrors.similarity = getErrorMessage(similarity.reason);
    if (pagerank.status === "fulfilled") setPagerankStatus(pagerank.value);
    else nextErrors.pagerank = getErrorMessage(pagerank.reason);
    if (imported.status === "fulfilled") setImportStatus(imported.value);
    else nextErrors.import = getErrorMessage(imported.reason);
    if (top.status === "fulfilled") setTopBooks(top.value.books);
    else nextErrors.top = getErrorMessage(top.reason);

    setErrors(nextErrors);
    setLoading(false);
  }, []);

  const syncCatalog = async () => {
    setAction("sync");
    setActionError("");
    try {
      await api.syncCatalog();
      await refresh();
    } catch (error) {
      setActionError(getErrorMessage(error));
    } finally {
      setAction(null);
    }
  };

  const startIndexing = async () => {
    setAction("indexing");
    setActionError("");
    try {
      await api.startIndexing();
      await refresh();
    } catch (error) {
      setActionError(getErrorMessage(error));
    } finally {
      setAction(null);
    }
  };

  const rebuild = async (target: "similarity" | "pagerank") => {
    setAction(target);
    setActionError("");
    try {
      if (target === "similarity") await api.rebuildSimilarity();
      else await api.rebuildPagerank();
      await refresh();
    } catch (error) {
      setActionError(getErrorMessage(error));
    } finally {
      setAction(null);
    }
  };

  return {
    indexStatus,
    similarityStatus,
    pagerankStatus,
    importStatus,
    topBooks,
    errors,
    loading,
    action,
    actionError,
    refresh,
    syncCatalog,
    startIndexing,
    rebuild,
  };
}