import api from './api';
import blogService, {
  BLOG_UNEXPECTED_RESPONSE_MESSAGE,
  BlogPostDto,
  BlogPostPage,
} from './blogService';

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

const SITE_ID = 'site-1';
const POST_ID = 'post-1';

const post: BlogPostDto = {
  id: POST_ID,
  siteId: SITE_ID,
  title: 'Welcome',
  content: 'Hello, world',
  status: 'PUBLISHED',
  publishedDate: '2026-05-15T09:00:00',
  tags: ['intro', 'news'],
  createdBy: 'admin',
  createdDate: '2026-05-14T12:00:00',
};

const draftPost: BlogPostDto = {
  id: 'post-2',
  siteId: SITE_ID,
  title: 'In progress',
  content: null,
  status: 'DRAFT',
  publishedDate: null,
  tags: [],
  createdBy: 'editor',
  createdDate: '2026-05-15T10:00:00',
};

const page = (content: BlogPostDto[] = [post]): BlogPostPage => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 20,
});

describe('blogService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('listPosts', () => {
    it('forwards default paging and returns a guarded page', async () => {
      const response = page([post, draftPost]);
      mockedApi.get.mockResolvedValueOnce(response);

      await expect(blogService.listPosts(SITE_ID)).resolves.toEqual(response);
      expect(mockedApi.get).toHaveBeenCalledWith(
        `/sites/${SITE_ID}/blog/posts`,
        { params: { page: 0, size: 20 } },
      );
    });

    it('forwards status filter when provided', async () => {
      mockedApi.get.mockResolvedValueOnce(page());

      await blogService.listPosts(SITE_ID, 2, 5, 'PUBLISHED');
      expect(mockedApi.get).toHaveBeenCalledWith(
        `/sites/${SITE_ID}/blog/posts`,
        { params: { page: 2, size: 5, status: 'PUBLISHED' } },
      );
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(blogService.listPosts(SITE_ID)).rejects.toThrow(
        BLOG_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a page entry whose status is outside the closed union', async () => {
      mockedApi.get.mockResolvedValueOnce(page([
        { ...post, status: 'ARCHIVED' as BlogPostDto['status'] },
      ]));

      await expect(blogService.listPosts(SITE_ID)).rejects.toThrow(
        BLOG_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a page entry whose tags contain non-string values', async () => {
      mockedApi.get.mockResolvedValueOnce(page([
        { ...post, tags: ['valid', 42 as unknown as string] },
      ]));

      await expect(blogService.listPosts(SITE_ID)).rejects.toThrow(
        BLOG_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a page envelope whose totalPages is a string', async () => {
      mockedApi.get.mockResolvedValueOnce({ ...page(), totalPages: '1' });

      await expect(blogService.listPosts(SITE_ID)).rejects.toThrow(
        BLOG_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('listDrafts', () => {
    it('forwards paging and returns a guarded page', async () => {
      mockedApi.get.mockResolvedValueOnce(page([draftPost]));

      await expect(blogService.listDrafts(SITE_ID, 1, 10)).resolves.toEqual(page([draftPost]));
      expect(mockedApi.get).toHaveBeenCalledWith(
        `/sites/${SITE_ID}/blog/posts/drafts`,
        { params: { page: 1, size: 10 } },
      );
    });

    it('uses default paging when none is provided', async () => {
      mockedApi.get.mockResolvedValueOnce(page([draftPost]));

      await blogService.listDrafts(SITE_ID);
      expect(mockedApi.get).toHaveBeenCalledWith(
        `/sites/${SITE_ID}/blog/posts/drafts`,
        { params: { page: 0, size: 20 } },
      );
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(blogService.listDrafts(SITE_ID)).rejects.toThrow(
        BLOG_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('getPost', () => {
    it('returns a guarded post and forwards the post path', async () => {
      mockedApi.get.mockResolvedValueOnce(post);

      await expect(blogService.getPost(SITE_ID, POST_ID)).resolves.toEqual(post);
      expect(mockedApi.get).toHaveBeenCalledWith(`/sites/${SITE_ID}/blog/posts/${POST_ID}`);
    });

    it('accepts nullable content and publishedDate', async () => {
      mockedApi.get.mockResolvedValueOnce(draftPost);

      await expect(blogService.getPost(SITE_ID, draftPost.id)).resolves.toEqual(draftPost);
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(blogService.getPost(SITE_ID, POST_ID)).rejects.toThrow(
        BLOG_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a post whose createdBy is not a string', async () => {
      mockedApi.get.mockResolvedValueOnce({ ...post, createdBy: 123 });

      await expect(blogService.getPost(SITE_ID, POST_ID)).rejects.toThrow(
        BLOG_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('createPost', () => {
    it('forwards title, content, and tags to POST', async () => {
      mockedApi.post.mockResolvedValueOnce(post);

      await expect(
        blogService.createPost(SITE_ID, 'Welcome', 'Hello, world', ['intro', 'news']),
      ).resolves.toEqual(post);
      expect(mockedApi.post).toHaveBeenCalledWith(
        `/sites/${SITE_ID}/blog/posts`,
        { title: 'Welcome', content: 'Hello, world', tags: ['intro', 'news'] },
      );
    });

    it('rejects HTML fallback on create', async () => {
      mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(blogService.createPost(SITE_ID, 'Welcome')).rejects.toThrow(
        BLOG_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('updatePost', () => {
    it('forwards the partial payload to PUT', async () => {
      mockedApi.put.mockResolvedValueOnce(post);

      await expect(
        blogService.updatePost(SITE_ID, POST_ID, { title: 'Updated' }),
      ).resolves.toEqual(post);
      expect(mockedApi.put).toHaveBeenCalledWith(
        `/sites/${SITE_ID}/blog/posts/${POST_ID}`,
        { title: 'Updated' },
      );
    });

    it('rejects a post whose tags array is malformed', async () => {
      mockedApi.put.mockResolvedValueOnce({ ...post, tags: 'intro' });

      await expect(
        blogService.updatePost(SITE_ID, POST_ID, { title: 'Updated' }),
      ).rejects.toThrow(BLOG_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('publish / unpublish', () => {
    it('publish forwards POST to the /publish path and returns the guarded post', async () => {
      mockedApi.post.mockResolvedValueOnce(post);

      await expect(blogService.publish(SITE_ID, POST_ID)).resolves.toEqual(post);
      expect(mockedApi.post).toHaveBeenCalledWith(
        `/sites/${SITE_ID}/blog/posts/${POST_ID}/publish`,
      );
    });

    it('unpublish forwards POST to the /unpublish path and returns the guarded post', async () => {
      mockedApi.post.mockResolvedValueOnce(draftPost);

      await expect(blogService.unpublish(SITE_ID, POST_ID)).resolves.toEqual(draftPost);
      expect(mockedApi.post).toHaveBeenCalledWith(
        `/sites/${SITE_ID}/blog/posts/${POST_ID}/unpublish`,
      );
    });

    it('rejects HTML fallback on publish', async () => {
      mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(blogService.publish(SITE_ID, POST_ID)).rejects.toThrow(
        BLOG_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects an unpublish response with a status outside the closed union', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...post, status: 'PENDING' });

      await expect(blogService.unpublish(SITE_ID, POST_ID)).rejects.toThrow(
        BLOG_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('deletePost', () => {
    it('forwards DELETE and resolves to void for a no-content response', async () => {
      mockedApi.delete.mockResolvedValueOnce(undefined as unknown as void);

      await expect(blogService.deletePost(SITE_ID, POST_ID)).resolves.toBeUndefined();
      expect(mockedApi.delete).toHaveBeenCalledWith(`/sites/${SITE_ID}/blog/posts/${POST_ID}`);
    });
  });
});
