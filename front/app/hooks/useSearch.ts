import { useRef, useState } from "react";
import { api } from "../api";
import { getErrorMessage, isSingleKeyword } from "../lib/format";
import type { SearchMode, SearchResponse, SearchSuggestions } from "../types/search";

const MAX_REGEX_LENGTH = 256;

/** Owns search input, validation, result loading, and recommendation loading. */
export function useSearch() {
  const [mode, setMode] = useState<SearchMode>("simple");
  const [input, setInput] = useState("");
  const [search, setSearch] = useState<SearchResponse | null>(null);
  const [suggestions, setSuggestions] = useState<SearchSuggestions | null>(null);
  const [searchError, setSearchError] = useState("");
  const [suggestionsError, setSuggestionsError] = useState("");
  const [searchLoading, setSearchLoading] = useState(false);
  const [suggestionsLoading, setSuggestionsLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const requestId = useRef(0);

  const selectMode = (nextMode: SearchMode) => {
    requestId.current += 1;
    setMode(nextMode);
    setSearch(null);
    setSuggestions(null);
    setSearchError("");
    setSuggestionsError("");
    setSearchLoading(false);
    setSuggestionsLoading(false);
    setHasSearched(false);
  };

  const runSearch = async (
    requestedMode: SearchMode,
    requestedInput: string,
    page: number,
  ) => {
    const value = requestedInput.trim();
    if (!value) {
      setSearchError("Saisissez un terme pour lancer la recherche.");
      return;
    }
    if (requestedMode === "simple" && !isSingleKeyword(value)) {
      setSearchError(
        "Le mode Mot-clé accepte un seul mot (lettres, apostrophe ou trait d’union).",
      );
      return;
    }
    if (requestedMode === "advanced" && value.length > MAX_REGEX_LENGTH) {
      setSearchError(
        `L’expression régulière ne peut pas dépasser ${MAX_REGEX_LENGTH} caractères.`,
      );
      return;
    }

    const currentRequestId = ++requestId.current;
    setSearchError("");
    setSuggestionsError("");
    setSearch(null);
    setSuggestions(null);
    setHasSearched(true);
    setSearchLoading(true);
    setSuggestionsLoading(true);

    const [searchResult, suggestionsResult] = await Promise.allSettled([
      api.search(requestedMode, value, page),
      api.searchSuggestions(requestedMode, value),
    ]);

    if (currentRequestId !== requestId.current) return;

    if (searchResult.status === "fulfilled") {
      setSearch(searchResult.value);
    } else {
      setSearchError(getErrorMessage(searchResult.reason));
    }
    if (suggestionsResult.status === "fulfilled") {
      setSuggestions(suggestionsResult.value);
    } else {
      setSuggestionsError(getErrorMessage(suggestionsResult.reason));
    }

    setSearchLoading(false);
    setSuggestionsLoading(false);
  };

  const submitSearch = () => runSearch(mode, input, 0);

  const changePage = (page: number) => {
    if (!search) return;
    const query = "regex" in search ? search.regex : search.query;
    const searchMode = "regex" in search ? "advanced" : "simple";
    return runSearch(searchMode, query, page);
  };

  return {
    mode,
    input,
    search,
    suggestions,
    searchError,
    suggestionsError,
    searchLoading,
    suggestionsLoading,
    hasSearched,
    setInput,
    selectMode,
    submitSearch,
    changePage,
  };
}
