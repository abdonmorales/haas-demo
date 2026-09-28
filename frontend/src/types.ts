// Shapes of the JSON the Spring Boot API returns. Each mirrors a Java record on the server
// (named in the comment), so a change there should be made here too.

/** `com.example.haas.user.ProjectView` */
export interface Project {
  projectId: string;
  name: string;
  description: string;
  memberCount: number;
  owner: boolean;
}

/** `com.example.haas.hardware.HardwareView` */
export interface HardwareSet {
  name: string;
  description: string;
  capacity: number;
  available: number;
  /** Units the currently open project holds. */
  checkedOut: number;
}

export interface Me {
  userId: string;
}

export interface Message {
  message: string;
}

export interface RegisterForm {
  userId: string;
  password: string;
  confirmPassword: string;
}

export interface ChangePasswordForm {
  userId: string;
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface CreateProjectForm {
  projectId: string;
  name: string;
  description: string;
}
