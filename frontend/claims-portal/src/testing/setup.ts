import { vi } from 'vitest';

// The first test in a file to render Material components or load a lazy route pays a cold-start cost that can pass
// Vitest's 5 s default on a busy or cloud-synced disk. A generous limit keeps that from reading as a failure; tests
// that really hang still fail.
vi.setConfig({ testTimeout: 30_000 });
