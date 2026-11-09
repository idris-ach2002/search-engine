/** Public book metadata returned by the books endpoints. */
export interface BookDetails {
  id: number;
  gutenbergId: number;
  title: string;
  author: string | null;
  language: string;
  wordCount: number | null;
  status: string;
}

/** A Jaccard neighbor returned for a book detail panel. */
export interface BookNeighbor {
  bookId: number;
  gutenbergId: number;
  title: string;
  author: string | null;
  jaccardScore: number;
  intersectionSize: number;
  unionSize: number;
}

export interface BookSuggestions {
  suggestions: BookNeighbor[];
}
