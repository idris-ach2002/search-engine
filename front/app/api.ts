import type {
  ImportStatus,
  IndexStatus,
  PageRankStatus,
  PageRankTop,
  SimilarityStatus,
} from "./types/admin";
import type { BookDetails, BookSuggestions } from "./types/books";
import type { SearchMode, SearchResponse, SearchSuggestions } from "./types/search";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080")
  .replace(/\/+$/, "");

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

interface ApiErrorPayload {
  error?: string;
  message?: string;
}

function isApiErrorPayload(value: unknown): value is ApiErrorPayload {
  if (typeof value !== "object" || value === null) return false;
  const payload = value as Record<string, unknown>;
  return (
    (payload.error === undefined || typeof payload.error === "string") &&
    (payload.message === undefined || typeof payload.message === "string")
  );
}

/**
 * Centralizes HTTP behavior so endpoint functions stay focused on contracts
 * and URL construction.
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, init);
  } catch {
    throw new Error(
      `Impossible de joindre l’API (${API_BASE_URL}). Vérifiez que le backend est démarré.`,
    );
  }

  const body = await response.text();
  let data: unknown;
  if (body) {
    try {
      data = JSON.parse(body);
    } catch {
      data = body;
    }
  }

  if (!response.ok) {
    const payload = isApiErrorPayload(data) ? data : undefined;
    throw new ApiError(
      payload?.error ??
        payload?.message ??
        `La requête a échoué (${response.status}).`,
      response.status,
    );
  }

  return data as T;
}

function queryString(params: Record<string, string | number>): string {
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    searchParams.set(key, String(value));
  }
  return searchParams.toString();
}

/** Typed client for the documented Spring Boot endpoints. */
export const api = {
  health(): Promise<unknown> {
    return request<unknown>("/api/health");
  },

  search(mode: SearchMode, input: string, page = 0): Promise<SearchResponse> {
    const endpoint = mode === "simple" ? "/api/search" : "/api/search/advanced";
    const queryKey = mode === "simple" ? "q" : "regex";
    return request<SearchResponse>(
      `${endpoint}?${queryString({ [queryKey]: input, page, size: 10 })}`,
    );
  },

  searchSuggestions(mode: SearchMode, input: string): Promise<SearchSuggestions> {
    const endpoint =
      mode === "simple"
        ? "/api/search/suggestions"
        : "/api/search/advanced/suggestions";
    const queryKey = mode === "simple" ? "q" : "regex";
    return request<SearchSuggestions>(
      `${endpoint}?${queryString({ [queryKey]: input, seedCount: 3, limit: 5 })}`,
    );
  },

  book(id: number): Promise<BookDetails> {
    return request<BookDetails>(`/api/books/${id}`);
  },

  bookSuggestions(id: number): Promise<BookSuggestions> {
    return request<BookSuggestions>(`/api/books/${id}/suggestions?limit=6`);
  },

  indexingStatus(): Promise<IndexStatus> {
    return request<IndexStatus>("/api/admin/indexing/status");
  },

  similarityStatus(): Promise<SimilarityStatus> {
    return request<SimilarityStatus>("/api/admin/similarity/status");
  },

  pagerankStatus(): Promise<PageRankStatus> {
    return request<PageRankStatus>("/api/admin/ranking/pagerank/status");
  },

  pagerankTop(): Promise<PageRankTop> {
    return request<PageRankTop>("/api/admin/ranking/pagerank/top?limit=8");
  },

  importStatus(): Promise<ImportStatus> {
    return request<ImportStatus>("/api/admin/gutenberg/import/status");
  },

  rebuildSimilarity(): Promise<unknown> {
    return request<unknown>("/api/admin/similarity/rebuild", { method: "POST" });
  },

  rebuildPagerank(): Promise<unknown> {
    return request<unknown>("/api/admin/ranking/pagerank/rebuild", {
      method: "POST",
    });
  },
};
