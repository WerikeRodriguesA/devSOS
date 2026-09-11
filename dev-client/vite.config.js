import { defineConfig } from 'vite';

// Dev Client Web: SPA estática simples (sem framework).
// Porta padrão 5173 — a mesma usada como origem CORS padrão no backend
// (devsos.cors.allowed-origins). Altere aqui se quiser outra porta.
export default defineConfig({
  server: { port: 5173 },
  preview: { port: 5173 },
});