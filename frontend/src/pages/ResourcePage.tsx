import { useCallback, useEffect, useState } from 'react';
import { useApp, useErrorMessage } from '../AppContext.ts';
import { hardwareApi } from '../api.ts';
import type { HardwareSet, Project } from '../types.ts';
import { parseQuantity } from '../validation.ts';
import FormDialog from '../components/FormDialog.tsx';

const CHECK_IN_FIELDS = ['quantity'] as const;

interface ResourcePageProps {
  project: Project;
  onBack: () => void;
}

export default function ResourcePage({ project, onBack }: ResourcePageProps) {
  const { notify } = useApp();
  const errorMessage = useErrorMessage();
  const [hardware, setHardware] = useState<HardwareSet[]>([]);
  const [error, setError] = useState('');
  const [returning, setReturning] = useState<HardwareSet | null>(null); // set whose check-in popup is open

  const load = useCallback(async () => {
    setError('');
    try {
      setHardware(await hardwareApi.list(project.projectId));
    } catch (err) {
      setError(errorMessage(err));
    }
  }, [project.projectId]);

  useEffect(() => { void load(); }, [load]);

  async function checkIn({ quantity }: Record<(typeof CHECK_IN_FIELDS)[number], string>) {
    if (!returning) return;
    const qty = parseQuantity(quantity);
    if (Number.isNaN(qty)) return 'Enter a positive whole number.';
    if (qty > returning.checkedOut) return `You can return at most ${returning.checkedOut}.`;
    await hardwareApi.checkIn(returning.name, project.projectId, qty);
    notify(`Returned ${qty} × ${returning.name}`);
    await load();
  }

  return (
    <section className="window">
      <div className="resource-head">
        <div>
          <button className="link" onClick={onBack}>&larr; Projects</button>
          <h1>{project.name} ({project.projectId})</h1>
          <p className="muted">{project.description}</p>
        </div>
        <button className="secondary" onClick={() => void load()}>Refresh</button>
      </div>
      <p className="error" role="alert">{error}</p>

      <div className="table-wrap">
        <table className="hw">
          <thead>
            <tr>
              <th>HW set / compute</th>
              <th className="num">Capacity</th>
              <th className="num">Available</th>
              <th className="num">This project</th>
              <th>Request</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {hardware.map((hw) => (
              <HardwareRow key={hw.name} hw={hw} projectId={project.projectId}
                           onChanged={load} onCheckIn={() => setReturning(hw)} />
            ))}
          </tbody>
        </table>
      </div>

      <FormDialog open={returning !== null} title="Return hardware" submitLabel="Return"
                  fields={CHECK_IN_FIELDS} onClose={() => setReturning(null)} onSubmit={checkIn}>
        {returning && <p className="muted">{returning.name}: this project holds {returning.checkedOut} unit(s).</p>}
        <label>Enter compute amount to return
          <input name="quantity" type="number" min={1} max={returning?.checkedOut} step={1} inputMode="numeric" autoFocus />
        </label>
      </FormDialog>
    </section>
  );
}

interface HardwareRowProps {
  hw: HardwareSet;
  projectId: string;
  onChanged: () => Promise<void>;
  onCheckIn: () => void;
}

function HardwareRow({ hw, projectId, onChanged, onCheckIn }: HardwareRowProps) {
  const { notify } = useApp();
  const errorMessage = useErrorMessage();
  const [request, setRequest] = useState('');
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState('');

  // Live capacity check while typing ("safe math" on the client; the server re-checks atomically).
  const qty = parseQuantity(request);
  let inputError = '';
  if (request !== '' && Number.isNaN(qty)) inputError = 'Enter a positive whole number.';
  else if (qty > hw.available) inputError = `Only ${hw.available} available.`;

  async function checkOut() {
    if (request === '' || inputError) {
      setServerError(inputError || 'Enter a positive whole number to request.');
      return;
    }
    setBusy(true);
    setServerError('');
    try {
      await hardwareApi.checkOut(hw.name, projectId, qty);
      notify(`Checked out ${qty} × ${hw.name}`);
      setRequest('');
    } catch (err) {
      setServerError(errorMessage(err));
    } finally {
      setBusy(false);
      await onChanged();
    }
  }

  const freePct = hw.capacity ? Math.round((hw.available / hw.capacity) * 100) : 0;

  return (
    <tr>
      <td className="name"><strong>{hw.name}</strong><small>{hw.description}</small></td>
      <td className="num">{hw.capacity}</td>
      <td className="num">
        {hw.available}
        <div className="bar" title={`${100 - freePct}% in use`}><span style={{ width: `${freePct}%` }} /></div>
      </td>
      <td className="num">{hw.checkedOut}</td>
      <td>
        <input type="number" min={1} max={hw.available} step={1} inputMode="numeric" placeholder="0"
               aria-label={`Units of ${hw.name} to check out`} aria-invalid={Boolean(inputError)}
               value={request} onChange={(e) => { setRequest(e.target.value); setServerError(''); }} />
        <p className="error" role="alert">{inputError || serverError}</p>
      </td>
      <td>
        <div className="row-actions">
          <button className="primary" onClick={() => void checkOut()} disabled={busy || hw.available === 0}>Check out</button>
          <button className="secondary" onClick={onCheckIn} disabled={hw.checkedOut === 0}>Check in</button>
        </div>
      </td>
    </tr>
  );
}
