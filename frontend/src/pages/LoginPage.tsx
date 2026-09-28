import { useEffect, useState, type FormEvent } from 'react';
import { useApp } from '../AppContext.ts';
import { auth } from '../api.ts';
import { checkNewPassword, checkUserId } from '../validation.ts';
import FormDialog from '../components/FormDialog.tsx';

type Popup = 'register' | 'forgot' | null;

const REGISTER_FIELDS = ['userId', 'password', 'confirmPassword'] as const;
const FORGOT_FIELDS = ['userId', 'oldPassword', 'newPassword', 'confirmPassword'] as const;

interface LoginPageProps {
  onSignedIn: (userId: string) => void;
}

export default function LoginPage({ onSignedIn }: LoginPageProps) {
  const { notify } = useApp();
  const [demoUsers, setDemoUsers] = useState<string[]>([]);
  const [userId, setUserId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [popup, setPopup] = useState<Popup>(null);

  useEffect(() => {
    auth.demoUsers().then(setDemoUsers).catch(() => {}); // the chooser is optional
  }, []);

  async function signIn(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const id = userId.trim();
    if (!id || !password) return setError('Enter your user ID and password.');
    const bad = checkUserId(id);
    if (bad) return setError(bad);
    setError('');
    try {
      const me = await auth.login(id, password);
      onSignedIn(me.userId);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign-in failed.');
    }
  }

  async function register(v: Record<(typeof REGISTER_FIELDS)[number], string>) {
    const id = v.userId.trim();
    const problem = checkUserId(id) || checkNewPassword(v.password, v.confirmPassword);
    if (problem) return problem;
    const res = await auth.register({ ...v, userId: id });
    setUserId(id);
    notify(res.message);
  }

  async function changePassword(v: Record<(typeof FORGOT_FIELDS)[number], string>) {
    if (!v.userId.trim() || !v.oldPassword) return 'Enter your user ID and old password.';
    const problem = checkNewPassword(v.newPassword, v.confirmPassword);
    if (problem) return problem;
    const res = await auth.changePassword({ ...v, userId: v.userId.trim() });
    notify(res.message);
  }

  return (
    <section className="window login">
      <h1>Sign in</h1>
      <form onSubmit={signIn} noValidate>
        {demoUsers.length > 0 && (
          <label>User choose
            <select value="" onChange={(e) => setUserId(e.target.value)}>
              <option value="">— pick a demo user —</option>
              {demoUsers.map((id) => <option key={id} value={id}>{id}</option>)}
            </select>
          </label>
        )}
        <label>User ID
          <input value={userId} onChange={(e) => setUserId(e.target.value)}
                 autoComplete="username" maxLength={32} required />
        </label>
        <label>Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                 autoComplete="current-password" maxLength={64} required />
        </label>
        <p className="error" role="alert">{error}</p>
        <button type="submit" className="primary">Sign in</button>
      </form>
      <div className="login-links">
        <button className="link" onClick={() => setPopup('register')}>Create account</button>
        <button className="link" onClick={() => setPopup('forgot')}>Forgot password</button>
      </div>

      <FormDialog open={popup === 'register'} title="Create account" submitLabel="Create account"
                  fields={REGISTER_FIELDS} onClose={() => setPopup(null)} onSubmit={register}>
        <label>User ID <input name="userId" maxLength={32} autoComplete="username" /></label>
        <label>Password <input name="password" type="password" maxLength={64} autoComplete="new-password" /></label>
        <label>Confirm password <input name="confirmPassword" type="password" maxLength={64} autoComplete="new-password" /></label>
        <p className="hint">3-32 characters: letters, digits, <code>. _ -</code>. Passwords 8-64 characters, no spaces.</p>
      </FormDialog>

      <FormDialog open={popup === 'forgot'} title="Change password" submitLabel="Update password"
                  fields={FORGOT_FIELDS} onClose={() => setPopup(null)} onSubmit={changePassword}>
        <label>User ID <input name="userId" maxLength={32} autoComplete="username" /></label>
        <label>Old password <input name="oldPassword" type="password" maxLength={64} autoComplete="current-password" /></label>
        <label>New password <input name="newPassword" type="password" maxLength={64} autoComplete="new-password" /></label>
        <label>Confirm new password <input name="confirmPassword" type="password" maxLength={64} autoComplete="new-password" /></label>
      </FormDialog>
    </section>
  );
}
