import api from './api';
import tagService, {
  Tag,
  TagCloudItem,
  TAG_UNEXPECTED_RESPONSE_MESSAGE,
} from './tagService';

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

const tag: Tag = {
  id: 'tag-1',
  name: 'contract',
  description: null,
  color: '#1976d2',
  usageCount: 3,
  created: '2026-05-14T00:00:00Z',
  creator: 'admin',
};

const cloudItem: TagCloudItem = {
  name: 'contract',
  count: 3,
  color: '#1976d2',
};

describe('tagService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test.each([
    ['getAllTags', () => tagService.getAllTags(), ['/tags']],
    ['searchTags', () => tagService.searchTags('contract'), ['/tags/search', { params: { q: 'contract' } }]],
    ['getPopularTags', () => tagService.getPopularTags(5), ['/tags/popular', { params: { limit: 5 } }]],
    ['getNodeTags', () => tagService.getNodeTags('node-1'), ['/nodes/node-1/tags']],
  ] as const)('returns guarded tag lists from %s', async (_name, action, expectedCall) => {
    mockedApi.get.mockResolvedValueOnce([tag]);

    await expect(action()).resolves.toEqual([tag]);
    expect(mockedApi.get).toHaveBeenCalledWith(...expectedCall);
  });

  test('rejects HTML fallback for tag lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(tagService.getAllTags()).rejects.toThrow(TAG_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test('rejects malformed tag list items', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...tag, usageCount: '3' }]);

    await expect(tagService.getAllTags()).rejects.toThrow(TAG_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test('returns guarded create and update responses', async () => {
    mockedApi.post.mockResolvedValueOnce(tag);
    mockedApi.put.mockResolvedValueOnce({ ...tag, description: 'Updated tag' });

    await expect(
      tagService.createTag({
        name: 'contract',
        description: 'Contract tag',
        color: '#1976d2',
      })
    ).resolves.toEqual(tag);
    await expect(
      tagService.updateTag('tag-1', {
        name: 'contract',
        description: 'Updated tag',
        color: '#1976d2',
      })
    ).resolves.toEqual({ ...tag, description: 'Updated tag' });

    expect(mockedApi.post).toHaveBeenCalledWith('/tags', {
      name: 'contract',
      description: 'Contract tag',
      color: '#1976d2',
    });
    expect(mockedApi.put).toHaveBeenCalledWith('/tags/tag-1', {
      name: 'contract',
      description: 'Updated tag',
      color: '#1976d2',
    });
  });

  test('rejects malformed mutation responses', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...tag, color: null });

    await expect(tagService.createTag({ name: 'contract' })).rejects.toThrow(
      TAG_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('returns guarded tag cloud responses', async () => {
    mockedApi.get.mockResolvedValueOnce([cloudItem]);

    const result = await tagService.getTagCloud();

    expect(result).toEqual([cloudItem]);
    expect(mockedApi.get).toHaveBeenCalledWith('/tags/cloud');
  });

  test('rejects malformed tag cloud responses', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...cloudItem, count: '3' }]);

    await expect(tagService.getTagCloud()).rejects.toThrow(TAG_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test('keeps void mutation endpoint wiring unchanged', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);
    mockedApi.post.mockResolvedValueOnce(undefined);
    mockedApi.post.mockResolvedValueOnce(undefined);
    mockedApi.post.mockResolvedValueOnce(undefined);

    await tagService.deleteTag('tag-1');
    await tagService.mergeTags('source-tag', 'target-tag');
    await tagService.addTagToNode('node-1', 'contract');
    await tagService.addTagsToNode('node-1', ['contract', 'legal']);

    expect(mockedApi.delete).toHaveBeenCalledWith('/tags/tag-1');
    expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/tags/source-tag/merge', {
      targetTagId: 'target-tag',
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(2, '/nodes/node-1/tags', { tagName: 'contract' });
    expect(mockedApi.post).toHaveBeenNthCalledWith(3, '/nodes/node-1/tags/batch', {
      tagNames: ['contract', 'legal'],
    });
  });

  test('removes tags from nodes', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await tagService.removeTagFromNode('node-1', 'contract');

    expect(mockedApi.delete).toHaveBeenCalledWith('/nodes/node-1/tags/contract');
  });
});
