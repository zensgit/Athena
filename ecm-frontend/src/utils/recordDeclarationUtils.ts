import { Node, RecordDeclaration } from 'types';

const RECORD_ASPECT = 'rm:record';
const DECLARED_BY_PROPERTY = 'rm:declaredBy';
const DECLARED_AT_PROPERTY = 'rm:declaredAt';
const DECLARED_VERSION_LABEL_PROPERTY = 'rm:declaredVersionLabel';
const DECLARATION_COMMENT_PROPERTY = 'rm:declarationComment';
const RECORD_CATEGORY_ID_PROPERTY = 'rm:recordCategoryId';
const RECORD_CATEGORY_NAME_PROPERTY = 'rm:recordCategoryName';
const RECORD_CATEGORY_PATH_PROPERTY = 'rm:recordCategoryPath';

type RecordProjectionLike = Pick<Node, 'id' | 'name' | 'path'> & Partial<Pick<
  Node,
  | 'properties'
  | 'aspects'
  | 'currentVersionLabel'
  | 'record'
  | 'declaredBy'
  | 'declaredAt'
  | 'declaredVersionLabel'
  | 'declarationComment'
  | 'recordCategoryId'
  | 'recordCategoryName'
  | 'recordCategoryPath'
>>;

const asString = (value: unknown): string | undefined => {
  if (typeof value !== 'string') {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
};

export const getRecordDeclarationFromNode = (node?: RecordProjectionLike | null): RecordDeclaration | null => {
  if (!node) {
    return null;
  }

  const properties = node.properties || {};
  const isRecord = Boolean(
    node.record
      || node.aspects?.includes(RECORD_ASPECT)
      || properties[DECLARED_AT_PROPERTY]
      || properties[DECLARED_BY_PROPERTY]
      || properties[DECLARED_VERSION_LABEL_PROPERTY]
      || properties[DECLARATION_COMMENT_PROPERTY]
      || properties[RECORD_CATEGORY_ID_PROPERTY]
      || properties[RECORD_CATEGORY_NAME_PROPERTY]
      || properties[RECORD_CATEGORY_PATH_PROPERTY]
  );

  if (!isRecord) {
    return null;
  }

  return {
    nodeId: node.id,
    name: node.name,
    path: node.path,
    currentVersionLabel: node.currentVersionLabel,
    declaredVersionLabel: node.declaredVersionLabel || asString(properties[DECLARED_VERSION_LABEL_PROPERTY]),
    declaredBy: node.declaredBy || asString(properties[DECLARED_BY_PROPERTY]),
    declaredAt: node.declaredAt || asString(properties[DECLARED_AT_PROPERTY]),
    declarationComment: node.declarationComment || asString(properties[DECLARATION_COMMENT_PROPERTY]),
    recordCategoryId: node.recordCategoryId || asString(properties[RECORD_CATEGORY_ID_PROPERTY]),
    recordCategoryName: node.recordCategoryName || asString(properties[RECORD_CATEGORY_NAME_PROPERTY]),
    recordCategoryPath: node.recordCategoryPath || asString(properties[RECORD_CATEGORY_PATH_PROPERTY]),
  };
};
