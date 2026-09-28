import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { useApp, useErrorMessage } from '../AppContext.ts';
import { projectsApi } from '../api.ts';
import type { CreateProjectForm, Project } from '../types.ts';
import { checkProjectId } from '../validation.ts';

const EMPTY_PROJECT: CreateProjectForm = { name: '', description: '', projectId: '' };

interface ProjectsPageProps {
  onOpen: (project: Project) => void;
}

export default function ProjectsPage({ onOpen }: ProjectsPageProps) {
  const { notify } = useApp();
  const errorMessage = useErrorMessage();
  const [projects, setProjects] = useState<Project[] | null>(null); // null = loading
  const [form, setForm] = useState<CreateProjectForm>(EMPTY_PROJECT);
  const [createError, setCreateError] = useState('');
  const [accessId, setAccessId] = useState('');
  const [accessError, setAccessError] = useState('');

  useEffect(() => {
    projectsApi.mine().then(setProjects).catch((err: unknown) => setAccessError(errorMessage(err)));
  }, []); // load once when the window opens

  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const v = { ...form, projectId: form.projectId.trim() };
    if (!v.name.trim()) return setCreateError('Project name is required.');
    const bad = checkProjectId(v.projectId);
    if (bad) return setCreateError(bad);
    setCreateError('');
    try {
      const project = await projectsApi.create(v);
      notify(`Created project ${project.projectId}`);
      onOpen(project);
    } catch (err) {
      setCreateError(errorMessage(err));
    }
  }

  async function access(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const id = accessId.trim();
    if (!id) return setAccessError('Enter a project ID.');
    setAccessError('');
    try {
      onOpen(await projectsApi.access(id));
    } catch (err) {
      setAccessError(errorMessage(err));
    }
  }

  /** Binds an input to one key of the create-project form. */
  const field = (key: keyof CreateProjectForm) => ({
    value: form[key],
    onChange: (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setForm({ ...form, [key]: e.target.value }),
  });

  return (
    <section className="window">
      <h1>Project management</h1>
      <div className="split">
        <form className="panel" onSubmit={create} noValidate>
          <h2>Create new project</h2>
          <label>Name <input {...field('name')} maxLength={80} /></label>
          <label>Description <textarea {...field('description')} maxLength={500} rows={3} /></label>
          <label>Project ID <input {...field('projectId')} maxLength={32} placeholder="e.g. LAB7" /></label>
          <p className="error" role="alert">{createError}</p>
          <button type="submit" className="primary">Create new project</button>
        </form>

        <form className="panel" onSubmit={access} noValidate>
          <h2>Use existing project</h2>
          <label>Project ID <input value={accessId} onChange={(e) => setAccessId(e.target.value)} maxLength={32} /></label>
          <p className="error" role="alert">{accessError}</p>
          <button type="submit" className="primary">Access project</button>

          <h3>Your projects</h3>
          <ul className="project-list">
            {projects?.length === 0 && <li className="muted">You have no projects yet.</li>}
            {projects?.map((p) => (
              <li key={p.projectId}>
                <button type="button" onClick={() => onOpen(p)}>
                  <strong>{p.projectId} — {p.name}</strong>
                  <small>{p.memberCount} member{p.memberCount === 1 ? '' : 's'}{p.owner ? ' · owner' : ''}</small>
                </button>
              </li>
            ))}
          </ul>
        </form>
      </div>
    </section>
  );
}
