# ==============================
# ETAPA 1: Build del frontend
# ==============================

FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./

# npm install actualiza el lock al agregar Konva en esta primera integración.
# Después de ejecutar npm install localmente y versionar package-lock.json,
# puede volver a usarse npm ci para builds totalmente reproducibles.
RUN npm ci

COPY . .

RUN npm run build


# ==============================
# ETAPA 2: Servidor web
# ==============================

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]