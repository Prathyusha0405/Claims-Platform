import { resolveReturnUrl, roleHome } from './role-home';

describe('roleHome', () => {
  it('lands each role on the first page of its own area', () => {
    expect(roleHome('CLAIMANT')).toBe('/claimant/my-claims');
    expect(roleHome('OFFICER')).toBe('/officer/work-queue');
    expect(roleHome('ADMIN')).toBe('/admin/dashboard');
  });
});

describe('resolveReturnUrl', () => {
  it('goes back to the requested page when it is inside the role\'s own area', () => {
    expect(resolveReturnUrl('/claimant/claims/CLM-2026-000042', 'CLAIMANT')).toBe('/claimant/claims/CLM-2026-000042');
    expect(resolveReturnUrl('/officer/work-queue?page=2&sort=claimAmount,desc', 'OFFICER')).toBe(
      '/officer/work-queue?page=2&sort=claimAmount,desc',
    );
    expect(resolveReturnUrl('/admin/dashboard#liability', 'ADMIN')).toBe('/admin/dashboard#liability');
    expect(resolveReturnUrl('/claimant', 'CLAIMANT')).toBe('/claimant');
  });

  it('falls back to the role home when there is nothing to return to', () => {
    expect(resolveReturnUrl(null, 'OFFICER')).toBe('/officer/work-queue');
    expect(resolveReturnUrl(undefined, 'OFFICER')).toBe('/officer/work-queue');
    expect(resolveReturnUrl('', 'OFFICER')).toBe('/officer/work-queue');
  });

  it("does not follow a link into another role's area", () => {
    expect(resolveReturnUrl('/officer/work-queue', 'CLAIMANT')).toBe('/claimant/my-claims');
    expect(resolveReturnUrl('/admin/dashboard', 'OFFICER')).toBe('/officer/work-queue');
    expect(resolveReturnUrl('/claimant/my-claims', 'ADMIN')).toBe('/admin/dashboard');
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
