import api from './api';
import { User } from '@/types';

interface LoginRequest {
  username: string;
  password: string;
}

interface LoginResponse {
  token: string;
  user: User;
}

class AuthService {
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    const response = await api.post<LoginResponse>('/auth/login', credentials);
    localStorage.setItem('token', response.token);
    localStorage.setItem('user', JSON.stringify(response.user));
    return response;
  }

  async logout(): Promise<void> {
    await api.post('/auth/logout');
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  }

  async getCurrentUser(): Promise<User> {
    return api.get<User>('/auth/me');
  }

  async refreshToken(): Promise<string> {
    const response = await api.post<{ token: string }>('/auth/refresh');
    localStorage.setItem('token', response.token);
    return response.token;
  }

  getStoredUser(): User | null {
    const userStr = localStorage.getItem('user');
    return userStr ? JSON.parse(userStr) : null;
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }
}

export default new AuthService();