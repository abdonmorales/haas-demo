// Client-side checks for instant feedback. The server enforces the same rules regardless.
// Each returns an error message, or '' when the value is fine.
const USER_ID = /^[A-Za-z0-9_.-]{3,32}$/;
const PROJECT_ID = /^[A-Za-z0-9_-]{3,32}$/;

export function checkUserId(value: string): string {
  return USER_ID.test(value) ? '' : 'User ID must be 3-32 characters: letters, digits, ".", "_" or "-".';
}

export function checkProjectId(value: string): string {
  return PROJECT_ID.test(value) ? '' : 'Project ID must be 3-32 characters: letters, digits, "_" or "-".';
}

export function checkNewPassword(password: string, confirm: string): string {
  if (password.length < 8 || password.length > 64) return 'Password must be 8-64 characters.';
  if (/\s/.test(password)) return 'Password cannot contain spaces.';
  if (password !== confirm) return 'Passwords do not match.';
  return '';
}

/** A strictly positive whole number, or NaN for anything else ("3.5", "1e3", "-2", ""). */
export function parseQuantity(raw: string): number {
  const s = raw.trim();
  return /^\d{1,5}$/.test(s) && Number(s) > 0 ? Number(s) : NaN;
}
