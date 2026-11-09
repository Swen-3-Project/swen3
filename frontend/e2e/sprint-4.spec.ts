import { expect, test } from '@playwright/test';
import { resolve } from 'node:path';

for (const sample of [
  { file: 'HelloWorld.pdf', query: 'Hello', fuzzy: 'Hlelo' },
  { file: 'scan.pdf', query: 'Sonnenblume', fuzzy: 'Sonnenbluem' },
]) {
  test(
    'recognizes and finds ' + sample.file + ' through the website',
    async ({ page, request }) => {
      test.setTimeout(180_000);
      let id: string | undefined;
      try {
        await page.goto('/documents/upload');
        await page
          .getByLabel('PDF-Datei', { exact: true })
          .setInputFiles(resolve('../tests/fixtures/' + sample.file));
        const created = page.waitForResponse(
          (response) =>
            response.request().method() === 'POST' &&
            new URL(response.url()).pathname === '/api/documents/upload',
        );
        await page.getByRole('button', { name: 'PDF hochladen', exact: true }).click();
        const response = await created;
        expect(response.status()).toBe(201);
        id = (await response.json()).id;
        await expect
          .poll(async () => (await (await request.get('/api/documents/' + id)).json()).status, {
            timeout: 120_000,
          })
          .toBe('INDEXED');
        await page.getByRole('button', { name: 'Aktualisieren', exact: true }).click();
        await expect(page.locator('.ocr-text')).toContainText(sample.query);
        await page.getByRole('link', { name: 'Zur Dokumentliste', exact: false }).click();
        await page.getByLabel('Dokumentinhalt durchsuchen').fill(sample.query);
        await page.getByRole('button', { name: 'Suchen', exact: true }).click();
        await expect(
          page.locator('table').getByRole('link', { name: sample.file, exact: true }),
        ).toBeVisible();
        await page.getByLabel('Dokumentinhalt durchsuchen').fill(sample.fuzzy);
        await page.getByRole('button', { name: 'Suchen', exact: true }).click();
        await expect(
          page.locator('table').getByRole('link', { name: sample.file, exact: true }),
        ).toBeVisible();
        await page.getByRole('button', { name: 'Zurücksetzen', exact: true }).click();
        await expect(page.getByLabel('Dokumentinhalt durchsuchen')).toHaveValue('');
      } finally {
        if (id) await request.delete('/api/documents/' + id);
      }
    },
  );
}
