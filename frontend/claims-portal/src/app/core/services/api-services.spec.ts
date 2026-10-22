import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PageRequest } from '@shared/models/page';
import { provideHttpTesting } from '@testing/auth-testing';
import { ClaimApi } from './claim-api';
import { DashboardApi } from './dashboard-api';
import { NotificationApi } from './notification-api';
import { NotificationStore } from './notification-store';
import { OfficerApi } from './officer-api';

const PAGE: PageRequest = { page: 1, size: 20, sortField: 'createdAt', sortDirection: 'desc' };

describe('backend API services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideHttpTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** The one request to `url`, ignoring the query string; it is answered with `body`. */
  function answer(url: string, method: string, body: object = {}): TestRequest {
    const request = http.expectOne((r) => r.url === url);
    expect(request.request.method).toBe(method);
    request.flush(body);
    return request;
  }

  describe('ClaimApi', () => {
    it('creates a claim with a POST to /api/claims', () => {
      const body = {
        policyNumber: 'POL-2026-12345',
        claimType: 'AUTO' as const,
        incidentDate: '2026-10-15',
        description: 'Rear-ended',
        claimAmount: 2500,
      };
      TestBed.inject(ClaimApi).create(body).subscribe();

      expect(answer('/api/claims', 'POST').request.body).toEqual(body);
    });

    it('lists my claims with page, size and sort', () => {
      TestBed.inject(ClaimApi).myClaims(PAGE).subscribe();

      const { params } = answer('/api/claims/my-claims', 'GET').request;
      expect(params.get('page')).toBe('1');
      expect(params.get('size')).toBe('20');
      expect(params.get('sort')).toBe('createdAt,desc');
    });

    it('searches with only the filters that have a value', () => {
      TestBed.inject(ClaimApi)
        .search({ claimNumber: 'CLM-2026', status: 'APPROVED', policyNumber: '  ', assignedOfficerId: undefined, fromDate: '2026-10-01' }, PAGE)
        .subscribe();

      const { params } = answer('/api/claims/search', 'GET').request;
      expect(params.get('claimNumber')).toBe('CLM-2026');
      expect(params.get('status')).toBe('APPROVED');
      expect(params.get('fromDate')).toBe('2026-10-01');
      expect(params.has('policyNumber')).toBe(false);
      expect(params.has('assignedOfficerId')).toBe(false);
      expect(params.has('toDate')).toBe(false);
    });

    it('looks a claim up by number, encoding it for the URL', () => {
      TestBed.inject(ClaimApi).get('CLM-2026-000042').subscribe();
      answer('/api/claims/CLM-2026-000042', 'GET');

      TestBed.inject(ClaimApi).get('a/b c').subscribe();
      answer('/api/claims/a%2Fb%20c', 'GET');
    });

    it('answers an information request by the claim id', () => {
      TestBed.inject(ClaimApi).submitAdditionalInfo(42, 'Estimate attached').subscribe();

      expect(answer('/api/claims/42/additional-info', 'POST').request.body).toEqual({ message: 'Estimate attached' });
    });
  });

  describe('OfficerApi', () => {
    it('reads the work queue', () => {
      TestBed.inject(OfficerApi).unassigned({ ...PAGE, sortDirection: 'asc' }).subscribe();

      const { params } = answer('/api/officer/claims/unassigned', 'GET').request;
      expect(params.get('sort')).toBe('createdAt,asc');
    });

    it('takes a claim with an empty POST (the caller is the officer)', () => {
      TestBed.inject(OfficerApi).assign(7).subscribe();

      expect(answer('/api/officer/claims/7/assign', 'POST').request.body).toEqual({});
    });

    it('approves with a trimmed comment, or with none at all', () => {
      const api = TestBed.inject(OfficerApi);

      api.approve(7, '  Damage confirmed  ').subscribe();
      expect(answer('/api/officer/claims/7/approve', 'POST').request.body).toEqual({ comment: 'Damage confirmed' });

      api.approve(7, '   ').subscribe();
      expect(answer('/api/officer/claims/7/approve', 'POST').request.body).toEqual({});

      api.approve(7).subscribe();
      expect(answer('/api/officer/claims/7/approve', 'POST').request.body).toEqual({});
    });

    it('rejects with a reason and asks for information with a message', () => {
      const api = TestBed.inject(OfficerApi);

      api.reject(7, 'Policy lapsed').subscribe();
      expect(answer('/api/officer/claims/7/reject', 'POST').request.body).toEqual({ reason: 'Policy lapsed' });

      api.requestInfo(7, 'Send photos').subscribe();
      expect(answer('/api/officer/claims/7/request-info', 'POST').request.body).toEqual({ message: 'Send photos' });
    });

    it('reads and adds notes on the same URL', () => {
      const api = TestBed.inject(OfficerApi);

      api.notes(7).subscribe();
      answer('/api/officer/claims/7/notes', 'GET', []);

      api.addNote(7, 'Called the workshop').subscribe();
      expect(answer('/api/officer/claims/7/notes', 'POST').request.body).toEqual({ content: 'Called the workshop' });
    });
  });

  describe('DashboardApi', () => {
    it('reads the three admin reports', () => {
      const api = TestBed.inject(DashboardApi);

      api.statusSummary().subscribe();
      answer('/api/dashboard/status-summary', 'GET');
      api.officerWorkload().subscribe();
      answer('/api/dashboard/officer-workload', 'GET');
      api.liabilityExposure().subscribe();
      answer('/api/dashboard/liability-exposure', 'GET');
    });
  });

  describe('NotificationApi', () => {
    it('lists with the unreadOnly flag and the page', () => {
      TestBed.inject(NotificationApi).list(true, PAGE).subscribe();

      const { params } = answer('/api/notifications', 'GET').request;
      expect(params.get('unreadOnly')).toBe('true');
      expect(params.get('sort')).toBe('createdAt,desc');
    });

    it('marks one or all as read', () => {
      const api = TestBed.inject(NotificationApi);

      api.markRead(5).subscribe();
      answer('/api/notifications/5/read', 'POST');
      api.markAllRead().subscribe();
      answer('/api/notifications/read-all', 'POST');
    });
  });

  describe('NotificationStore', () => {
    it('takes the unread count from claim-service', () => {
      const store = TestBed.inject(NotificationStore);

      store.refresh();
      answer('/api/notifications/unread-count', 'GET', { unreadCount: 4 });

      expect(store.unreadCount()).toBe(4);
    });

    it('keeps the old number when a refresh fails: a badge is not worth an error', () => {
      const store = TestBed.inject(NotificationStore);
      store.adjust(3);

      store.refresh();
      http.expectOne('/api/notifications/unread-count').flush({}, { status: 503, statusText: 'Unavailable' });

      expect(store.unreadCount()).toBe(3);
    });

    it('adjusts immediately, never below zero, and resets', () => {
      const store = TestBed.inject(NotificationStore);

      store.adjust(2);
      store.adjust(-1);
      expect(store.unreadCount()).toBe(1);

      store.adjust(-5);
      expect(store.unreadCount()).toBe(0);

      store.adjust(2);
      store.reset();
      expect(store.unreadCount()).toBe(0);
    });
  });
});
