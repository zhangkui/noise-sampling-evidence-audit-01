# ---------- 构建阶段 ----------
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json package-lock.json* ./
# 有 lock 文件走 npm ci；没有则 npm install
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

COPY . .
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
