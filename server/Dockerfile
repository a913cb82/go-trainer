FROM node:20-slim
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY dist ./dist
COPY config ./config
# models mounted at runtime: -v ./models:/app/models
EXPOSE 3001
ENV PORT=3001 KATAGO_MODE=real
CMD ["node","dist/index.js"]
