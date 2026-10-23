import { resolveReturnUrl, roleHome } from './role-home';

describe('roleHome', () => {
  it('lands each role on the first page of its own area', () => {
    expect(roleHome('CLAIMANT')).toBe('/claimant/my-claims');
    expect(roleHome('CLAIMS_STAFF')).toBe('/staff/work-queue');
    expect(roleHome('MANAGER')).toBe('/manager/dashboard');
  });
});

describe('resolveReturnUrl', () => {
  it('goes back to the requested page when it is inside the role\'s own area', () => {
    expect(resolveReturnUrl('/claimant/claims/CLM-2026-000042', 'CLAIMANT')).toBe('/claimant/claims/CLM-2026-000042');
    expect(resolveReturnUrl('/staff/work-queue?page=2&sort=claimAmount,desc', 'CLAIMS_STAFF')).toBe(
      '/staff/work-queue?page=2&sort=claimAmount,desc',
    );
    expect(resolveReturnUrl('/manager/dashboard#liability', 'MANAGER')).toBe('/manager/dashboard#liability');
    expect(resolveReturnUrl('/claimant', 'CLAIMANT')).toBe('/claimant');
  });

  it('falls back to the role home when there is nothing to return to', () => {
    expect(resolveReturnUrl(null, 'CLAIMS_STAFF')).toBe('/staff/work-queue');
    expect(resolveReturnUrl(undefined, 'CLAIMS_STAFF')).toBe('/staff/work-queue');
    expect(resolveReturnUrl('', 'CLAIMS_STAFF')).toBe('/staff/work-queue');
  });

  it("does not follow a link into another role's area", () => {
    expect(resolveReturnUrl('/staff/work-queue', 'CLAIMANT')).toBe('/claimant/my-claims');
    expect(resolveReturnUrl('/manager/dashboard', 'CLAIMS_STAFF')).toBe('/staff/work-queue');
    expect(resolveReturnUrl('/claimant/my-claims', 'MANAGER')).toBe('/manager/dashboard');
  });

  it('does not treat a longer area name as the area', () => {
    expect(resolveReturnUrl('/claimants/secret', 'CLAIMANT')).toBe('/claimant/my-claims');
    expect(resolveReturnUrl('/claimant-evil/x', 'CLAIMANT')).toBe('/claimant/my-claims');
  });

  it.each([
    ['another origin', 'https://evil.example/claimant/my-claims'],
    ['a protocol-relative URL', '//evil.example/claimant/my-claims'],
    ['a backslash trick', '/\\evil.example'],
    ['a script URL', 'javascript:alert(1)'],
    ['a relative path', 'claimant/my-claims'],
    ['a tab hidden in the path', '/\t/evil.example'],
    ['a newline hidden in the path', '/claimant/\n/evil.example'],
    ['a page nobody should "return" to', '/forbidden'],
  ])('refuses %s', (_label, url) => {
    expect(resolveReturnUrl(url, 'CLAIMANT')).toBe('/claimant/my-claims');
  });
});
