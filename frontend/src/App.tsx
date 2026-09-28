import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppContext, type AppServices } from './AppContext.ts';
import { auth } from './api.ts';
import type { Project } from './types.ts';
import LoginPage from './pages/LoginPage.tsx';
import ProjectsPage from './pages/ProjectsPage.tsx';
import ResourcePage from './pages/ResourcePage.tsx';

/**
 * Top-level navigation is plain state rather than a router: which window shows depends only on
 * whether someone is signed in and whether a project is open.
 */
export default function App() {
  const [userId, setUserId] = useState<string | null | undefined>(undefined); // undefined = still checking
  const [project, setProject] = useState<Project | null>(null);
  const [toast, setToast] = useState('');
  const toastTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => {
    auth.me().then((me) => setUserId(me.userId)).catch(() => setUserId(null));
  }, []);

  const notify = useCallback((message: string) => {
    setToast(message);
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(''), 2600);
  }, []);

  const sessionExpired = useCallback(() => {
    setUserId(null);
    setProject(null);
    notify('Your session ended — please sign in again.');
  }, [notify]);

  const services = useMemo<AppServices>(() => ({ notify, sessionExpired }), [notify, sessionExpired]);

  async function signOut() {
    await auth.logout().catch(() => {});
    setUserId(null);
    setProject(null);
  }

  let page = null;
  if (userId === null) page = <LoginPage onSignedIn={setUserId} />;
  else if (userId !== undefined && !project) page = <ProjectsPage onOpen={setProject} />;
  else if (userId !== undefined && project) page = <ResourcePage project={project} onBack={() => setProject(null)} />;

  return (
    <AppContext.Provider value={services}>
      <header className="topbar">
        <span className="brand">HaaS <small>Hardware-as-a-Service demo</small></span>
        {userId && (
          <span className="session">
            Signed in as <strong>{userId}</strong>{' '}
            <button className="link" onClick={signOut}>Sign out</button>
          </span>
        )}
      </header>
      <main>{page}</main>
      <div className={`toast${toast ? ' show' : ''}`} role="status" aria-live="polite">{toast}</div>
    </AppContext.Provider>
  );
}
