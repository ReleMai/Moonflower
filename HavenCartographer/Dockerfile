# =============================================================================
# Haven Cartographer - Dockerfile
# =============================================================================

FROM node:20-slim AS base

LABEL maintainer="Rele"
LABEL description="Haven Cartographer - Map Server for Haven & Hearth"

WORKDIR /app

# Install only production dependencies
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev && npm cache clean --force

# Copy source code
COPY config.json ./
COPY scripts/ ./scripts/
COPY src/ ./src/

# Create data directories
RUN mkdir -p data/tiles data/maps data/exports data/live data/screenshots

# Expose port
EXPOSE 3300

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD node -e "fetch('http://localhost:3300/api/health').then(r=>{if(!r.ok)throw r;process.exit(0)}).catch(()=>process.exit(1))"

# Default: run the map server
CMD ["node", "src/server/index.js"]
