const STORAGE_KEY = 'ecm_saved_search_pins';

const readStored = () => {
  if (typeof window === 'undefined') {
    return [] as string[];
  }
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return [] as string[];
  }
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed)
      ? parsed.map((value) => String(value)).filter((value) => value.length > 0)
      : [];
  } catch {
    return [] as string[];
  }
};

const writeStored = (ids: string[]) => {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(ids));
};

export const loadPinnedSavedSearchIds = (): string[] => readStored();

export const savePinnedSavedSearchIds = (ids: string[]): void => {
  writeStored(ids);
};

export const togglePinnedSavedSearchId = (ids: string[], id: string): string[] => {
  const normalizedId = String(id);
  const next = ids.filter((value) => value !== normalizedId);
  if (next.length === ids.length) {
    next.push(normalizedId);
  }
  writeStored(next);
  return next;
};
