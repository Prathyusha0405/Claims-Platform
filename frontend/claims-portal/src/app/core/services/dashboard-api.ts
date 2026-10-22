import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { LiabilityExposure, OfficerWorkloadReport, StatusSummary } from '@shared/models/dashboard';
import { API } from './endpoints';

/** assessment-service's management dashboards (ADMIN only). The figures are computed live on every call. */
@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);

  statusSummary(): Observable<StatusSummary> {
    return this.http.get<StatusSummary>(API.dashboard.statusSummary);
  }

  officerWorkload(): Observable<OfficerWorkloadReport> {
    return this.http.get<OfficerWorkloadReport>(API.dashboard.officerWorkload);
  }

  liabilityExposure(): Observable<LiabilityExposure> {
    return this.http.get<LiabilityExposure>(API.dashboard.liabilityExposure);
  }
}
