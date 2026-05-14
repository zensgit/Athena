import api from './api';

export const CATEGORY_UNEXPECTED_RESPONSE_MESSAGE =
  'Category endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

export interface CategoryTreeNode {
  id: string;
  name: string;
  description?: string | null;
  path: string;
  level: number;
  children: CategoryTreeNode[];
}

export interface CategoryResponse {
  id: string;
  name: string;
  description?: string | null;
  path: string;
  level: number;
}

interface CreateCategoryRequest {
  name: string;
  description?: string;
  parentId?: string | null;
}

interface UpdateCategoryRequest {
  name: string;
  description?: string;
}

const assertUnexpectedResponse = (): never => {
  throw new Error(CATEGORY_UNEXPECTED_RESPONSE_MESSAGE);
};

const isCategoryResponse = (value: unknown): value is CategoryResponse => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && isStringOrNullish(value.description)
    && typeof value.path === 'string'
    && isNumber(value.level);
};

const assertCategoryResponse = (value: unknown): CategoryResponse => (
  isCategoryResponse(value) ? value : assertUnexpectedResponse()
);

const isCategoryTreeNode = (value: unknown): value is CategoryTreeNode => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && isStringOrNullish(value.description)
    && typeof value.path === 'string'
    && isNumber(value.level)
    && Array.isArray(value.children)
    && value.children.every(isCategoryTreeNode);
};

const assertCategoryTree = (value: unknown): CategoryTreeNode[] => {
  if (!Array.isArray(value) || !value.every(isCategoryTreeNode)) {
    throw new Error(CATEGORY_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

const assertCategoryList = (value: unknown): CategoryResponse[] => {
  if (!Array.isArray(value) || !value.every(isCategoryResponse)) {
    throw new Error(CATEGORY_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

class CategoryService {
  async getCategoryTree(): Promise<CategoryTreeNode[]> {
    const result = await api.get<unknown>('/categories/tree');
    return assertCategoryTree(result);
  }

  async createCategory(data: CreateCategoryRequest): Promise<CategoryResponse> {
    const result = await api.post<unknown>('/categories', data);
    return assertCategoryResponse(result);
  }

  async updateCategory(categoryId: string, data: UpdateCategoryRequest): Promise<CategoryResponse> {
    const result = await api.put<unknown>(`/categories/${categoryId}`, data);
    return assertCategoryResponse(result);
  }

  async deleteCategory(categoryId: string, deleteChildren = false): Promise<void> {
    return api.delete<void>(`/categories/${categoryId}`, {
      params: { deleteChildren },
    });
  }

  async addCategoryToNode(nodeId: string, categoryId: string): Promise<void> {
    return api.post<void>(`/nodes/${nodeId}/categories`, { categoryId });
  }

  async removeCategoryFromNode(nodeId: string, categoryId: string): Promise<void> {
    return api.delete<void>(`/nodes/${nodeId}/categories/${categoryId}`);
  }

  async getNodeCategories(nodeId: string): Promise<CategoryResponse[]> {
    const result = await api.get<unknown>(`/nodes/${nodeId}/categories`);
    return assertCategoryList(result);
  }
}

const categoryService = new CategoryService();
export default categoryService;
