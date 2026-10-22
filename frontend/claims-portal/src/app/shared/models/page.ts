/** `PageResponse<T>` from claim-service and assessment-service: zero-based pages. */
export interface Page<T> {
  content: T[];
  /** Zero-based page index. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export type SortDirection = 'asc' | 'desc';

/** Which page of a list to fetch and how to order it. The backend rejects sort fields it does not allow with 400. */
export interface PageRequest {
  page: number;
  size: number;
  sortField: string;
  sortDirection: SortDirection;
}

export const PAGE_SIZE_OPTIONS: readonly number[] = [10, 20, 50, 100];
export const DEFAULT_PAGE_SIZE = 20;

/** The `sort=field,direction` query value Spring Data expects. */
export const sortParam = (request: Pick<PageRequest, 'sortField' | 'sortDirection'>): string =>
  `${request.sortField},${request.sortDirection}`;
