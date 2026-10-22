import { HttpParams } from '@angular/common/http';

import { PageRequest, sortParam } from '@shared/models/page';

export type ParamValue = string | number | boolean | null | undefined;

/** Query parameters with every missing or blank value left out, so an empty filter is never sent as `?status=`. */
export function toParams(values: Readonly<Record<string, ParamValue>>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined) {
      continue;
    }
    const text = String(value).trim();
    if (text !== '') {
      params = params.set(key, text);
    }
  }
  return params;
}

/** `page`, `size` and `sort` in the form Spring Data reads. */
export const pageParams = (request: PageRequest): Record<string, ParamValue> => ({
  page: request.page,
  size: request.size,
  sort: sortParam(request),
});
