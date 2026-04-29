import axios from 'axios';

// Create a centralized Axios instance
const api = axios.create({
  baseURL: 'http://localhost:9918/cph', // Update this if your API is hosted on a different domain/port
  withCredentials: true, // Important for sending/receiving cookies
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor for adding auth tokens or other global request config
api.interceptors.request.use(
  (config) => {
    // Example: Add auth token if available
    // const token = localStorage.getItem('token');
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`;
    // }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor for global error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // Example: Handle global errors like 401 Unauthorized
    if (error.response?.status === 401) {
      console.warn('Unauthorized access - perhaps redirect to login?');
    }
    console.error('API Error:', error.response?.data || error.message);
    return Promise.reject(error);
  }
);

export default api;
