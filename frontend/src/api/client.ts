import axios from 'axios';

const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Redirect to login on 401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && window.location.pathname !== '/login') {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;

// ─── Auth ────────────────────────────────────────────────────────
export const authApi = {
  register: (data: { email: string; password: string; fullName: string; organizationName?: string }) =>
    api.post('/auth/register', data),
  login: (data: { email: string; password: string }) =>
    api.post('/auth/login', data),
};

// ─── Projects ────────────────────────────────────────────────────
export const projectApi = {
  list: (organizationId?: string, page = 0, size = 20) =>
    api.get('/projects', { params: { organizationId, page, size } }),
  create: (organizationId: string, data: { name: string }) =>
    api.post('/projects', data, { params: { organizationId } }),
};

// ─── Queues ──────────────────────────────────────────────────────
export const queueApi = {
  list: (projectId?: string, page = 0, size = 20) =>
    api.get('/queues', { params: { projectId, page, size } }),
  get: (id: string) => api.get(`/queues/${id}`),
  create: (data: Record<string, unknown>) => api.post('/queues', data),
  update: (id: string, data: Record<string, unknown>) => api.put(`/queues/${id}`, data),
  delete: (id: string) => api.delete(`/queues/${id}`),
  pause: (id: string) => api.post(`/queues/${id}/pause`),
  resume: (id: string) => api.post(`/queues/${id}/resume`),
  stats: (id: string) => api.get(`/queues/${id}/stats`),
};

// ─── Jobs ────────────────────────────────────────────────────────
export const jobApi = {
  list: (params: { queueId?: string; status?: string; page?: number; size?: number }) =>
    api.get('/jobs', { params }),
  get: (id: string) => api.get(`/jobs/${id}`),
  create: (data: Record<string, unknown>) => api.post('/jobs', data),
  createBatch: (data: Record<string, unknown>) => api.post('/jobs/batch', data),
  retry: (id: string) => api.post(`/jobs/${id}/retry`),
  cancel: (id: string) => api.post(`/jobs/${id}/cancel`),
  delete: (id: string) => api.delete(`/jobs/${id}`),
};

// ─── Workers ─────────────────────────────────────────────────────
export const workerApi = {
  list: (status?: string) => api.get('/workers', { params: { status } }),
  get: (id: string) => api.get(`/workers/${id}`),
  clearOffline: () => api.delete('/workers/offline'),
};

// ─── Metrics ─────────────────────────────────────────────────────
export const metricsApi = {
  global: () => api.get('/metrics/global'),
  queue: (queueId: string) => api.get(`/metrics/queues/${queueId}`),
};

// ─── Dead Letter Queue ───────────────────────────────────────────
export const dlqApi = {
  list: (page = 0, size = 20) => api.get('/dlq', { params: { page, size } }),
  retry: (id: string) => api.post(`/dlq/${id}/retry`),
  delete: (id: string) => api.delete(`/dlq/${id}`),
};

// ─── Retry Policies ──────────────────────────────────────────────
export const retryPolicyApi = {
  list: (organizationId?: string, page = 0, size = 100) =>
    api.get('/retry-policies', { params: { organizationId, page, size } }),
  get: (id: string) => api.get(`/retry-policies/${id}`),
  create: (data: Record<string, unknown>, organizationId?: string) =>
    api.post('/retry-policies', data, { params: { organizationId } }),
  update: (id: string, data: Record<string, unknown>) =>
    api.put(`/retry-policies/${id}`, data),
  delete: (id: string) => api.delete(`/retry-policies/${id}`),
};
