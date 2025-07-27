export interface User {
  id: string;
  username: string;
  email: string;
  roles: string[];
  firstName?: string;
  lastName?: string;
}

export interface Node {
  id: string;
  name: string;
  nodeType: 'FOLDER' | 'DOCUMENT';
  parentId?: string;
  properties: Record<string, any>;
  aspects: string[];
  created: string;
  modified: string;
  creator: string;
  modifier: string;
  path: string;
  contentType?: string;
  size?: number;
  currentVersionLabel?: string;
}

export interface Version {
  id: string;
  documentId: string;
  versionLabel: string;
  comment?: string;
  created: string;
  creator: string;
  size: number;
  isMajor: boolean;
}

export interface Permission {
  principal: string;
  permission: PermissionType;
  allowed: boolean;
}

export type PermissionType = 
  | 'READ'
  | 'WRITE'
  | 'DELETE'
  | 'ADD_CHILDREN'
  | 'READ_PERMISSIONS'
  | 'WRITE_PERMISSIONS'
  | 'EXECUTE';

export interface SearchCriteria {
  name?: string;
  properties?: Record<string, any>;
  aspects?: string[];
  contentType?: string;
  createdFrom?: string;
  createdTo?: string;
  modifiedFrom?: string;
  modifiedTo?: string;
}

export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

export interface NodeState {
  currentNode: Node | null;
  nodes: Node[];
  loading: boolean;
  error: string | null;
  selectedNodes: string[];
}