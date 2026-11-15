/** Locale-aware display helpers shared by the search and monitoring views. */
export function formatNumber(value: number | null | undefined): string {
  return value == null ? "—" : new Intl.NumberFormat("fr-FR").format(value);
}

export function formatScore(value: number): string {
  return new Intl.NumberFormat("fr-FR", {
    maximumFractionDigits: 3,
  }).format(value);
}

export function formatPercent(value: number): string {
  return `${new Intl.NumberFormat("fr-FR", {
    maximumFractionDigits: 0,
  }).format(value * 100)} %`;
}

export function formatDate(value: string | null): string {
  if (!value) return "Jamais calculé";
  return new Intl.DateTimeFormat("fr-FR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

/** Converts unknown request failures into a message suitable for the UI. */
export function getErrorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "Une erreur inattendue est survenue.";
}

/** Mirrors the backend's ASCII token rules for strict client-side KMP input. */
export function isSingleKeyword(value: string): boolean {
  const normalized = value
    .replace(/[’‘ʼ]/g, "'")
    .replace(/[‐‑‒–—]/g, "-")
    .normalize("NFKD")
    .replace(/\p{M}/gu, "")
    .replace(/ß/g, "ss")
    .replace(/æ/g, "ae")
    .replace(/œ/g, "oe")
    .replace(/ø/g, "o")
    .replace(/ł/g, "l")
    .toLowerCase();

  return /^[a-z]+(?:['-][a-z]+)*$/.test(normalized);
}
