import { Role } from './role';

/** auth-service `UserResponse`. */
export interface User {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  createdAt: string;
}

/** `POST /api/auth/login` body. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** `POST /api/auth/register` body. Registration always creates a CLAIMANT. */
export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
}

/** `POST /api/auth/login` response. */
export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  /** Token lifetime in seconds. */
  expiresIn: number;
  user: User;
}

/** A signed-in browser session. */
export interface Session {
  /** The raw JWT, sent as the bearer token. */
  token: string;
  user: User;
  /** Epoch milliseconds after which the token is no longer valid. */
  expiresAt: number;
}
