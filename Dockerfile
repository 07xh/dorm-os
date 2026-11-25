FROM node:22-bookworm-slim
WORKDIR /app
ENV WRANGLER_SEND_METRICS=false
COPY package.json ./
RUN npm install --no-audit --no-fund --ignore-scripts \
 && npm install --global --no-audit --no-fund wrangler@4 \
 && npm cache clean --force
COPY public/ public/
COPY functions/ functions/
COPY lib/ lib/
COPY migrations/ migrations/
COPY wrangler.local.toml ./
COPY docker/start-local.sh /usr/local/bin/start-local
RUN mkdir -p /data && chown node:node /data /app && chmod 755 /usr/local/bin/start-local
USER node
EXPOSE 8788
CMD ["/usr/local/bin/start-local"]
