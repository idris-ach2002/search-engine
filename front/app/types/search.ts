/** Search modes supported by the backend API. */
export type SearchMode = "simple" | "advanced";

/** A book returned by either search endpoint. */
export interface SearchBook {
  bookId: number;
  gutenbergId: number;
  title: string;
  author: string | null;
  language: string;
  wordCount: number | null;
  lexicalScore: number;
  matchedTerms: number;
  normalizedLexicalScore: number;
  pageRankScore: number;
  normalizedPageRankScore: number;
  finalScore: number;
}

/** Fields shared by simple and advanced search responses. */
export interface SearchResponseBase {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  matchedIndexTerms: number;
  pageRankApplied: boolean;
  lexicalWeight: number;
  pageRankWeight: number;
  elapsedMillis: number;
  content: SearchBook[];
}

/** Response returned by `GET /api/search`. */
export interface SimpleSearchResponse extends SearchResponseBase {
  query: string;
}

/** Response returned by `GET /api/search/advanced`. */
export interface AdvancedSearchResponse extends SearchResponseBase {
  regex: string;
  nfaStates: number;
  dfaStates: number;
  minimizedDfaStates: number;
}

export type SearchResponse = SimpleSearchResponse | AdvancedSearchResponse;

/** Explainable recommendations produced from a search result's seed books. */
export interface SearchSuggestions {
  elapsedMillis: number;
  usedSeedCount: number;
  seeds: SearchSeed[];
  suggestions: SearchRecommendation[];
}

export interface SearchSeed {
  bookId: number;
  title: string;
  author: string | null;
  finalScore: number;
}

export interface SearchRecommendation {
  bookId: number;
  title: string;
  author: string | null;
  recommendationScore: number;
  bestJaccardScore: number;
  supportingSeeds: number;
  reasons: RecommendationReason[];
}

export interface RecommendationReason {
  seedBookId?: number;
  seedTitle: string;
  seedWeight?: number;
  jaccardScore: number;
  contribution?: number;
}
