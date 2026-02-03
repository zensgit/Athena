# Multi-stage build for React frontend
FROM node:20-alpine AS build

# Set working directory
WORKDIR /app

# Copy package files
COPY package.json package-lock.json ./

# Install dependencies (need dev deps for react-scripts build)
# Playwright is only needed for local/CI E2E tests; skip browser downloads in the image build.
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV NODE_OPTIONS=--max_old_space_size=4096
ARG REACT_APP_E2E_BYPASS_AUTH
ENV REACT_APP_E2E_BYPASS_AUTH=$REACT_APP_E2E_BYPASS_AUTH
RUN npm ci --legacy-peer-deps --no-audit --no-fund

# Copy source code
COPY . .

# Build the app
RUN npm run build

# Production stage with Nginx
FROM nginx:alpine

# Copy built app from build stage
COPY --from=build /app/build /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port
EXPOSE 80

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
