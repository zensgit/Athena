export type BulkImportSelectionFile = File & {
  webkitRelativePath?: string;
};

export interface BulkImportSelectionSummary {
  fileCount: number;
  folderCount: number;
}

export const getBulkImportRelativePath = (file: BulkImportSelectionFile): string => {
  const candidate = typeof file.webkitRelativePath === 'string' ? file.webkitRelativePath.trim() : '';
  return candidate || file.name;
};

export const summarizeBulkImportSelection = (
  files: BulkImportSelectionFile[]
): BulkImportSelectionSummary => {
  const folders = new Set<string>();
  files.forEach((file) => {
    const relativePath = getBulkImportRelativePath(file);
    const segments = relativePath.split('/').filter(Boolean);
    segments.slice(0, -1).reduce((currentPath, segment) => {
      const nextPath = currentPath ? `${currentPath}/${segment}` : segment;
      folders.add(nextPath);
      return nextPath;
    }, '');
  });

  return {
    fileCount: files.length,
    folderCount: folders.size,
  };
};
