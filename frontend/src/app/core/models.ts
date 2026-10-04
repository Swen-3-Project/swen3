export interface Document {
  id: string; filename: string; mimeType: string; fileSize: number;
  status: 'CREATED'; createdAt: string; title: string | null; description: string | null;
}
export interface CreateDocument {
  filename: string; mimeType: string; fileSize: number; title: string | null; description: string | null;
}
export interface UpdateDocument { title: string | null; description: string | null; }
export interface Reminder {
  id: string; documentId: string; title: string; description: string | null; dueDate: string;
  status: 'OPEN' | 'COMPLETED'; createdAt: string; completedAt: string | null;
}
export interface CreateReminder { title: string; description: string | null; dueDate: string; }
export interface ReminderHistory { id: string; event: 'CREATED' | 'COMPLETED'; occurredAt: string; }
export interface ApiProblem { status: number; title?: string; detail?: string; errors?: Record<string, string>; }
