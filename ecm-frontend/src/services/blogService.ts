import api from './api';

export const BLOG_UNEXPECTED_RESPONSE_MESSAGE =
  'Blog endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export type BlogStatus = 'DRAFT' | 'PUBLISHED';

export interface BlogPostDto {
  id: string;
  siteId: string;
  title: string;
  content?: string | null;
  status: BlogStatus;
  publishedDate?: string | null;
  tags: string[];
  createdBy: string;
  createdDate: string;
}

export interface BlogPostPage {
  content: BlogPostDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const BLOG_STATUSES: BlogStatus[] = ['DRAFT', 'PUBLISHED'];

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isBlogStatus = (value: unknown): value is BlogStatus => (
  typeof value === 'string' && (BLOG_STATUSES as string[]).includes(value)
);

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((entry) => typeof entry === 'string')
);

const assertUnexpectedResponse = (): never => {
  throw new Error(BLOG_UNEXPECTED_RESPONSE_MESSAGE);
};

const isBlogPostDto = (value: unknown): value is BlogPostDto => {
  if (!isObject(value)) {
    return false;
  }
  if (typeof value.id !== 'string'
    || typeof value.siteId !== 'string'
    || typeof value.title !== 'string'
    || typeof value.createdBy !== 'string'
    || typeof value.createdDate !== 'string') {
    return false;
  }
  if (!isBlogStatus(value.status)) {
    return false;
  }
  if (!isStringOrNullish(value.content) || !isStringOrNullish(value.publishedDate)) {
    return false;
  }
  if (!isStringArray(value.tags)) {
    return false;
  }
  return true;
};

const assertBlogPostDto = (value: unknown): BlogPostDto => (
  isBlogPostDto(value) ? value : assertUnexpectedResponse()
);

const isBlogPostPage = (value: unknown): value is BlogPostPage => (
  isObject(value)
    && Array.isArray(value.content)
    && value.content.every(isBlogPostDto)
    && isFiniteNumber(value.totalElements)
    && isFiniteNumber(value.totalPages)
    && isFiniteNumber(value.number)
    && isFiniteNumber(value.size)
);

const assertBlogPostPage = (value: unknown): BlogPostPage => (
  isBlogPostPage(value) ? value : assertUnexpectedResponse()
);

class BlogService {
  async listPosts(siteId: string, page = 0, size = 20, status?: BlogStatus): Promise<BlogPostPage> {
    const params: Record<string, string | number> = { page, size };
    if (status) params.status = status;
    const result = await api.get<unknown>(`/sites/${siteId}/blog/posts`, { params });
    return assertBlogPostPage(result);
  }

  async listDrafts(siteId: string, page = 0, size = 20): Promise<BlogPostPage> {
    const result = await api.get<unknown>(`/sites/${siteId}/blog/posts/drafts`, { params: { page, size } });
    return assertBlogPostPage(result);
  }

  async getPost(siteId: string, postId: string): Promise<BlogPostDto> {
    const result = await api.get<unknown>(`/sites/${siteId}/blog/posts/${postId}`);
    return assertBlogPostDto(result);
  }

  async createPost(siteId: string, title: string, content?: string, tags?: string[]): Promise<BlogPostDto> {
    const result = await api.post<unknown>(`/sites/${siteId}/blog/posts`, { title, content, tags });
    return assertBlogPostDto(result);
  }

  async updatePost(siteId: string, postId: string, data: { title?: string; content?: string; tags?: string[] }): Promise<BlogPostDto> {
    const result = await api.put<unknown>(`/sites/${siteId}/blog/posts/${postId}`, data);
    return assertBlogPostDto(result);
  }

  async publish(siteId: string, postId: string): Promise<BlogPostDto> {
    const result = await api.post<unknown>(`/sites/${siteId}/blog/posts/${postId}/publish`);
    return assertBlogPostDto(result);
  }

  async unpublish(siteId: string, postId: string): Promise<BlogPostDto> {
    const result = await api.post<unknown>(`/sites/${siteId}/blog/posts/${postId}/unpublish`);
    return assertBlogPostDto(result);
  }

  async deletePost(siteId: string, postId: string): Promise<void> {
    await api.delete(`/sites/${siteId}/blog/posts/${postId}`);
  }
}

const blogService = new BlogService();
export default blogService;
