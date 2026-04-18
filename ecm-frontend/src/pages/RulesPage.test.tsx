import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'react-toastify';
import RulesPage from './RulesPage';
import ruleService from 'services/ruleService';

jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    success: jest.fn(),
    info: jest.fn(),
  },
}));

jest.mock('services/ruleService', () => ({
  __esModule: true,
  default: {
    getAllRules: jest.fn(),
    getTemplates: jest.fn(),
    getStats: jest.fn(),
    getActionDefinitions: jest.fn(),
    listRuleExecutionTimeline: jest.fn(),
    listRuleAuditTimeline: jest.fn(),
    validateCronExpression: jest.fn(),
    updateRule: jest.fn(),
    createRule: jest.fn(),
    validateCondition: jest.fn(),
    setEnabled: jest.fn(),
    deleteRule: jest.fn(),
    testRule: jest.fn(),
    getScopeFolderRules: jest.fn(),
    reorderScopeFolderRules: jest.fn(),
    dryRunScopeFolderRules: jest.fn(),
    exportRuleExecutionTimelineCsv: jest.fn(),
    exportRuleAuditTimelineCsv: jest.fn(),
    executeRuleManually: jest.fn(),
    triggerScheduledRule: jest.fn(),
  },
}));

const mockedRuleService = ruleService as jest.Mocked<typeof ruleService>;
const toastErrorMock = toast.error as jest.Mock;
const toastSuccessMock = toast.success as jest.Mock;

const scheduledRule = {
  id: 'rule-1',
  name: 'Scheduled Review',
  description: 'Scheduled content review',
  triggerType: 'SCHEDULED' as const,
  condition: { type: 'ALWAYS_TRUE' },
  actions: [{ type: 'ADD_TAG', params: { tagName: 'review' } }],
  priority: 100,
  enabled: true,
  owner: 'admin',
  cronExpression: '0 */15 * * * *',
  timezone: 'UTC',
  maxItemsPerRun: 25,
  manualBackfillMinutes: 30,
};

describe('RulesPage scheduled rule hardening', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedRuleService.getAllRules.mockResolvedValue([scheduledRule]);
    mockedRuleService.getTemplates.mockResolvedValue([]);
    mockedRuleService.getStats.mockResolvedValue({
      totalRules: 1,
      enabledRules: 1,
      disabledRules: 0,
      totalExecutions: 0,
      totalFailures: 0,
      successRate: 100,
    });
    mockedRuleService.getActionDefinitions.mockResolvedValue([]);
    mockedRuleService.listRuleExecutionTimeline.mockResolvedValue([]);
    mockedRuleService.listRuleAuditTimeline.mockResolvedValue([]);
    mockedRuleService.validateCondition.mockResolvedValue({ valid: true, message: 'ok' });
    mockedRuleService.updateRule.mockResolvedValue(scheduledRule as any);
    mockedRuleService.createRule.mockResolvedValue(scheduledRule as any);
    mockedRuleService.validateCronExpression.mockResolvedValue({
      valid: false,
      error: 'Scheduled rules must run at least 5 minutes apart',
    });
  });

  it('blocks scheduled save when max items per run is invalid', async () => {
    render(<RulesPage />);

    const editButtons = await screen.findAllByLabelText('Edit');
    fireEvent.click(editButtons[0]);

    const maxItemsInput = await screen.findByLabelText('Max Items Per Run');
    fireEvent.change(maxItemsInput, { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(toastErrorMock).toHaveBeenCalledWith('Max items per run must be at least 1');
    expect(mockedRuleService.updateRule).not.toHaveBeenCalled();
  });

  it('clears scheduled payload when trigger switches away from scheduled', async () => {
    render(<RulesPage />);

    const editButtons = await screen.findAllByLabelText('Edit');
    fireEvent.click(editButtons[0]);

    const dialog = await screen.findByRole('dialog');
    const triggerSelect = within(dialog).getByRole('combobox', { name: 'Trigger' });
    fireEvent.mouseDown(triggerSelect);
    const listbox = await screen.findByRole('listbox');
    fireEvent.click(within(listbox).getByText('DOCUMENT_UPDATED'));

    expect(screen.queryByLabelText('Cron Expression')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      expect(mockedRuleService.updateRule).toHaveBeenCalled();
    });
    expect(mockedRuleService.updateRule).toHaveBeenCalledWith(
      'rule-1',
      expect.objectContaining({
        triggerType: 'DOCUMENT_UPDATED',
        cronExpression: undefined,
        timezone: undefined,
        maxItemsPerRun: undefined,
        manualBackfillMinutes: undefined,
      })
    );
    expect(toastSuccessMock).toHaveBeenCalledWith('Rule updated');
  });
});
