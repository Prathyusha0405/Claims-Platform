import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ClaimsOverTime, DashboardFilters, LiabilityExposure, StaffWorkloadReport, StatusSummary } from '@shared/models/dashboard';
import { API } from './endpoints';
import { toParams } from './http-params';

/**
 * assessment-service's management dashboards (MANAGER only). The figures are computed live on every call. Every report
 * takes the same optional filters (market, claim type, and a submission date range); a field left out, or blank, is
 * not sent and leaves that dimension open.
 */
@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);

  statusSummary(filters: DashboardFilters = {}): Observable<StatusSummary> {
    return this.http.get<StatusSummary>(API.dashboard.statusSummary, { params: toParams({ ...filters }) });
  }

  staffWorkload(filters: DashboardFilters = {}): Observable<StaffWorkloadReport> {
    return this.http.get<StaffWorkloadReport>(API.dashboard.staffWorkload, { params: toParams({ ...filters }) });
  }

  liabilityExposure(filters: DashboardFilters = {}): Observable<LiabilityExposure> {
    return this.http.get<LiabilityExposure>(API.dashboard.liabilityExposure, { params: toParams({ ...filters }) });
  }

  claimsOverTime(filters: DashboardFilters = {}): Observable<ClaimsOverTime> {
    return this.http.get<ClaimsOverTime>(API.dashboard.claimsOverTime, { params: toParams({ ...filters }) });
  }
}
