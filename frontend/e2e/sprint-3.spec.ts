import { expect, test } from '@playwright/test';
import { resolve } from 'node:path';

test('uploads a real PDF and preserves metadata through reload', async ({ page, request }) => {
  let id: string | undefined;
  try {
    await page.goto('/documents');
    await page.getByRole('link', { name: 'PDF hochladen', exact: true }).click();
    await page.getByRole('button', { name: 'PDF hochladen', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('PDF-Datei auswählen');
    await page.getByLabel('PDF-Datei', { exact: true }).setInputFiles(resolve('../tests/fixtures/hello.pdf'));
    await page.getByLabel('Titel (optional)', { exact: true }).fill('Hochgeladene PDF');
    await page.getByLabel('Beschreibung (optional)', { exact: true }).fill('Upload bleibt gespeichert');
    const creation = page.waitForResponse(response =>
      response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/documents/upload');
    await page.getByRole('button', { name: 'PDF hochladen', exact: true }).click();
    const response = await creation;
    expect(response.status()).toBe(201);
    id = (await response.json()).id;
    await expect(page.getByRole('heading', { name: 'hello.pdf', exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByText('Hochgeladene PDF', { exact: true })).toBeVisible();
    await expect(page.getByText('Upload bleibt gespeichert', { exact: true })).toBeVisible();
    const document = await (await request.get(`/api/documents/${id}`)).json();
    expect(document.mimeType).toBe('application/pdf');
    expect(document.fileSize).toBeGreaterThan(0);
  } finally {
    if (id) await request.delete(`/api/documents/${id}`);
  }
});
