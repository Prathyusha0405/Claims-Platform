import { isRole, ROLE_LABELS, ROLES } from './role';

describe('roles', () => {
  it('are the three the backend puts in the token', () => {
    expect(ROLES).toEqual(['CLAIMANT', 'CLAIMS_STAFF', 'MANAGER']);
  });

  it('are shown by their business names', () => {
    expect(ROLE_LABELS).toEqual({ CLAIMANT: 'Claimant', CLAIMS_STAFF: 'Claims staff', MANAGER: 'Manager' });
  });

  it.each(['CLAIMANT', 'CLAIMS_STAFF', 'MANAGER'])('accepts %s', (role) => {
    expect(isRole(role)).toBe(true);
  });

  it.each(['OFFICER', 'ADMIN', 'claims_staff', 'Manager', '', null, undefined, 7])('refuses %s', (value) => {
    expect(isRole(value)).toBe(false);
  });
});
