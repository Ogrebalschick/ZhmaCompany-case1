export function errorText(error: unknown): string {
  if (error instanceof TypeError) {
    return "Не удалось связаться с сервером. Проверьте, что он запущен на порту 8080.";
  }
  if (error instanceof Error && error.message.trim()) return error.message;
  return "Не удалось связаться с сервером.";
}
