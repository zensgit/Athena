import api from './api';
import categoryService, {
  CategoryResponse,
  CategoryTreeNode,
  CATEGORY_UNEXPECTED_RESPONSE_MESSAGE,
} from './categoryService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const category: CategoryResponse = {
  id: 'cat-1',
  name: 'Contracts',
  description: null,
  path: '/Contracts',
  level: 0,
};

const categoryTree: CategoryTreeNode[] = [
  {
    ...category,
    children: [
      {
        id: 'cat-2',
        name: 'Vendor Contracts',
        description: 'Vendor agreements',
        path: '/Contracts/Vendor Contracts',
        level: 1,
        children: [],
      },
    ],
  },
];

describe('categoryService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('returns guarded category trees', async () => {
    mockedApi.get.mockResolvedValueOnce(categoryTree);

    const result = await categoryService.getCategoryTree();

    expect(result).toEqual(categoryTree);
    expect(mockedApi.get).toHaveBeenCalledWith('/categories/tree');
  });

  test('rejects HTML fallback for category trees', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(categoryService.getCategoryTree()).rejects.toThrow(
      CATEGORY_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('rejects malformed nested category children', async () => {
    mockedApi.get.mockResolvedValueOnce([
      {
        ...categoryTree[0],
        children: [{ ...categoryTree[0].children[0], level: '1' }],
      },
    ]);

    await expect(categoryService.getCategoryTree()).rejects.toThrow(
      CATEGORY_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('returns guarded create and update responses', async () => {
    mockedApi.post.mockResolvedValueOnce(category);
    mockedApi.put.mockResolvedValueOnce({ ...category, description: 'Updated description' });

    await expect(
      categoryService.createCategory({
        name: 'Contracts',
        description: 'Contract categories',
        parentId: null,
      })
    ).resolves.toEqual(category);
    await expect(
      categoryService.updateCategory('cat-1', { name: 'Contracts', description: 'Updated description' })
    ).resolves.toEqual({ ...category, description: 'Updated description' });

    expect(mockedApi.post).toHaveBeenCalledWith('/categories', {
      name: 'Contracts',
      description: 'Contract categories',
      parentId: null,
    });
    expect(mockedApi.put).toHaveBeenCalledWith('/categories/cat-1', {
      name: 'Contracts',
      description: 'Updated description',
    });
  });

  test('rejects malformed mutation responses', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...category, path: null });

    await expect(categoryService.createCategory({ name: 'Contracts' })).rejects.toThrow(
      CATEGORY_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('keeps void mutation endpoint wiring unchanged', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);
    mockedApi.post.mockResolvedValueOnce(undefined);

    await categoryService.deleteCategory('cat-1', true);
    await categoryService.addCategoryToNode('node-1', 'cat-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/categories/cat-1', {
      params: { deleteChildren: true },
    });
    expect(mockedApi.post).toHaveBeenCalledWith('/nodes/node-1/categories', { categoryId: 'cat-1' });
  });

  test('removes categories from nodes', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await categoryService.removeCategoryFromNode('node-1', 'cat-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/nodes/node-1/categories/cat-1');
  });

  test('returns guarded node category lists', async () => {
    mockedApi.get.mockResolvedValueOnce([category]);

    const result = await categoryService.getNodeCategories('node-1');

    expect(result).toEqual([category]);
    expect(mockedApi.get).toHaveBeenCalledWith('/nodes/node-1/categories');
  });

  test('rejects HTML fallback for node category lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(categoryService.getNodeCategories('node-1')).rejects.toThrow(
      CATEGORY_UNEXPECTED_RESPONSE_MESSAGE
    );
  });
});
