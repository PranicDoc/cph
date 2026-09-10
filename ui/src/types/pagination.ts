/**
 * Wire mirror of the backend `com.pranicdoc.cph.common.dto.PageResponse<T>` record.
 *
 * The record's component names ARE the JSON keys, so the current-page field is
 * `page` (populated from `springPage.getNumber()`) — not `number`.
 *
 * Do NOT copy the local `PageResponse<T>` interface in
 * `hooks/admin/useAdminAccounts.ts`: it names the field `number` and omits
 * `last`, which is wrong for the `PageResponse` envelope. Every Phase 38 screen
 * reads pagination state off THIS type instead.
 */
export interface SpringPageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
