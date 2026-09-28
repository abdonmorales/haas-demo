import type {
  ChangePasswordForm, CreateProjectForm, HardwareSet, Me, Message, Project, RegisterForm,
} from './types.ts';

/** An API failure carrying the server's user-facing message and the HTTP status. */
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

type Method = 'GET' | 'POST';

// Every server call goes through here.
async function api<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    credentials: 'same-origin',
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 204) return undefined as T;
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    const serverMessage = (data as { error?: unknown }).error;
    throw new ApiError(typeof serverMessage === 'string' ? serverMessage : `Request failed (${res.status})`, res.status);
  }
  return data as T;
}

export const auth = {
  demoUsers: () => api<string[]>('GET', '/api/auth/demo-users'),
  me: () => api<Me>('GET', '/api/auth/me'),
  login: (userId: string, password: string) => api<Me>('POST', '/api/auth/login', { userId, password }),
  logout: () => api<void>('POST', '/api/auth/logout'),
  register: (form: RegisterForm) => api<Message>('POST', '/api/auth/register', form),
  changePassword: (form: ChangePasswordForm) => api<Message>('POST', '/api/auth/change-password', form),
};

export const projectsApi = {
  mine: () => api<Project[]>('GET', '/api/projects'),
  create: (form: CreateProjectForm) => api<Project>('POST', '/api/projects', form),
  access: (projectId: string) => api<Project>('POST', `/api/projects/${encodeURIComponent(projectId)}/access`),
};

export const hardwareApi = {
  list: (projectId: string) =>
    api<HardwareSet[]>('GET', `/api/hardware?projectId=${encodeURIComponent(projectId)}`),
  checkOut: (setName: string, projectId: string, quantity: number) =>
    api<HardwareSet>('POST', `/api/hardware/${encodeURIComponent(setName)}/checkout`, { projectId, quantity }),
  checkIn: (setName: string, projectId: string, quantity: number) =>
    api<HardwareSet>('POST', `/api/hardware/${encodeURIComponent(setName)}/checkin`, { projectId, quantity }),
};
