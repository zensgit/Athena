import { Node } from 'types';
import { getRecordDeclarationFromNode } from './recordDeclarationUtils';

describe('recordDeclarationUtils', () => {
  it('returns null for non-record nodes', () => {
    const node: Node = {
      id: 'doc-1',
      name: 'Plain document',
      path: '/Sites/Finance/Plain document.pdf',
      nodeType: 'DOCUMENT',
      properties: {},
      aspects: [],
      created: '2026-04-17T10:00:00Z',
      modified: '2026-04-17T10:00:00Z',
      creator: 'alice',
      modifier: 'alice',
    };

    expect(getRecordDeclarationFromNode(node)).toBeNull();
  });

  it('builds a declaration from explicit node projection fields', () => {
    const node: Node = {
      id: 'doc-2',
      name: 'Contract.pdf',
      path: '/Sites/Legal/Contract.pdf',
      nodeType: 'DOCUMENT',
      properties: {},
      aspects: [],
      created: '2026-04-17T10:00:00Z',
      modified: '2026-04-17T10:00:00Z',
      creator: 'admin',
      modifier: 'admin',
      currentVersionLabel: '1.3',
      record: true,
      declaredBy: 'records-admin',
      declaredAt: '2026-04-10T08:30:00',
      declaredVersionLabel: '1.2',
      declarationComment: 'Final retention copy',
      recordCategoryId: 'cat-1',
      recordCategoryName: 'Contracts',
      recordCategoryPath: '/Records Management/Contracts',
    };

    expect(getRecordDeclarationFromNode(node)).toEqual({
      nodeId: 'doc-2',
      name: 'Contract.pdf',
      path: '/Sites/Legal/Contract.pdf',
      currentVersionLabel: '1.3',
      declaredBy: 'records-admin',
      declaredAt: '2026-04-10T08:30:00',
      declaredVersionLabel: '1.2',
      declarationComment: 'Final retention copy',
      recordCategoryId: 'cat-1',
      recordCategoryName: 'Contracts',
      recordCategoryPath: '/Records Management/Contracts',
    });
  });

  it('builds a declaration from rm aspect and properties', () => {
    const node: Node = {
      id: 'doc-3',
      name: 'Invoice.pdf',
      path: '/Sites/Finance/Invoice.pdf',
      nodeType: 'DOCUMENT',
      properties: {
        'rm:declaredBy': 'records-admin',
        'rm:declaredAt': '2026-04-11T09:00:00',
        'rm:declaredVersionLabel': '2.0',
        'rm:declarationComment': 'Quarter close',
        'rm:recordCategoryId': 'cat-2',
        'rm:recordCategoryName': 'Finance',
        'rm:recordCategoryPath': '/Records Management/Finance',
      },
      aspects: ['rm:record'],
      created: '2026-04-17T10:00:00Z',
      modified: '2026-04-17T10:00:00Z',
      creator: 'alice',
      modifier: 'alice',
    };

    const declaration = getRecordDeclarationFromNode(node);
    expect(declaration?.declaredBy).toBe('records-admin');
    expect(declaration?.declaredVersionLabel).toBe('2.0');
    expect(declaration?.recordCategoryPath).toBe('/Records Management/Finance');
  });
});
