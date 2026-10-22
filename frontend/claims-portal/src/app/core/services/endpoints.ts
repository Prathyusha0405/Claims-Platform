/**
 * Every backend URL the app calls, in one place. They are relative on purpose: in development the dev-server proxy
 * (proxy.conf.json) routes each prefix to its service, and in production a reverse proxy does the same, so the app
 * never knows three host names and no CORS configuration is needed.
 *
 *   /api/auth, /api/users                → auth-service       :8081
 *   /api/claims, /api/notifications      → claim-service      :8082
 *   /api/officer, /api/dashboard         → assessment-service :8083
 */
export const API = {
  auth: {
    login: '/api/auth/login',
    register: '/api/auth/register',
    me: '/api/users/me',
  },
  claims: {
    create: '/api/claims',
    myClaims: '/api/claims/my-claims',
    search: '/api/claims/search',
    byNumber: (claimNumber: string) => `/api/claims/${encodeURIComponent(claimNumber)}`,
    additionalInfo: (id: number) => `/api/claims/${id}/additional-info`,
  },
  notifications: {
    list: '/api/notifications',
    unreadCount: '/api/notifications/unread-count',
    markRead: (id: number) => `/api/notifications/${id}/read`,
    readAll: '/api/notifications/read-all',
  },
  officer: {
    unassigned: '/api/officer/claims/unassigned',
    assign: (id: number) => `/api/officer/claims/${id}/assign`,
    approve: (id: number) => `/api/officer/claims/${id}/approve`,
    reject: (id: number) => `/api/officer/claims/${id}/reject`,
    requestInfo: (id: number) => `/api/officer/claims/${id}/request-info`,
    notes: (id: number) => `/api/officer/claims/${id}/notes`,
  },
  dashboard: {
    statusSummary: '/api/dashboard/status-summary',
    officerWorkload: '/api/dashboard/officer-workload',
    liabilityExposure: '/api/dashboard/liability-exposure',
  },
} as const;

/** Endpoints that must work without a token, and must never be sent one. */
export const PUBLIC_API_PATHS: readonly string[] = [API.auth.login, API.auth.register];
