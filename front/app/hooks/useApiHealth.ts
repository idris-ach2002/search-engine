import { useEffect, useState } from "react";
import { api } from "../api";
import type { ApiAvailability } from "../types/app";

/** Reads the backend health endpoint once and exposes connection state. */
export function useApiHealth(): ApiAvailability {
  const [availability, setAvailability] = useState<ApiAvailability>("checking");

  useEffect(() => {
    let active = true;
    api.health().then(
      () => {
        if (active) setAvailability("online");
      },
      () => {
        if (active) setAvailability("offline");
      },
    );

    return () => {
      active = false;
    };
  }, []);

  return availability;
}
