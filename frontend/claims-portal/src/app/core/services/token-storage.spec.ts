import { TestBed } from '@angular/core/testing';

import { TokenStorage } from './token-storage';

describe('TokenStorage', () => {
  let storage: TokenStorage;

  beforeEach(() => {
    sessionStorage.clear();
    storage = TestBed.inject(TokenStorage);
  });

  afterEach(() => vi.restoreAllMocks());

  it('keeps a token for the lifetime of the tab and forgets it on clear', () => {
    expect(storage.read()).toBeNull();

    storage.write('abc.def.ghi');
    expect(storage.read()).toBe('abc.def.ghi');

    storage.clear();
    expect(storage.read()).toBeNull();
  });

  it('uses sessionStorage, never localStorage (the token must not outlive the tab)', () => {
    storage.write('abc.def.ghi');

    expect(sessionStorage.length).toBe(1);
    expect(localStorage.length).toBe(0);
  });

  it('behaves as "nothing stored" when the browser refuses storage access', () => {
    const blocked = () => {
      throw new DOMException('blocked', 'SecurityError');
    };
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(blocked);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(blocked);
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(blocked);

    expect(storage.read()).toBeNull();
    expect(() => storage.write('abc.def.ghi')).not.toThrow();
    expect(() => storage.clear()).not.toThrow();
  });
});
