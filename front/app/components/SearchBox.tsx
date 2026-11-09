import type { FormEvent } from "react";
import type { SearchMode } from "../types/search";
import { Icon } from "./Icon";

interface SearchBoxProps {
  mode: SearchMode;
  input: string;
  loading: boolean;
  onInputChange: (value: string) => void;
  onModeChange: (mode: SearchMode) => void;
  onSubmit: () => void;
}

/** Search mode selector and input form, independent of request state logic. */
export function SearchBox({
  mode,
  input,
  loading,
  onInputChange,
  onModeChange,
  onSubmit,
}: SearchBoxProps) {
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSubmit();
  };

  return (
    <form className="search-box" onSubmit={handleSubmit}>
      <div className="mode-switch" role="tablist" aria-label="Mode de recherche">
        <button
          type="button"
          role="tab"
          aria-selected={mode === "simple"}
          className={mode === "simple" ? "selected" : ""}
          onClick={() => onModeChange("simple")}
        >
          <span className="mode-symbol">K</span> Mot-clé
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === "advanced"}
          className={mode === "advanced" ? "selected" : ""}
          onClick={() => onModeChange("advanced")}
        >
          <span className="mode-symbol regex-symbol">.*</span> RegEx
        </button>
      </div>
      <div className="search-input-row">
        <Icon name="search" size={20} />
        <input
          value={input}
          onChange={(event) => onInputChange(event.target.value)}
          placeholder={mode === "simple" ? "Ex. : adventure" : "Ex. : m(an|en)"}
          aria-label={mode === "simple" ? "Mot-clé" : "Expression régulière"}
          maxLength={mode === "advanced" ? 256 : undefined}
        />
        <kbd>↵</kbd>
        <button className="search-submit" type="submit" disabled={loading}>
          {loading ? (
            <span className="spinner" />
          ) : (
            <>Rechercher <Icon name="arrow" size={17} /></>
          )}
        </button>
      </div>
      <div className="search-hint">
        {mode === "simple"
          ? "Un seul mot · recherche par correspondance KMP"
          : "Automates NFA → DFA → minimisation Hopcroft"}
        {mode === "advanced" && (
          <span className="character-count">{input.length}/256</span>
        )}
      </div>
    </form>
  );
}
