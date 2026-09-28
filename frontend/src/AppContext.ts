import { createContext, useContext } from 'react';
import { ApiError } from './api.ts';

export interface AppServices {
  /** Shows a short confirmation at the bottom of the screen. */
  notify: (message: string) => void;
  /** Sends the user back to the login window after a 401. */
  sessionExpired: () => void;
}

export const AppContext = createContext<AppServices | null>(null);

export function useApp(): AppServices {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside <AppContext.Provider>');
  return ctx;
}

/**
 * Returns a function that turns any thrown error into the message to show. A 401 means the session
 * ended, so instead of an inline message the whole app goes back to the login window.
 */
export function useErrorMessage(): (err: unknown) => string {
  const { sessionExpired } = useApp();
  return (err) => {
    if (err instanceof ApiError && err.status === 401) {
      sessionExpired();
      return '';
    }
    return err instanceof Error ? err.message : 'Something went wrong.';
  };
}
