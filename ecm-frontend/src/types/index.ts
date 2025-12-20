export interface User {
  id: string;
  username: string;
  email: string;
  roles: string[];
  firstName?: string;
  lastName?: string;
  enabled?: boolean;
  locked?: boolean;
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
  description?: string;
  highlights?: Record<string, string[]>;
  tags?: string[];
  categories?: string[];
  correspondent?: string;
  correspondentId?: string;
  score?: number;
  inheritPermissions?: boolean;
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
  authority: string;
  authorityType: 'USER' | 'GROUP' | 'ROLE' | 'EVERYONE';
  permission: PermissionType;
  allowed: boolean;
  inherited?: boolean;
  expiryDate?: string;
  notes?: string;
}

export type PermissionType = 
  | 'READ'
  | 'WRITE'
  | 'DELETE'
  | 'CREATE_CHILDREN'
  | 'DELETE_CHILDREN'
  | 'EXECUTE'
  | 'CHANGE_PERMISSIONS'
  | 'TAKE_OWNERSHIP'
  | 'CHECKOUT'
  | 'CHECKIN'
  | 'CANCEL_CHECKOUT'
  | 'APPROVE'
  | 'REJECT';

export interface SearchCriteria {
  name?: string;
  properties?: Record<string, any>;
  aspects?: string[];
  contentType?: string;
  mimeTypes?: string[];
  createdBy?: string;
  createdByList?: string[];
  createdFrom?: string;
  createdTo?: string;
  modifiedFrom?: string;
  modifiedTo?: string;
  tags?: string[];
  categories?: string[];
  correspondents?: string[];
  minSize?: number;
  maxSize?: number;
  path?: string;
  page?: number;
  size?: number;
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
  nodesTotal: number;
  searchFacets?: Record<string, { value: string; count: number }[]>;
  lastSearchCriteria?: SearchCriteria;
  loading: boolean;
  error: string | null;
  selectedNodes: string[];
}
