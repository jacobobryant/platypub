const { test, expect } = require('@playwright/test');

const adminEmail = 'admin@example.test';
const waitlistEmail = 'waitlist@example.test';
const confirmedEmail = 'confirmed@example.test';
const deliveryEmail = 'subscriber001@example.test';

let publicationPath;

async function settle(page) {
  // The initial SSE response morphs the server-rendered content once Datastar
  // has created its per-tab signal. Avoid typing into the element it replaces.
  await page.waitForTimeout(150);
}

async function emails(request) {
  const response = await request.get('/_mock/mailersend/emails');
  expect(response.ok()).toBeTruthy();
  return (await response.json()).data;
}

async function latestEmail(request, recipient, subjectPrefix) {
  await expect.poll(async () => {
    const messages = await emails(request);
    return messages.some((message) =>
      message.to?.some((to) => to.email === recipient)
      && message.subject?.startsWith(subjectPrefix));
  }).toBe(true);

  const messages = await emails(request);
  return messages.findLast((message) =>
    message.to?.some((to) => to.email === recipient)
    && message.subject?.startsWith(subjectPrefix));
}

async function signIn(page, request, email) {
  await page.goto('/signin');
  await page.getByLabel('Email address').fill(email);
  await page.getByRole('button', { name: 'Continue' }).click();

  const message = await latestEmail(request, email, 'Your sign-in code');
  const code = message.text.match(/\b\d{6}\b/)?.[0];
  expect(code).toBeTruthy();

  await page.getByLabel('Verification code').fill(code);
  await page.getByRole('button', { name: 'Verify' }).click();
  await expect(page).toHaveURL(/\/app$/);
  await settle(page);
}

test.describe.serial('Platypub user flows', () => {
  test('the first user becomes an admin and can admit a waitlisted user', async ({ browser, request }) => {
    const adminContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    await signIn(adminPage, request, adminEmail);
    await expect(adminPage.getByRole('heading', { name: 'Publications' })).toBeVisible();
    await expect(adminPage.getByRole('link', { name: 'Admin' })).toBeVisible();

    const waitlistContext = await browser.newContext();
    const waitlistPage = await waitlistContext.newPage();
    await signIn(waitlistPage, request, waitlistEmail);
    await expect(waitlistPage.getByRole('heading', { name: /waitlist/i })).toBeVisible();

    await adminPage.getByRole('link', { name: 'Admin' }).click();
    const userForm = adminPage.locator('form').filter({ hasText: waitlistEmail });
    await userForm.locator('select').selectOption('free');
    await userForm.getByRole('button', { name: 'Save' }).click();

    await waitlistPage.reload();
    await expect(waitlistPage.getByRole('heading', { name: 'Publications' })).toBeVisible();

    await adminContext.close();
    await waitlistContext.close();
  });

  test('an admin creates a publication whose feed has no description', async ({ page, request }) => {
    const sseRequests = [];
    page.on('request', (pageRequest) => {
      if (pageRequest.method() === 'GET'
          && pageRequest.headers().accept?.includes('text/event-stream')) {
        sseRequests.push(pageRequest.url());
      }
    });

    await signIn(page, request, adminEmail);
    sseRequests.length = 0;

    const createResponse = page.waitForResponse((response) =>
      response.url().endsWith('/app/publications')
      && response.request().method() === 'POST');
    await page.getByPlaceholder('Website or feed URL')
      .fill('http://127.0.0.1:9090/descriptionless');
    await page.getByRole('button', { name: 'Add publication' }).click();
    expect((await createResponse).status()).toBe(204);

    await expect(page.getByRole('link', { name: 'Fixture JSON Feed' })).toBeVisible();
    await page.waitForTimeout(300);
    expect(sseRequests).toHaveLength(0);

    const reloadResponse = await page.reload();
    expect(reloadResponse.status()).toBe(200);
    await expect(page.getByRole('link', { name: 'Fixture JSON Feed' })).toBeVisible();
  });

  test('an owner handles feed discovery and creates a publication', async ({ page, request }) => {
    await signIn(page, request, adminEmail);

    const noFeedResponse = page.waitForResponse((response) =>
      response.url().endsWith('/app/publications')
      && response.request().method() === 'POST');
    await page.getByPlaceholder('Website or feed URL').fill('http://127.0.0.1:9090/no-feed');
    await page.getByRole('button', { name: 'Add publication' }).click();
    expect((await noFeedResponse).status()).toBe(422);

    await page.getByPlaceholder('Website or feed URL').fill('http://127.0.0.1:9090/multi');
    await page.getByRole('button', { name: 'Add publication' }).click();
    await expect(page.locator('select')).toBeVisible();
    await expect(page.locator('select option')).toHaveCount(3);
    await page.locator('select').selectOption('http://127.0.0.1:9090/feed.xml');
    const createResponse = page.waitForResponse((response) =>
      response.url().endsWith('/app/publications')
      && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Use this feed' }).click();
    expect((await createResponse).status()).toBe(204);

    const publicationLink = page.getByRole('link', { name: /Fixture Gazette/ });
    await expect(publicationLink).toBeVisible();
    publicationPath = await publicationLink.getAttribute('href');
    await publicationLink.click();
    await expect(page.getByRole('heading', { name: 'Fixture Gazette' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'First post' })).toBeVisible();
    await expect(page.getByText('Hosted form:')).toBeVisible();
  });

  test('an owner syncs the feed and edits publication settings', async ({ page, request }) => {
    await signIn(page, request, adminEmail);
    await page.goto(publicationPath);
    await settle(page);

    await request.post('http://127.0.0.1:9090/control/feed-version?value=2');
    const syncResponse = page.waitForResponse((response) =>
      response.url().endsWith('/sync') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Sync feed' }).click();
    expect((await syncResponse).status()).toBe(204);
    await expect(page.getByRole('heading', { name: 'Second post' })).toBeVisible();

    await page.getByRole('link', { name: 'Settings' }).click();
    await expect(page.getByRole('heading', { name: 'Publication settings' })).toBeVisible();
    await settle(page);
    await expect(page.getByRole('heading', { name: 'Subscribe form' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Email' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Lorem ipsum' })).toBeVisible();

    const bannerForm = page.locator('form').filter({ hasText: 'Banner image' });
    const uploadResponse = page.waitForResponse((response) =>
      response.url().includes('/settings/image/banner')
      && response.request().method() === 'POST');
    await bannerForm.locator('input[type=file]').setInputFiles({
      name: 'banner.png',
      mimeType: 'image/png',
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
        'base64'),
    });
    expect((await uploadResponse).status()).toBe(204);
    await expect(bannerForm.locator('img')).toHaveAttribute('src', /_mock\/cdn\/.+\.png/);

    await page.getByLabel('Title').fill('Updated Gazette');
    await page.getByLabel('Description').fill('Updated publication description');
    await page.getByLabel('Intro').fill('A short introduction');
    await page.getByLabel('Default author name').fill('Fixture Editor');
    await page.getByLabel('Welcome HTML').fill('<strong>Welcome aboard.</strong>');
    await page.getByLabel('Require confirmation').check();

    const settingsResponse = page.waitForResponse((response) =>
      response.url().endsWith('/settings') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save settings' }).click();
    expect((await settingsResponse).status()).toBe(204);

    await page.getByRole('link', { name: 'Publication' }).click();
    await expect(page.getByRole('heading', { name: 'Updated Gazette' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Second post' })).toBeVisible();

    await page.getByRole('link', { name: 'Settings' }).click();
    await settle(page);
    await page.getByLabel('Feed URL').fill('http://127.0.0.1:9090/feed.json');
    const feedChangeResponse = page.waitForResponse((response) =>
      response.url().endsWith('/settings') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save settings' }).click();
    expect((await feedChangeResponse).status()).toBe(204);

    await page.getByRole('link', { name: 'Publication' }).click();
    await expect(page.getByRole('heading', { name: 'Updated Gazette' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'JSON post', exact: true })).toBeVisible();
  });

  test('a reader confirms a subscription and receives a welcome email', async ({ page, request }) => {
    const subscribePath = publicationPath.replace('/app/publications/', '/subscribe/');
    await page.goto(subscribePath);
    await expect(page.getByRole('heading', { name: 'Updated Gazette' })).toBeVisible();
    await expect(page.locator('img')).toHaveAttribute('src', /_mock\/cdn\/.+\.png/);

    const subscribeResponse = page.waitForResponse((response) =>
      response.url().includes('/subscribe/') && response.request().method() === 'POST');
    await page.getByPlaceholder('you@example.com').fill(`  ${confirmedEmail.toUpperCase()}  `);
    await page.getByRole('button', { name: 'Subscribe' }).click();
    expect((await subscribeResponse).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Check your inbox' })).toBeVisible();

    const confirmation = await latestEmail(request, confirmedEmail, 'Confirm your subscription');
    const confirmationUrl = confirmation.text.match(/https?:\/\/\S+\/confirm\/\S+/)?.[0];
    expect(confirmationUrl).toBeTruthy();
    await page.goto(confirmationUrl);
    await expect(page.getByRole('heading', { name: 'Subscription confirmed' })).toBeVisible();

    const welcome = await latestEmail(request, confirmedEmail, 'Welcome to Updated Gazette');
    expect(welcome.html).toContain('<strong>Welcome aboard.</strong>');
  });

  test('an admin imports subscribers and an owner can paginate, search, and unsubscribe', async ({ page, request }) => {
    await signIn(page, request, adminEmail);
    await page.goto('/app/admin');
    await settle(page);
    await expect(page.getByText('Updated Gazette', { exact: true })).toHaveCount(0);

    const searchResponse = page.waitForResponse((response) =>
      response.url().endsWith('/app/admin') && response.request().method() === 'POST');
    await page.getByPlaceholder('Search title or owner email').fill('Updated Gazette');
    await page.getByRole('button', { name: 'Search' }).click();
    expect((await searchResponse).status()).toBe(204);

    const importForm = page.locator('form').filter({ hasText: 'Updated Gazette' });
    await expect(importForm).toBeVisible();
    const csv = ['email'];
    for (let index = 1; index <= 51; index += 1) {
      csv.push(`subscriber${String(index).padStart(3, '0')}@example.test`);
    }
    csv.push('invalid-address');
    csv.push(deliveryEmail);
    await importForm.locator('input[type=file]').setInputFiles({
      name: 'subscribers.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csv.join('\n')),
    });
    const importResponse = page.waitForResponse((response) =>
      response.url().includes('/import') && response.request().method() === 'POST');
    await importForm.getByRole('button', { name: 'Import' }).click();
    expect((await importResponse).status()).toBe(204);

    await page.goto(`${publicationPath}/subscribers`);
    await settle(page);
    await expect(page.getByRole('link', { name: 'Next' })).toBeVisible();
    await page.getByRole('link', { name: 'Next' }).click();
    await expect(page).toHaveURL(/\?page=2$/);
    await expect(page.getByRole('link', { name: 'Previous' })).toBeVisible();

    await page.goto(`${publicationPath}/subscribers`);
    await settle(page);
    const subscriberSearchResponse = page.waitForResponse((response) =>
      response.url().endsWith('/subscribers') && response.request().method() === 'POST');
    await page.getByPlaceholder('Search email').fill('subscriber051');
    await page.getByRole('button', { name: 'Search' }).click();
    expect((await subscriberSearchResponse).status()).toBe(204);
    const row = page.locator('tr').filter({ hasText: 'subscriber051@example.test' });
    await expect(row).toBeVisible();
    const unsubscribeResponse = page.waitForResponse((response) =>
      response.url().includes('/app/subscribers/') && response.request().method() === 'POST');
    await row.getByRole('button', { name: 'Unsubscribe' }).click();
    expect((await unsubscribeResponse).status()).toBe(204);
    await expect(row.getByRole('button', { name: 'Unsubscribe' })).toHaveCount(0);
  });

  test('an owner previews and sends a newsletter and a reader unsubscribes', async ({ page, request }) => {
    await signIn(page, request, adminEmail);
    await page.goto(publicationPath);
    await settle(page);
    await page.getByRole('link', { name: 'Send' }).click();
    await expect(page.getByRole('heading', { name: 'Send newsletter' })).toBeVisible();
    await expect(page.getByText('Unselected JSON post', { exact: true })).toBeVisible();

    const post = page.getByText('JSON post', { exact: true });
    await post.click();
    const previewResponse = page.waitForResponse((response) =>
      response.url().endsWith('/send') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Preview' }).click();
    expect((await previewResponse).status()).toBe(204);
    await expect(page.getByText('From: Updated Gazette')).toBeVisible();
    await expect(page.getByText('Subject: JSON post')).toBeVisible();
    await expect(page.frameLocator('[title="Newsletter preview"]')
      .getByText('JSON feed body.')).toBeVisible();
    await expect(page.frameLocator('[title="Newsletter preview"]')
      .getByText('This post should not be in the newsletter.')).toHaveCount(0);

    const confirmResponse = page.waitForResponse((response) =>
      response.url().endsWith('/send/confirm') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Confirm and send' }).click();
    expect((await confirmResponse).status()).toBe(204);

    const newsletter = await latestEmail(request, deliveryEmail, 'JSON post');
    expect(newsletter.from.name).toBe('Updated Gazette');
    expect(newsletter.reply_to.email).toBe(adminEmail);
    expect(newsletter.html).toContain('{{unsubscribe_url}}');
    expect(newsletter.list_unsubscribe).toBeTruthy();
    expect(newsletter.headers).toContainEqual({
      name: 'List-Unsubscribe-Post',
      value: 'List-Unsubscribe=One-Click',
    });
    const unsubscribeUrl = newsletter.personalization[0].data.unsubscribe_url;

    await page.goto(unsubscribeUrl);
    await expect(page.getByText(`Stop emails to ${deliveryEmail}?`)).toBeVisible();
    const publicUnsubscribeResponse = page.waitForResponse((response) =>
      response.url().includes('/unsubscribe/') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Confirm unsubscribe' }).click();
    expect((await publicUnsubscribeResponse).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'You have been unsubscribed.' })).toBeVisible();

    await page.goto(`${publicationPath}/subscribers`);
    await settle(page);
    const searchResponse = page.waitForResponse((response) =>
      response.url().endsWith('/subscribers') && response.request().method() === 'POST');
    await page.getByPlaceholder('Search email').fill(deliveryEmail);
    await page.getByRole('button', { name: 'Search' }).click();
    expect((await searchResponse).status()).toBe(204);
    const row = page.locator('tr').filter({ hasText: deliveryEmail });
    await expect(row).toBeVisible();
    await expect(row.getByRole('button', { name: 'Unsubscribe' })).toHaveCount(0);
  });
});
