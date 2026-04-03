import { getBulkImportRelativePath, summarizeBulkImportSelection } from './bulkImportUtils';

const makeFile = (name: string, webkitRelativePath?: string): File => {
  const file = new File(['content'], name, { type: 'text/plain' });
  if (webkitRelativePath) {
    Object.defineProperty(file, 'webkitRelativePath', {
      value: webkitRelativePath,
      configurable: true,
    });
  }
  return file;
};

describe('bulkImportUtils', () => {
  test('prefers webkitRelativePath when present', () => {
    expect(getBulkImportRelativePath(makeFile('budget.xlsx', 'finance/q1/budget.xlsx'))).toBe(
      'finance/q1/budget.xlsx'
    );
  });

  test('falls back to file name when relative path is missing', () => {
    expect(getBulkImportRelativePath(makeFile('budget.xlsx'))).toBe('budget.xlsx');
  });

  test('summarizes selected files and nested folders', () => {
    const summary = summarizeBulkImportSelection([
      makeFile('budget.xlsx', 'finance/q1/budget.xlsx'),
      makeFile('notes.txt', 'finance/q2/notes.txt'),
      makeFile('readme.md'),
    ]);

    expect(summary).toEqual({
      fileCount: 3,
      folderCount: 3,
    });
  });
});
