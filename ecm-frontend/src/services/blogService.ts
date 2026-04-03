import api from './api';

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

class BlogService {
  async listPosts(siteId: string, page = 0, size = 20, status?: BlogStatus): Promise<BlogPostPage> {
    const params: Record<string, string | number> = { page, size };
    if (status) params.status = status;
    return api.get<BlogPostPage>(`/sites/${siteId}/blog/posts`, { params });
  }

  async listDrafts(siteId: string, page = 0, size = 20): Promise<BlogPostPage> {
    return api.get<BlogPostPage>(`/sites/${siteId}/blog/posts/drafts`, { params: { page, size } });
  }

  async getPost(siteId: string, postId: string): Promise<BlogPostDto> {
    return api.get<BlogPostDto>(`/sites/${siteId}/blog/posts/${postId}`);
  }

  async createPost(siteId: string, title: string, content?: string, tags?: string[]): Promise<BlogPostDto> {
    return api.post<BlogPostDto>(`/sites/${siteId}/blog/posts`, { title, content, tags });
  }

  async updatePost(siteId: string, postId: string, data: { title?: string; content?: string; tags?: string[] }): Promise<BlogPostDto> {
    return api.put<BlogPostDto>(`/sites/${siteId}/blog/posts/${postId}`, data);
  }

  async publish(siteId: string, postId: string): Promise<BlogPostDto> {
    return api.post<BlogPostDto>(`/sites/${siteId}/blog/posts/${postId}/publish`);
  }

  async unpublish(siteId: string, postId: string): Promise<BlogPostDto> {
    return api.post<BlogPostDto>(`/sites/${siteId}/blog/posts/${postId}/unpublish`);
  }

  async deletePost(siteId: string, postId: string): Promise<void> {
    return api.delete(`/sites/${siteId}/blog/posts/${postId}`);
  }
}

const blogService = new BlogService();
export default blogService;
