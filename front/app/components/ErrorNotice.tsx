import type { ReactNode } from "react";

interface ErrorNoticeProps {
  children: ReactNode;
}

/** Presents an actionable API or validation error with alert semantics. */
export function ErrorNotice({ children }: ErrorNoticeProps) {
  return (
    <div className="error-notice" role="alert">
      {children}
    </div>
  );
}
