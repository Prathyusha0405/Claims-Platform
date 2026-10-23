import { base64Url, fakeJwt } from '@testing/auth-testing';
import { decodeJwt } from './jwt';

describe('decodeJwt', () => {
  it('reads the claims auth-service puts in a token', () => {
    const token = fakeJwt({ sub: '12', email: 'a@b.com', name: 'Ann', role: 'CLAIMS_STAFF', iat: 100, exp: 200 });

    expect(decodeJwt(token)).toEqual({ sub: '12', email: 'a@b.com', name: 'Ann', role: 'CLAIMS_STAFF', iat: 100, exp: 200 });
  });

  it('decodes names in any script, not just ASCII', () => {
    expect(decodeJwt(fakeJwt({ name: '陳 美玲 – Zoë' }))?.name).toBe('陳 美玲 – Zoë');
  });

  it('understands the URL-safe alphabet and missing padding', () => {
    // Runs of "?" and ">" encode to "/" and "+" in plain base64, i.e. to "_" and "-" in base64url.
    const name = '???>>>???>>>???>>>???>>>';
    const segment = base64Url(JSON.stringify({ sub: '1', email: 'e', name, role: 'MANAGER', iat: 1, exp: 2 }));
    expect(segment).toMatch(/[-_]/);
    expect(segment).not.toContain('=');

    expect(decodeJwt(`x.${segment}.y`)?.name).toBe(name);
  });

  it.each([
    ['not a token at all', 'not-a-jwt'],
    ['too few segments', 'a.b'],
    ['too many segments', 'a.b.c.d'],
    ['an empty string', ''],
    ['a payload that is not base64', 'a.@@@.c'],
    ['a payload that is not JSON', `a.${base64Url('hello')}.c`],
    ['a payload that is not an object', `a.${base64Url('[1,2]')}.c`],
    ['a missing role', `a.${base64Url('{"sub":"1","email":"e","name":"n","iat":1,"exp":2}')}.c`],
    ['an unknown role', `a.${base64Url('{"sub":"1","email":"e","name":"n","role":"ROOT","iat":1,"exp":2}')}.c`],
    ['a role name retired by the rename (OFFICER)', `a.${base64Url('{"sub":"1","email":"e","name":"n","role":"OFFICER","iat":1,"exp":2}')}.c`],
    ['a role name retired by the rename (ADMIN)', `a.${base64Url('{"sub":"1","email":"e","name":"n","role":"ADMIN","iat":1,"exp":2}')}.c`],
    ['an empty subject', `a.${base64Url('{"sub":"","email":"e","name":"n","role":"MANAGER","iat":1,"exp":2}')}.c`],
    ['a non-numeric expiry', `a.${base64Url('{"sub":"1","email":"e","name":"n","role":"MANAGER","iat":1,"exp":"soon"}')}.c`],
  ])('rejects %s', (_label, token) => {
    expect(decodeJwt(token)).toBeNull();
  });
});
