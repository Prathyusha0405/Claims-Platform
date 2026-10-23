import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Market } from '@shared/models/claim';
import { LiabilityExposure, StaffWorkloadReport, StatusSummary } from '@shared/models/dashboard';
import { API } from './endpoints';
import { toParams } from './http-params';

/**
 * assessment-service's management dashboards (MANAGER only). The figures are computed live on every call. Each report
 * can be limited to one market; leave `market` out for all of them.
 */
@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);

  statusSummary(market?: Market): Observable<StatusSummary> {
    return this.http.get<StatusSummary>(API.dashboard.statusSummary, { params: toParams({ market }) });
  }

  staffWorkload(market?: Market): Observable<StaffWorkloadReport> {
    return this.http.get<StaffWorkloadReport>(API.dashboard.staffWorkload, { params: toParams({ market }) });
  }

  liabilityExposure(market?: Market): Observable<LiabilityExposure> {
    return this.http.get<LiabilityExposure>(API.dashboard.liabilityExposure, { params: toParams({ market }) });
  }
}
