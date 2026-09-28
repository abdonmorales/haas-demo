import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { useErrorMessage } from '../AppContext.ts';

interface FormDialogProps<F extends string> {
  open: boolean;
  title: string;
  submitLabel: string;
  /** Names of the inputs inside the form; `onSubmit` receives exactly these keys. */
  fields: readonly F[];
  onClose: () => void;
  /** Return an error message to keep the popup open, or nothing to close it. Thrown errors are shown too. */
  onSubmit: (values: Record<F, string>) => Promise<string | void>;
  children: ReactNode;
}

/**
 * A popup form built on the native <dialog> element (focus trapping, Esc to close, backdrop).
 * The dialog closes only when the submit succeeds.
 */
export default function FormDialog<F extends string>({
  open, title, submitLabel, fields, onClose, onSubmit, children,
}: FormDialogProps<F>) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const errorMessage = useErrorMessage();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open) {
      if (dialog.open) dialog.close();
      formRef.current?.reset();
      setError('');
    }
  }, [open]);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const values = Object.fromEntries(
      fields.map((name) => [name, String(data.get(name) ?? '')]),
    ) as Record<F, string>;
    setError('');
    setBusy(true);
    try {
      const problem = await onSubmit(values);
      if (problem) setError(problem);
      else onClose();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <dialog ref={dialogRef} onClose={onClose}>
      <form ref={formRef} onSubmit={handleSubmit} noValidate>
        <h2>{title}</h2>
        {children}
        <p className="error" role="alert">{error}</p>
        <div className="actions">
          <button type="button" className="secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="primary" disabled={busy}>{submitLabel}</button>
        </div>
      </form>
    </dialog>
  );
}
