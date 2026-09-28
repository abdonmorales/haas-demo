// In-browser stand-in for the Spring Boot API, used only by the static GitHub Pages build
// (`npm run build:pages`). It answers the same routes with the same rules and error messages,
// keeping its data in this browser's localStorage, so the demo works with no server at all.
// The seed data mirrors `haas.seed` in src/main/resources/application.yml; change both together.
import type { HardwareSet, Project } from './types.ts';

export interface MockResponse {
  status: number;
  data: unknown;
}

interface StoredUser { userId: string; passwordHash: string; demo: boolean }
interface StoredProject { projectId: string; name: string; description: string; owner: string; members: string[] }
interface StoredHardware { name: string; description: string; capacity: number; available: number; allocations: Record<string, number> }
interface State { users: StoredUser[]; projects: StoredProject[]; hardware: StoredHardware[] }

const STATE_KEY = 'haas-demo-state-v1';
const SESSION_KEY = 'haas-demo-session';
const DEMO_PASSWORD = 'Demo#2026!';
const USER_ID = /^[A-Za-z0-9_.-]{3,32}$/;
const PROJECT_ID = /^[A-Za-z0-9_-]{3,32}$/;
const MAX_REQUEST = 10_000;

class Failure extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

// ---- storage (falls back to memory when storage is blocked, e.g. private windows) ----

let memoryState: State | null = null;
let memorySession: string | null = null;

async function load(): Promise<State> {
  try {
    const raw = localStorage.getItem(STATE_KEY);
    if (raw) return JSON.parse(raw) as State;
  } catch { /* use memory */ }
  memoryState ??= await seed();
  save(memoryState);
  return memoryState;
}

function save(state: State): void {
  memoryState = state;
  try { localStorage.setItem(STATE_KEY, JSON.stringify(state)); } catch { /* memory only */ }
}

function getSession(): string | null {
  try { return sessionStorage.getItem(SESSION_KEY); } catch { return memorySession; }
}

function setSession(key: string | null): void {
  memorySession = key;
  try {
    if (key) sessionStorage.setItem(SESSION_KEY, key);
    else sessionStorage.removeItem(SESSION_KEY);
  } catch { /* memory only */ }
}

async function hash(password: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(password));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
}

async function seed(): Promise<State> {
  const demoHash = await hash(DEMO_PASSWORD);
  const state: State = {
    users: ['ada', 'grace', 'linus'].map((userId) => ({ userId, passwordHash: demoHash, demo: true })),
    projects: [
      { projectId: 'AMPLAB1', name: 'Audio Amplifier Build',
        description: 'Designs and characterizes a Class-AB audio amplifier.', owner: 'ada', members: ['ada', 'grace'] },
      { projectId: 'SENSOR7', name: 'Sensor Calibration Rig',
        description: 'Calibrates temperature and strain sensors against reference instruments.', owner: 'linus', members: ['linus'] },
    ],
    hardware: [
      { name: 'Oscilloscopes', description: '4-channel 200 MHz digital oscilloscopes', capacity: 60, available: 60, allocations: {} },
      { name: 'Function-Generators', description: 'Dual-channel arbitrary function generators', capacity: 40, available: 40, allocations: {} },
      { name: 'Power-Supplies', description: 'Triple-output programmable bench power supplies', capacity: 30, available: 30, allocations: {} },
    ],
  };
  transfer(state, 'Oscilloscopes', 'AMPLAB1', 12);
  transfer(state, 'Function-Generators', 'SENSOR7', 8);
  return state;
}

// ---- rules (mirroring UserService, ProjectService and HardwareService) ----

const key = (id: string) => id.trim().toLowerCase();

function findUser(state: State, userId: string): StoredUser | undefined {
  return state.users.find((u) => key(u.userId) === key(userId));
}

async function authenticate(state: State, userId: unknown, password: unknown): Promise<StoredUser> {
  if (typeof userId !== 'string' || typeof password !== 'string') throw new Failure(401, 'Invalid user ID or password.');
  const user = findUser(state, userId);
  if (!user || user.passwordHash !== await hash(password)) throw new Failure(401, 'Invalid user ID or password.');
  return user;
}

function validateNewPassword(password: unknown, confirm: unknown): string {
  if (typeof password !== 'string' || password.length < 8 || password.length > 64) {
    throw new Failure(400, 'Password must be 8-64 characters.');
  }
  if (/\s/.test(password)) throw new Failure(400, 'Password cannot contain spaces.');
  if (password !== confirm) throw new Failure(400, 'Passwords do not match.');
  return password;
}

function requireSession(state: State): string {
  const userKey = getSession();
  if (!userKey || !state.users.some((u) => key(u.userId) === userKey)) throw new Failure(401, 'Please sign in.');
  return userKey;
}

function findProject(state: State, projectId: unknown): StoredProject {
  if (typeof projectId !== 'string' || !projectId.trim()) throw new Failure(400, 'Project ID is required.');
  const project = state.projects.find((p) => key(p.projectId) === key(projectId));
  if (!project) throw new Failure(404, `No project with ID ${projectId.trim()}.`);
  return project;
}

function requireMember(state: State, userKey: string, projectId: unknown): string {
  const project = findProject(state, projectId);
  if (!project.members.includes(userKey)) throw new Failure(403, `You are not a member of project ${project.projectId}.`);
  return project.projectId;
}

function projectView(p: StoredProject, userKey: string): Project {
  return { projectId: p.projectId, name: p.name, description: p.description,
           memberCount: p.members.length, owner: p.owner === userKey };
}

function hardwareView(h: StoredHardware, projectId: string | null): HardwareSet {
  return { name: h.name, description: h.description, capacity: h.capacity, available: h.available,
           checkedOut: projectId ? h.allocations[projectId] ?? 0 : 0 };
}

function requireSet(state: State, name: string): StoredHardware {
  const set = state.hardware.find((h) => h.name === name);
  if (!set) throw new Failure(404, `No hardware set named ${name}.`);
  return set;
}

function validateQuantity(qty: unknown): number {
  if (qty === null || qty === undefined) throw new Failure(400, 'Quantity is required.');
  if (typeof qty !== 'number' || !Number.isInteger(qty)) {
    throw new Failure(400, 'Malformed request — check that numbers are whole numbers.');
  }
  if (qty <= 0) throw new Failure(400, 'Quantity must be a positive whole number.');
  if (qty > MAX_REQUEST) throw new Failure(400, `Quantity cannot exceed ${MAX_REQUEST} per request.`);
  return qty;
}

/** Moves units between the pool and a project; positive = check out, negative = check in. */
function transfer(state: State, setName: string, projectId: string, delta: number): StoredHardware {
  const set = requireSet(state, setName);
  const held = set.allocations[projectId] ?? 0;
  if (delta > 0 && set.available < delta) {
    throw new Failure(409, `Only ${set.available} unit(s) of ${set.name} are available; you requested ${delta}.`);
  }
  if (delta < 0 && held < -delta) {
    throw new Failure(409, `Project ${projectId} only holds ${held} unit(s) of ${set.name}; cannot return ${-delta}.`);
  }
  set.available -= delta;
  if (held + delta === 0) delete set.allocations[projectId];
  else set.allocations[projectId] = held + delta;
  return set;
}

// ---- routing ----

type Body = Record<string, unknown>;

async function route(method: string, path: string, body: Body): Promise<MockResponse> {
  const url = new URL(path, 'http://mock');
  const p = url.pathname;
  const state = await load();
  const ok = (data: unknown, status = 200): MockResponse => ({ status, data });

  if (method === 'GET' && p === '/api/auth/demo-users') {
    return ok(state.users.filter((u) => u.demo).map((u) => u.userId));
  }
  if (method === 'GET' && p === '/api/auth/me') {
    return ok({ userId: findUser(state, requireSession(state))!.userId });
  }
  if (method === 'POST' && p === '/api/auth/login') {
    const user = await authenticate(state, body.userId, body.password);
    setSession(key(user.userId));
    return ok({ userId: user.userId });
  }
  if (method === 'POST' && p === '/api/auth/logout') {
    setSession(null);
    return ok(undefined, 204);
  }
  if (method === 'POST' && p === '/api/auth/register') {
    const userId = body.userId;
    if (typeof userId !== 'string' || !USER_ID.test(userId)) {
      throw new Failure(400, "User ID must be 3-32 characters: letters, digits, '.', '_' or '-'.");
    }
    const password = validateNewPassword(body.password, body.confirmPassword);
    if (findUser(state, userId)) throw new Failure(409, 'That user ID is already taken.');
    state.users.push({ userId, passwordHash: await hash(password), demo: false });
    save(state);
    return ok({ message: 'Account created — you can sign in now.' }, 201);
  }
  if (method === 'POST' && p === '/api/auth/change-password') {
    const user = await authenticate(state, body.userId, body.oldPassword);
    const password = validateNewPassword(body.newPassword, body.confirmPassword);
    const newHash = await hash(password);
    if (newHash === user.passwordHash) throw new Failure(400, 'New password must differ from the old one.');
    user.passwordHash = newHash;
    save(state);
    return ok({ message: 'Password updated — sign in with your new password.' });
  }

  if (method === 'GET' && p === '/api/projects') {
    const userKey = requireSession(state);
    return ok(state.projects
      .filter((pr) => pr.members.includes(userKey))
      .sort((a, b) => (key(a.projectId) < key(b.projectId) ? -1 : 1))
      .map((pr) => projectView(pr, userKey)));
  }
  if (method === 'POST' && p === '/api/projects') {
    const userKey = requireSession(state);
    const { projectId, name, description } = body;
    if (typeof projectId !== 'string' || !PROJECT_ID.test(projectId.trim())) {
      throw new Failure(400, "Project ID must be 3-32 characters: letters, digits, '_' or '-'.");
    }
    if (typeof name !== 'string' || !name.trim() || name.length > 80) {
      throw new Failure(400, 'Project name is required (max 80 characters).');
    }
    if (typeof description === 'string' && description.length > 500) {
      throw new Failure(400, 'Description is limited to 500 characters.');
    }
    if (state.projects.some((pr) => key(pr.projectId) === key(projectId))) {
      throw new Failure(409, `Project ID ${projectId.trim()} already exists — use 'Access project' to join it.`);
    }
    const project: StoredProject = { projectId: projectId.trim(), name: name.trim(),
      description: typeof description === 'string' ? description.trim() : '', owner: userKey, members: [userKey] };
    state.projects.push(project);
    save(state);
    return ok(projectView(project, userKey), 201);
  }
  const access = p.match(/^\/api\/projects\/([^/]+)\/access$/);
  if (method === 'POST' && access) {
    const userKey = requireSession(state);
    const project = findProject(state, decodeURIComponent(access[1]!));
    if (!project.members.includes(userKey)) project.members.push(userKey);
    save(state);
    return ok(projectView(project, userKey));
  }

  if (method === 'GET' && p === '/api/hardware') {
    const userKey = requireSession(state);
    const requested = url.searchParams.get('projectId');
    const projectId = requested === null ? null : requireMember(state, userKey, requested);
    return ok([...state.hardware]
      .sort((a, b) => (a.name < b.name ? -1 : 1))
      .map((h) => hardwareView(h, projectId)));
  }
  const move = p.match(/^\/api\/hardware\/([^/]+)\/(checkout|checkin)$/);
  if (method === 'POST' && move) {
    const projectId = requireMember(state, requireSession(state), body.projectId);
    const qty = validateQuantity(body.quantity);
    const set = transfer(state, decodeURIComponent(move[1]!), projectId, move[2] === 'checkout' ? qty : -qty);
    save(state);
    return ok(hardwareView(set, projectId));
  }

  throw new Failure(404, 'Not Found');
}

export async function handle(method: string, path: string, body: unknown): Promise<MockResponse> {
  try {
    return await route(method, path, (body ?? {}) as Body);
  } catch (err) {
    if (err instanceof Failure) return { status: err.status, data: { error: err.message } };
    throw err;
  }
}
