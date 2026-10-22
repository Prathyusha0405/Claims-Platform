import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Assessment, ClaimNote } from '@shared/models/assessment';
import { ClaimSummary } from '@shared/models/claim';
import { Page, PageRequest } from '@shared/models/page';
import { API } from './endpoints';
import { pageParams, toParams } from './http-params';

/**
 * assessment-service's officer endpoints. Every call is made with the officer's own token, which assessment-service
 * forwards to claim-service, so claim-service decides who may do what.
 */
@Injectable({ providedIn: 'root' })
export class OfficerApi {
  private readonly http = inject(HttpClient);

  /** The work queue: SUBMITTED claims nobody has taken yet. */
  unassigned(page: PageRequest): Observable<Page<ClaimSummary>> {
    return this.http.get<Page<ClaimSummary>>(API.officer.unassigned, { params: toParams(pageParams(page)) });
  }

  /** Takes the claim for the caller: SUBMITTED → UNDER_REVIEW. 409 when somebody else got there first. */
  assign(id: number): Observable<Assessment> {
    return this.http.post<Assessment>(API.officer.assign(id), {});
  }

  /** UNDER_REVIEW → APPROVED, by the assigned officer only. The comment is optional. */
  approve(id: number, comment?: string): Observable<Assessment> {
    const text = comment?.trim();
    return this.http.post<Assessment>(API.officer.approve(id), text ? { comment: text } : {});
  }

  /** UNDER_REVIEW → REJECTED, by the assigned officer only. The claimant sees the reason. */
  reject(id: number, reason: string): Observable<Assessment> {
    return this.http.post<Assessment>(API.officer.reject(id), { reason });
  }

  /** UNDER_REVIEW → MORE_INFO_REQUIRED, by the assigned officer only. The claimant sees the message. */
  requestInfo(id: number, message: string): Observable<Assessment> {
    return this.http.post<Assessment>(API.officer.requestInfo(id), { message });
  }

  /** The internal notes for a claim, oldest first. */
  notes(id: number): Observable<ClaimNote[]> {
    return this.http.get<ClaimNote[]>(API.officer.notes(id));
  }

  /** Any officer can note any claim. Notes are never shown to claimants. */
  addNote(id: number, content: string): Observable<ClaimNote> {
    return this.http.post<ClaimNote>(API.officer.notes(id), { content });
  }
}
