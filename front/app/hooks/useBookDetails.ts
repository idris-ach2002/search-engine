import { useEffect, useState } from "react";
import { api } from "../api";
import { getErrorMessage } from "../lib/format";
import type { BookDetails, BookSuggestions } from "../types/books";

/** Loads a book and its Jaccard neighbors whenever the selected id changes. */
export function useBookDetails(bookId: number | null) {
  const [book, setBook] = useState<BookDetails | null>(null);
  const [suggestions, setSuggestions] = useState<BookSuggestions | null>(null);
  const [bookError, setBookError] = useState("");
  const [suggestionsError, setSuggestionsError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (bookId == null) return;

    let active = true;
    setBook(null);
    setSuggestions(null);
    setBookError("");
    setSuggestionsError("");
    setLoading(true);

    Promise.allSettled([api.book(bookId), api.bookSuggestions(bookId)]).then(
      ([bookResult, suggestionsResult]) => {
        if (!active) return;

        if (bookResult.status === "fulfilled") setBook(bookResult.value);
        else setBookError(getErrorMessage(bookResult.reason));

        if (suggestionsResult.status === "fulfilled") {
          setSuggestions(suggestionsResult.value);
        } else {
          setSuggestionsError(getErrorMessage(suggestionsResult.reason));
        }
        setLoading(false);
      },
    );

    return () => {
      active = false;
    };
  }, [bookId]);

  return { book, suggestions, bookError, suggestionsError, loading };
}
