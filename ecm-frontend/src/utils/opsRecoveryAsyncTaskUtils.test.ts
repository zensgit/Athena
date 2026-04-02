import {
  formatRecoveryHistoryExportAsyncRequestDetails,
  formatRecoveryHistoryExportAsyncRequestPrimary,
} from './opsRecoveryAsyncTaskUtils';

describe('opsRecoveryAsyncTaskUtils', () => {
  it('formats primary request summary for standard history exports', () => {
    expect(formatRecoveryHistoryExportAsyncRequestPrimary({
      exportType: 'HISTORY',
      days: 7,
      limit: 500,
    })).toBe('HISTORY · 7d · 500 rows');
  });

  it('formats compare-specific limits for compare exports', () => {
    expect(formatRecoveryHistoryExportAsyncRequestPrimary({
      exportType: 'HISTORY_COMPARE_BREAKDOWN',
      days: 14,
      limit: 500,
      compareBreakdownLimit: 25,
    })).toBe('HISTORY_COMPARE_BREAKDOWN · 14d · 500 rows · breakdown 25');

    expect(formatRecoveryHistoryExportAsyncRequestPrimary({
      exportType: 'HISTORY_COMPARE_ACTORS',
      days: 30,
      limit: 200,
      compareActorLimit: 12,
    })).toBe('HISTORY_COMPARE_ACTORS · 30d · 200 rows · actors 12');
  });

  it('formats request details when filters are present', () => {
    expect(formatRecoveryHistoryExportAsyncRequestDetails({
      mode: 'DRY_RUN',
      actor: 'ops-admin',
      eventType: 'OPS_RECOVERY_DRY_RUN',
      compareBreakdownSort: 'DELTA_ABS_DESC',
    })).toBe('mode=DRY_RUN · actor=ops-admin · event=OPS_RECOVERY_DRY_RUN · breakdownSort=DELTA_ABS_DESC');
  });

  it('returns null details when request has no extra filters', () => {
    expect(formatRecoveryHistoryExportAsyncRequestDetails({
      exportType: 'HISTORY',
      days: 7,
      limit: 500,
    })).toBeNull();
  });
});
