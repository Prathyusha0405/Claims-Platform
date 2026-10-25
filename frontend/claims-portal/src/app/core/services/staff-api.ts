import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Assessment, ClaimNote } from '@shared/models/assessment';
import { ClaimSummary, Market } from '@shared/models/claim';
import { Page, PageRequest } from '@shared/models/page';
import { API } from './endpoints';
import { pageParams, toParams } from './http-params';

/**
 * assessment-service's claims staff endpoints. Every call is made with the staff member's own token, which assessment-service
 * forwards to claim-service, so claim-service decides who may do what.
 */
@Injectable({ providedIn: 'root' })
export class StaffApi {
  private readonly http = inject(HttpClient);

  /** The work queue: SUBMITTED claims nobody has taken yet, for one market when `market` is given. */
  unassigned(page: PageRequest, market?: Market): Observable<Page<ClaimSummary>> {
    return this.http.get<Page<ClaimSummary>>(API.staff.unassigned, { params: toParams({ ...pageParams(page), market }) });
  }

  /** Takes the claim for the caller: SUBMITTED → UNDER_REVIEW. 409 when somebody else got there first. */
  assign(id: number): Observable<Assessment> {
    return this.http.post<Assessment>(API.staff.assign(id), {});
  }

  /** UNDER_REVIEW → APPROVED, by the assigned staff member only. The comment is optional. */
  approve(id: number, comment?: string): Observable<Assessment> {
    const text = comment?.trim();
    return this.http.post<Assessment>(API.staff.approve(id), text ? { comment: text } : {});
  }

  /** UNDER_REVIEW → REJECTED, by the assigned staff member only. The claimant sees the reason. */
  reject(id: number, reason: string): Observable<Assessment> {
    return this.http.post<Assessment>(API.staff.reject(id), { reason });
  }

  /** APPROVED or REJECTED → SETTLED, by the assigned staff member only. The optional comment goes in the claim history. */
  settle(id: number, comment?: string): Observable<Assessment> {
    const text = comment?.trim();
    return this.http.post<Assessment>(API.staff.settle(id), text ? { comment: text } : {});
  }

  /** UNDER_REVIEW → MORE_INFO_REQUIRED, by the assigned staff member only. The claimant sees the message. */
  requestInfo(id: number, message: string): Observable<Assessment> {
    return this.http.post<Assessment>(API.staff.requestInfo(id), { message });
  }

  /** The internal notes for a claim, oldest first. */
  notes(id: number): Observable<ClaimNote[]> {
    return this.http.get<ClaimNote[]>(API.staff.notes(id));
  }

  /** Any staff member can note any claim. Notes are never shown to claimants. */
  addNote(id: number, content: string): Observable<ClaimNote> {
    return this.http.post<ClaimNote>(API.staff.notes(id), { content });
  }
}
