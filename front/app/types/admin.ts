/** Operational status of the inverted index. */
export interface IndexStatus {
  readyBooks: number;
  indexedBooks: number;
  unindexedBooks: number;
  terms: number;
  bookTerms: number;
}

/** Operational status of the Jaccard similarity graph. */
export interface SimilarityStatus {
  readyBooks: number;
  indexedBooks: number;
  storedEdges: number;
  lastBuiltAt: string | null;
  minimumSimilarity: number;
  maxNeighborsPerBook: number;
}

/** Operational status of the PageRank calculation. */
export interface PageRankStatus {
  readyBooks: number;
  similarityEdges: number;
  storedScores: number;
  available: boolean;
  stale: boolean;
  computedAt: string | null;
  iterations: number | null;
  converged: boolean | null;
  lexicalWeight: number;
  pageRankWeight: number;
}

/** Progress snapshot returned by the Gutenberg import service. */
export interface ImportStatus {
  readyBooks: number;
  rejectedTooShort: number;
  failed: number;
  nextLinkIndex: number;
  minimumWordCount: number;
}

/** One entry in the global PageRank leaderboard. */
export interface RankedBook {
  bookId: number;
  gutenbergId: number;
  title: string;
  author: string | null;
  pageRankScore: number;
}

export interface PageRankTop {
  books: RankedBook[];
}

export type AdminSection = "indexing" | "similarity" | "pagerank" | "import" | "top";
export type AdminErrors = Partial<Record<AdminSection, string>>;
export type RebuildAction = "similarity" | "pagerank";
