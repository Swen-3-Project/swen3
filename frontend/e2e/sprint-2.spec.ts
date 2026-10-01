import { expect, test } from '@playwright/test';

test.beforeAll(async ({ request }) => {
  await expect
    .poll(async () => (await request.get('/api/documents')).status(), { timeout: 60000 })
    .toBe(200);
});

test('document and reminder workflow persists through nginx and PostgreSQL', async ({
  page,
  request,
}, testInfo) => {
  const filename = `sprint2-${Date.now()}.pdf`;
  let documentId: string | undefined;
  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  try {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Übersicht', exact: true })).toBeVisible();
    await expect(page.getByTestId('document-count')).toBeVisible();
    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: 'Zum Inhalt', exact: true })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.locator('#content')).toBeFocused();
    await page.screenshot({ path: testInfo.outputPath('dashboard.png'), fullPage: true });
    await page.getByRole('link', { name: 'Dokument anlegen', exact: true }).click();
    let createRequests = 0;
    page.on('request', (req) => {
      if (req.method() === 'POST' && new URL(req.url()).pathname === '/api/documents')
        createRequests++;
    });
    await page.getByRole('button', { name: 'Dokument speichern', exact: true }).click();
    await expect(
      page.getByText('Bitte dieses Feld ausfüllen.', { exact: true }).first(),
    ).toBeVisible();
    expect(createRequests).toBe(0);
    await page.getByLabel('Dateiname', { exact: true }).fill(filename);
    await page.getByLabel('Dateigröße in Bytes', { exact: true }).fill('4096');
    await page.getByLabel('Titel (optional)', { exact: true }).fill('Vertrag');
    await page.getByLabel('Beschreibung (optional)', { exact: true }).fill('Beschreibung bleibt');
    const creation = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/documents' &&
        response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Dokument speichern', exact: true }).click();
    const created = await creation;
    expect(created.status()).toBe(201);
    documentId = (await created.json()).id;
    await expect(page.getByRole('heading', { name: filename, exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Metadaten bearbeiten' }).click();
    await page.getByLabel('Titel (optional)', { exact: true }).fill('Geprüfter Vertrag');
    await page.getByRole('button', { name: 'Änderungen speichern' }).click();
    await expect(page.getByText('Metadaten gespeichert.', { exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByText('Geprüfter Vertrag', { exact: true })).toBeVisible();
    await expect(page.getByText('Beschreibung bleibt', { exact: true })).toBeVisible();
    await page.getByLabel('Titel der Erinnerung', { exact: true }).fill('Vertrag prüfen');
    await page.getByLabel('Fällig am', { exact: true }).fill('2026-01-01');
    await page.getByRole('button', { name: 'Erinnerung speichern', exact: true }).click();
    const reminder = page.getByRole('article', { name: 'Vertrag prüfen', exact: true });
    await expect(reminder.getByText('Offen', { exact: true })).toBeVisible();
    await expect(reminder.getByText('Fällig am 01.01.2026', { exact: true })).toBeVisible();
    await reminder.getByRole('button', { name: 'Als erledigt markieren' }).click();
    await expect(reminder.getByText('Erledigt', { exact: true })).toBeVisible();
    await page.reload();
    await expect(reminder.getByText('Erledigt', { exact: true })).toBeVisible();
    await expect(reminder.getByRole('button', { name: 'Als erledigt markieren' })).toHaveCount(0);
    await reminder.getByRole('button', { name: 'Verlauf ansehen' }).click();
    await expect(reminder.getByRole('listitem')).toHaveCount(2);
    await page.screenshot({ path: testInfo.outputPath('document-detail.png'), fullPage: true });
    const reminders = await (await request.get(`/api/documents/${documentId}/reminders`)).json();
    const reminderId = reminders[0].id;
    await page.getByRole('button', { name: 'Dokument löschen', exact: true }).click();
    await expect(
      page.getByRole('dialog').getByRole('button', { name: 'Abbrechen', exact: true }),
    ).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(
      page.getByRole('dialog').getByRole('button', { name: 'Endgültig löschen' }),
    ).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Dokument löschen', exact: true })).toBeFocused();
    await page.getByRole('button', { name: 'Dokument löschen', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Abbrechen', exact: true }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Dokument löschen', exact: true })).toBeFocused();
    await expect(page.getByRole('heading', { name: filename, exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Dokument löschen', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Endgültig löschen' }).click();
    await expect(page).toHaveURL(/\/documents$/);
    await expect(page.getByRole('link', { name: filename, exact: true })).toHaveCount(0);
    expect((await request.get(`/api/documents/${documentId}`)).status()).toBe(404);
    expect(
      (await request.get(`/api/documents/${documentId}/reminders/${reminderId}/history`)).status(),
    ).toBe(404);
    expect(pageErrors).toEqual([]);
  } finally {
    if (documentId) await request.delete(`/api/documents/${documentId}`);
  }
});

test('mobile navigation and a directly loaded missing-document URL work', async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await expect(page.getByTestId('document-count')).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Hauptnavigation' })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('mobile-dashboard.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByRole('navigation').getByRole('link', { name: 'Dokumente', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Dokumente', exact: true })).toBeVisible();
  await page.goto('/documents/00000000-0000-0000-0000-000000000000');
  await expect(page.getByRole('alert')).toContainText('nicht gefunden');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Dokument nicht verfügbar' })).toBeVisible();
  await page.route('**/api/documents', (route) => route.abort());
  await page.goto('/dashboard');
  await expect(page.getByRole('alert')).toContainText('Keine Verbindung zum Server');
  await page.unroute('**/api/documents');
  await page.getByRole('button', { name: 'Erneut versuchen' }).click();
  await expect(page.getByTestId('document-count')).toBeVisible();
});
