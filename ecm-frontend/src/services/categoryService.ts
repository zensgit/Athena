import api from './api';

export interface CategoryTreeNode {
  id: string;
  name: string;
  description?: string;
  path: string;
  level: number;
  children: CategoryTreeNode[];
}

export interface CategoryResponse {
  id: string;
  name: string;
  description?: string;
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

class CategoryService {
  async getCategoryTree(): Promise<CategoryTreeNode[]> {
    return api.get<CategoryTreeNode[]>('/categories/tree');
  }

  async createCategory(data: CreateCategoryRequest): Promise<CategoryResponse> {
    return api.post<CategoryResponse>('/categories', data);
  }

  async updateCategory(categoryId: string, data: UpdateCategoryRequest): Promise<CategoryResponse> {
    return api.put<CategoryResponse>(`/categories/${categoryId}`, data);
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
    return api.get<CategoryResponse[]>(`/nodes/${nodeId}/categories`);
  }
}

const categoryService = new CategoryService();
export default categoryService;

