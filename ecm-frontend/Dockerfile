# Multi-stage build for React frontend
FROM node:20-alpine AS build

# Set working directory
WORKDIR /app

# Copy package files
COPY package.json package-lock.json ./

# Install dependencies (use install to tolerate minor lock drift)
RUN npm install --omit=dev

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
