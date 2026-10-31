/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_USE_MOCK?: string;
  readonly VITE_TURN_TIMEOUT_SEC?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
