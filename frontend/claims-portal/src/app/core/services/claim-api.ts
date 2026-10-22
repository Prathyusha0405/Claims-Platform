import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ClaimDetail, ClaimSearchCriteria, ClaimSummary, CreateClaimRequest } from '@shared/models/claim';
import { Page, PageRequest } from '@shared/models/page';
import { API } from './endpoints';
import { pageParams, toParams } from './http-params';

/** claim-service over HTTP, nothing more. */
@Injectable({ providedIn: 'root' })
export class ClaimApi {
  private readonly http = inject(HttpClient);

  /** CLAIMANT only. The claim starts as SUBMITTED and comes back with its claim number (201). */
  create(request: CreateClaimRequest): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(API.claims.create, request);
  }

  /** CLAIMANT only: the caller's own claims. */
  myClaims(page: PageRequest): Observable<Page<ClaimSummary>> {
    return this.http.get<Page<ClaimSummary>>(API.claims.myClaims, { params: toParams(pageParams(page)) });
  }

  /** Claimants only ever find their own claims; officers and admins search all of them. */
  search(criteria: ClaimSearchCriteria, page: PageRequest): Observable<Page<ClaimSummary>> {
    return this.http.get<Page<ClaimSummary>>(API.claims.search, {
      params: toParams({ ...criteria, ...pageParams(page) }),
    });
  }

  /** The claim with its history. 404 when it does not exist or belongs to another claimant. */
  get(claimNumber: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(API.claims.byNumber(claimNumber));
  }

  /** CLAIMANT only: the answer to an officer's request; moves the claim back to UNDER_REVIEW. */
  submitAdditionalInfo(id: number, message: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(API.claims.additionalInfo(id), { message });
  }
}
