const { test, expect } = require('@playwright/test');

const adminEmail = 'admin@example.test';
const waitlistEmail = 'waitlist@example.test';
const confirmedEmail = 'confirmed@example.test';
const deliveryEmail = 'subscriber001@example.test';
const immediateEmail = 'immediate@example.test';

let publicationPath;
let atomPublicationPath;
let renderingPublicationPath;

async function settle(page) {
  // The initial SSE response morphs the server-rendered content once Datastar
  // has created its per-tab signal. Avoid typing into the element it replaces.
  await page.waitForTimeout(400);
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

async function createPublication(page, url, title) {
  await openPublicationDialog(page);
  const createResponse = page.waitForResponse((response) =>
    response.url().endsWith('/app/publications')
    && response.request().method() === 'POST');
  await page.getByPlaceholder('Website or feed URL').fill(url);
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  const response = await createResponse;
  expect(response.status()).toBe(204);
  const heading = page.getByRole('heading', { name: title, exact: true }).last();
  await expect(heading).toBeVisible();
  const path = await heading.locator('..').getAttribute('href');
  await page.reload();
  await settle(page);
  return path;
}

async function openPublicationDialog(page) {
  await expect.poll(async () => {
    if (!(await page.locator('#add-publication').isVisible())) {
      await page.getByRole('button', { name: 'Add publication' }).click({ timeout: 1000 });
    }
    await page.waitForTimeout(200);
    return page.locator('#add-publication').isVisible();
  }).toBe(true);
}

async function submitSubscription(page, publication, email) {
  await page.goto(publication.replace('/app/publications/', '/subscribe/'));
  const responsePromise = page.waitForResponse((response) =>
    response.url().includes('/subscribe/') && response.request().method() === 'POST');
  await page.locator('input[type=email]').fill(email);
  await page.getByRole('button', { name: 'Subscribe' }).click();
  expect((await responsePromise).status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Check your inbox' })).toBeVisible();
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
    const tierResponse = adminPage.waitForResponse((response) =>
      response.url().includes('/app/admin/users/')
      && response.request().method() === 'POST');
    await userForm.getByRole('button', { name: 'Save' }).click();
    expect((await tierResponse).status()).toBe(204);

    await adminPage.reload();
    const admittedUserForm = adminPage.locator('form').filter({ hasText: waitlistEmail });
    await expect(admittedUserForm.locator('option[value=waitlist]')).toHaveCount(0);

    await expect.poll(async () => {
      await waitlistPage.reload();
      return waitlistPage.getByRole('heading', { name: 'Publications' }).count();
    }).toBe(1);
    expect((await waitlistContext.request.get('/app/admin')).status()).toBe(403);

    await adminContext.close();
    await waitlistContext.close();
  });

  test('publication setup rejects unusable feeds and supports redirects, Atom, JSON, and feed reuse', async ({ page, request }) => {
    await signIn(page, request, adminEmail);

    for (const url of [
      'http://127.0.0.1:9090/invalid-feed',
      'http://127.0.0.1:9090/empty.json',
    ]) {
      await openPublicationDialog(page);
      const responsePromise = page.waitForResponse((response) =>
        response.url().endsWith('/app/publications')
        && response.request().method() === 'POST');
      await page.getByPlaceholder('Website or feed URL').fill(url);
      await page.getByRole('button', { name: 'Save', exact: true }).click();
      const response = await responsePromise;
      expect(response.status()).toBe(422);
    }

    const redirectedPublicationPath = await createPublication(
      page, 'http://127.0.0.1:9090/redirect', 'Fixture Gazette');
    await page.goto(`${redirectedPublicationPath}/settings`);
    await settle(page);
    await expect(page.getByLabel('Feed URL')).toHaveValue('http://127.0.0.1:9090/feed.xml');

    await page.goto('/app');
    await settle(page);
    atomPublicationPath = await createPublication(
      page, 'http://127.0.0.1:9090/atom.xml', 'Atom Fixture');
    await page.goto(`${atomPublicationPath}/settings`);
    await settle(page);
    await expect(page.getByLabel('Feed URL')).toHaveValue('http://127.0.0.1:9090/atom.xml');
    await expect(page.getByLabel('Title', { exact: true })).toHaveValue('Atom Fixture');
    await expect(page.getByLabel('Description')).toHaveValue('An Atom publication.');
    await expect(page.getByLabel('Default author name')).toHaveValue('Atom Author');

    await page.goto('/app');
    await settle(page);
    await createPublication(page, 'http://127.0.0.1:9090/atom.xml', 'Atom Fixture');
    await expect(page.getByRole('heading', { name: 'Atom Fixture', exact: true })).toHaveCount(2);

    renderingPublicationPath = await createPublication(
      page, 'http://127.0.0.1:9090/rendering.json', 'Rendering Fixture');
  });

  test('publication posts are ordered, paginated twenty at a time, and have no detail-page links', async ({ page, request }) => {
    await signIn(page, request, adminEmail);
    const manyPath = await createPublication(
      page, 'http://127.0.0.1:9090/many.json', 'Many Posts Fixture');
    await page.goto(manyPath);
    const articles = page.locator('article');
    await expect(articles).toHaveCount(20);
    await expect(articles.first()).toContainText('Many post 21');
    await expect(articles.locator('a')).toHaveCount(0);
    await page.getByRole('link', { name: /Next page/ }).click();
    await expect(page).toHaveURL(/\?page=2$/);
    await expect(page.locator('article')).toHaveCount(1);
    await expect(page.locator('article')).toContainText('Many post 01');
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
    await openPublicationDialog(page);
    await page.getByPlaceholder('Website or feed URL')
      .fill('http://127.0.0.1:9090/descriptionless');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
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
    await openPublicationDialog(page);
    await page.getByPlaceholder('Website or feed URL').fill('http://127.0.0.1:9090/no-feed');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    const missingFeed = await noFeedResponse;
    expect(missingFeed.status()).toBe(422);

    await page.getByPlaceholder('Website or feed URL').fill('http://127.0.0.1:9090/multi');
    const discoveryResponse = page.waitForResponse((response) =>
      response.url().endsWith('/app/publications')
      && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    expect((await discoveryResponse).status()).toBe(204);
    await page.keyboard.press('Escape');
    await expect.poll(async () => {
      if (await page.locator('select').isVisible()) return true;
      if (await page.locator('select').count()
          && !(await page.locator('#add-publication').isVisible())) {
        await openPublicationDialog(page);
      }
      return page.locator('select').isVisible();
    }).toBe(true);
    await expect(page.locator('select option')).toHaveCount(3);
    await page.locator('select').selectOption('http://127.0.0.1:9090/feed.xml');
    const createResponse = page.waitForResponse((response) =>
      response.url().endsWith('/app/publications')
      && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Use this feed' }).click();
    expect((await createResponse).status()).toBe(204);
    await page.reload();
    await settle(page);

    const publicationLink = page.getByRole('link', { name: /Fixture Gazette/ }).last();
    await expect(publicationLink).toBeVisible();
    publicationPath = await publicationLink.getAttribute('href');
    await publicationLink.click();
    await expect(page.getByRole('heading', { name: 'Fixture Gazette' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'First post' })).toBeVisible();
    await expect(page.getByText('Hosted form', { exact: true })).toBeVisible();
    await expect(page.locator('textarea[readonly]')).toHaveValue(
      /<iframe[^>]+src="http:\/\/[^"/]+\/subscribe\//);
    await expect(page.getByRole('link', { name: 'Send' })).toHaveAttribute('aria-disabled', 'true');
    await expect(page.getByText('Add an active, confirmed subscriber to enable sending.'))
      .toBeVisible();
  });

  test('an owner syncs the feed and edits publication settings', async ({ page, request }) => {
    await signIn(page, request, adminEmail);
    await page.goto(publicationPath);
    await settle(page);

    await request.post('http://127.0.0.1:9090/control/feed-version?value=2');
    const syncResponse = page.waitForResponse((response) =>
      response.url().endsWith('/sync') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Sync', exact: true }).click();
    expect((await syncResponse).status()).toBe(204);
    await expect(page.getByRole('heading', { name: 'Second post' })).toBeVisible();

    await page.getByRole('link', { name: 'Settings' }).click();
    await expect(page.getByLabel('Feed URL')).toBeVisible();
    await settle(page);
    await page.getByLabel('Title', { exact: true }).fill('Unsaved preview title');
    await page.getByLabel('Description').fill('Unsaved preview description');
    await page.getByRole('button', { name: 'Preview subscribe form' }).click();
    const preview = page.locator('#settings-preview');
    const frame = page.frameLocator('#settings-preview iframe');
    await expect(preview).toBeVisible();
    await expect(frame.getByRole('heading', { name: 'Sign up for Unsaved preview title' }))
      .toBeVisible();
    await expect(frame.getByText('Unsaved preview description')).toBeVisible();
    await expect(frame.getByRole('heading', { name: 'Sign up for Unsaved preview title' }))
      .toHaveCSS('font-size', '18px');
    const formHeight = await page.locator('#settings-preview iframe')
      .evaluate((el) => el.clientHeight);
    await expect(frame.locator('input[type=email]')).toBeDisabled();
    await expect(frame.locator('.cf-turnstile, .h-captcha')).toHaveCount(0);
    await expect(page.locator('#settings-preview iframe'))
      .toHaveAttribute('srcdoc', /Sign up for Unsaved preview title/);
    const otherTab = await page.context().newPage();
    await otherTab.bringToFront();
    await page.bringToFront();
    await expect(frame.getByRole('heading', { name: 'Sign up for Unsaved preview title' }))
      .toBeVisible();
    await expect.poll(async () => page.locator('#settings-preview iframe')
      .evaluate((el) => el.clientHeight)).toBe(formHeight);
    await page.mouse.click(10, 10);
    await expect(preview).toBeHidden();
    await page.getByLabel('Title', { exact: true }).fill('Fixture Gazette');
    await page.getByLabel('Description').fill('News from the Playwright fixture.');
    await page.getByLabel('Intro').fill('<strong>Unsaved intro</strong>');
    await page.getByRole('button', { name: 'Preview email (one post)' }).click();
    await expect(preview).toBeVisible();
    await expect(frame.locator('strong')).toHaveText('Unsaved intro');
    await expect(frame.getByRole('heading', { name: 'Example post' }))
      .toBeVisible();
    await expect(page.locator('#settings-preview iframe'))
      .toHaveAttribute('srcdoc', /Example post/);
    const onePostHeight = await page.locator('#settings-preview iframe')
      .evaluate((el) => el.clientHeight);
    await otherTab.bringToFront();
    await page.bringToFront();
    await expect(frame.getByRole('heading', { name: 'Example post' }))
      .toBeVisible();
    await expect.poll(async () => page.locator('#settings-preview iframe')
      .evaluate((el) => el.clientHeight)).toBe(onePostHeight);
    await expect(frame.locator('body')).toHaveCSS('background-color', 'rgb(247, 247, 242)');
    await expect(frame.locator('body > div > div[style*="padding:16px"]'))
      .toHaveCSS('background-color', 'rgb(255, 255, 255)');
    await preview.getByRole('button', { name: 'Close' }).click();
    await page.getByRole('button', { name: 'Preview email (multiple posts)' }).click();
    await expect(preview).toBeVisible();
    await expect(frame.locator('article')).toHaveCount(2);
    await expect(page.locator('#settings-preview iframe'))
      .toHaveAttribute('srcdoc', /Another example post/);
    const multiPostHeight = await page.locator('#settings-preview iframe')
      .evaluate((el) => el.clientHeight);
    await otherTab.bringToFront();
    await page.bringToFront();
    await expect(frame.locator('article')).toHaveCount(2);
    await expect.poll(async () => page.locator('#settings-preview iframe')
      .evaluate((el) => el.clientHeight)).toBe(multiPostHeight);
    await otherTab.close();
    await preview.getByRole('button', { name: 'Close' }).click();
    await page.getByLabel('Intro').fill('News from the Playwright fixture.');

    await expect(page.getByLabel('Description')).toHaveValue('News from the Playwright fixture.');
    await expect(page.getByLabel('Intro')).toHaveValue('News from the Playwright fixture.');
    await expect(page.getByLabel('Automatic sending')).toBeChecked();

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
    await page.getByRole('button', { name: 'Preview email (one post)' }).click();
    await expect(frame.locator('img[alt="Fixture Gazette"]')).toBeVisible();
    await expect(frame.locator('img[alt="Fixture Gazette"]'))
      .toHaveJSProperty('naturalWidth', 1);
    await preview.getByRole('button', { name: 'Close' }).click();
    await page.getByLabel('Email style').selectOption('letter');
    await page.getByRole('button', { name: 'Preview email (one post)' }).click();
    await expect(frame.locator('div[style*="height:75px"]'))
      .toHaveCSS('background-image', /_mock\/cdn\/.+\.png/);
    await preview.getByRole('button', { name: 'Close' }).click();
    await page.getByLabel('Email style').selectOption('card');

    const authorForm = page.locator('form').filter({ hasText: 'Default author image' });
    const authorUploadResponse = page.waitForResponse((response) =>
      response.url().includes('/settings/image/author')
      && response.request().method() === 'POST');
    await authorForm.locator('input[type=file]').setInputFiles({
      name: 'author.png',
      mimeType: 'image/png',
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
        'base64'),
    });
    expect((await authorUploadResponse).status()).toBe(204);
    await expect(authorForm.locator('img')).toHaveAttribute('src', /_mock\/cdn\/.+\.png/);

    await page.getByLabel('Title', { exact: true }).fill('Updated Gazette');
    await expect(page.getByLabel('Reply-to email')).toHaveValue(adminEmail);
    await page.getByLabel('Reply-to email').fill('replies@example.test');
    await page.getByLabel('Address').fill('123 Test Street, Test City');
    await page.getByLabel('Description').fill('Updated publication description');
    await page.getByLabel('Intro').fill('A short introduction');
    await page.getByLabel('Default author name').fill('Fixture Editor');
    await page.getByLabel('Default author URL').fill('https://example.test/editor');
    const paddingPicker = page.getByLabel('Padding color');
    const paddingHex = paddingPicker.locator('..').locator('input[type=text]');
    await paddingHex.fill('#f0f1f2');
    await expect(paddingPicker).toHaveValue('#f0f1f2');
    await page.getByLabel('Background color').fill('#fafafa');
    await expect(page.getByLabel('Background color').locator('..').locator('input[type=text]'))
      .toHaveValue('#fafafa');
    await page.getByLabel('Text color').fill('#101112');
    await page.getByLabel('Primary color').fill('#123456');
    await page.getByLabel('Filter tag').fill('include-me');
    await page.getByLabel('Remove tag').fill('remove-me');
    await page.getByLabel('Welcome HTML').fill('<strong>Welcome aboard.</strong>');
    await page.getByLabel('Require confirmation').check();
    await page.getByLabel('Hide form title').check();

    const settingsResponse = page.waitForResponse((response) =>
      response.url().endsWith('/settings') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save settings' }).click();
    expect((await settingsResponse).status()).toBe(204);

    await page.getByRole('navigation', { name: 'Publication' })
      .getByRole('link', { name: 'Posts' }).click();
    await expect(page.getByRole('heading', { name: 'Updated Gazette' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Second post' })).toBeVisible();

    const subscribePagePromise = page.context().waitForEvent('page');
    await page.locator('section').getByRole('link').click();
    const subscribePage = await subscribePagePromise;
    await expect(subscribePage.locator('main')).toHaveAttribute('style', /background:#f0f1f2/);
    await expect(subscribePage.getByRole('button', { name: 'Subscribe' })).toHaveAttribute('style', /#123456/);
    await expect(subscribePage.locator('.cf-turnstile')).toHaveCount(0);
    await subscribePage.close();

    await page.getByRole('link', { name: 'Settings' }).click();
    await settle(page);
    await expect(paddingHex).toHaveValue('#f0f1f2');
    await expect(page.getByLabel('Hide form title')).toBeChecked();
    await expect(page.getByLabel('Default author URL')).toHaveValue('https://example.test/editor');
    await expect(page.getByLabel('Reply-to email')).toHaveValue('replies@example.test');
    await expect(page.getByLabel('Welcome HTML')).toHaveValue('<strong>Welcome aboard.</strong>');
    await page.getByLabel('Background color').fill('#f0f1f2');
    await page.getByRole('button', { name: 'Preview email (one post)' }).click();
    await expect(frame.locator('img[alt="Updated Gazette"]'))
      .toHaveJSProperty('naturalWidth', 1);
    await expect(frame.locator('body')).toHaveCSS('background-color', 'rgb(240, 241, 242)');
    await expect(frame.locator('body > div > div[style*="padding:16px"]'))
      .toHaveCSS('background-color', 'rgb(255, 255, 255)');
    await preview.getByRole('button', { name: 'Close' }).click();
    await page.getByLabel('Background color').fill('#fafafa');
    await page.getByLabel('Automatic sending').uncheck();
    const disableAutomaticResponse = page.waitForResponse((response) =>
      response.url().endsWith('/settings') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save settings' }).click();
    expect((await disableAutomaticResponse).status()).toBe(204);
    await page.reload();
    await settle(page);
    await expect(page.getByLabel('Automatic sending')).not.toBeChecked();
    await expect(page.getByLabel('Hide form title')).toBeChecked();
    await expect(bannerForm.locator('img')).toHaveAttribute('src', /_mock\/cdn\/.+\.png/);
    await expect(authorForm.locator('img')).toHaveAttribute('src', /_mock\/cdn\/.+\.png/);
    await page.getByLabel('Hide form title').uncheck();
    await page.getByLabel('Automatic sending').check();
    await page.getByLabel('Feed URL').fill('http://127.0.0.1:9090/feed.json');
    const feedChangeResponse = page.waitForResponse((response) =>
      response.url().endsWith('/settings') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save settings' }).click();
    expect((await feedChangeResponse).status()).toBe(204);
    await page.reload();
    await settle(page);
    await expect(page.getByLabel('Hide form title')).not.toBeChecked();

    await page.getByRole('navigation', { name: 'Publication' })
      .getByRole('link', { name: 'Posts' }).click();
    await expect(page.getByRole('heading', { name: 'Updated Gazette' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'JSON post', exact: true })).toBeVisible();
  });

  test('a reader confirms a subscription and receives a welcome email', async ({ page, request }) => {
    const subscribePath = publicationPath.replace('/app/publications/', '/subscribe/');
    await page.goto(subscribePath);
    await expect(page.getByRole('heading', { name: 'Updated Gazette' })).toBeVisible();
    await expect(page.locator('img')).toHaveCount(0);

    const subscribeResponse = page.waitForResponse((response) =>
      response.url().includes('/subscribe/') && response.request().method() === 'POST');
    await page.locator('input[type=email]').fill(`  ${confirmedEmail.toUpperCase()}  `);
    await page.getByRole('button', { name: 'Subscribe' }).click();
    expect((await subscribeResponse).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Check your inbox' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Sign up for Updated Gazette' }))
      .toBeHidden();

    const confirmation = await latestEmail(request, confirmedEmail, 'Confirm your subscription');
    expect(confirmation.subject).toBe('Confirm your subscription');
    expect(confirmation.from.name).toBe('Updated Gazette');
    expect(confirmation.reply_to).toEqual({
      email: 'replies@example.test', name: 'Updated Gazette',
    });
    const confirmationUrl = confirmation.text.match(/https?:\/\/\S+\/confirm\/\S+/)?.[0];
    expect(confirmationUrl).toBeTruthy();
    await page.goto(confirmationUrl);
    await expect(page.getByRole('heading', { name: 'Subscription confirmed' })).toBeVisible();

    const welcome = await latestEmail(request, confirmedEmail, 'Welcome');
    expect(welcome.subject).toBe('Welcome');
    expect(welcome.from.name).toBe('Updated Gazette');
    expect(welcome.reply_to.email).toBe('replies@example.test');
    expect(welcome.html).toContain('<strong>Welcome aboard.</strong>');
  });

  test('a no-confirmation subscription becomes active immediately and duplicate submissions reveal nothing', async ({ page, request }) => {
    await page.goto(renderingPublicationPath.replace('/app/publications/', '/subscribe/'));
    await expect(page.getByRole('heading', { name: 'Rendering Fixture' })).toBeVisible();
    await expect(page.getByText('Rendering fixture description.')).toBeVisible();
    await expect(page.locator('.cf-turnstile, .h-captcha')).toHaveCount(0);

    await submitSubscription(page, renderingPublicationPath, `  ${immediateEmail.toUpperCase()}  `);
    const welcome = await latestEmail(request, immediateEmail, 'Welcome');
    expect(welcome.subject).toBe('Welcome');
    expect(welcome.from.name).toBe('Rendering Fixture');
    expect(welcome.reply_to.email).toBe(adminEmail);
    expect(welcome.text).toContain('Thanks for subscribing.');

    const before = (await emails(request)).filter((message) =>
      message.to?.some((to) => to.email === immediateEmail)).length;
    await submitSubscription(page, renderingPublicationPath, immediateEmail);
    await page.waitForTimeout(250);
    const after = (await emails(request)).filter((message) =>
      message.to?.some((to) => to.email === immediateEmail)).length;
    expect(after).toBe(before);
    await expect(page.getByRole('heading', { name: 'Check your inbox' })).toBeVisible();
  });

  test('multi-post email rendering, current settings, and sent-post exclusion follow the spec', async ({ page, request }) => {
    await signIn(page, request, adminEmail);
    await page.goto(`${renderingPublicationPath}/settings`);
    await settle(page);
    await page.getByLabel('Address').fill('123 Test Street, Test City');
    const addressResponse = page.waitForResponse((response) =>
      response.url().endsWith('/settings') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save settings' }).click();
    expect((await addressResponse).status()).toBe(204);
    await page.goto(renderingPublicationPath);
    await settle(page);
    await expect(page.getByRole('link', { name: 'Send' })).not.toHaveAttribute('aria-disabled');
    await page.getByRole('link', { name: 'Send' }).click();

    await expect(page.getByText('Unusable post', { exact: true })).toHaveCount(0);
    for (const title of ['Older linked post', 'Newer linked post', 'Full text post']) {
      await page.getByText(title, { exact: true }).click();
    }

    const previewResponse = page.waitForResponse((response) =>
      response.url().endsWith('/send') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Preview' }).click();
    expect((await previewResponse).status()).toBe(204);
    await expect(page.getByRole('heading', { name: 'Older linked post' })).toBeVisible();

    const preview = page.frameLocator('[title="Newsletter preview"]');
    await expect(preview.getByText('Rendering fixture description.')).toBeVisible();
    await expect(preview.getByText('Post Author')).toBeVisible();
    await expect(preview.getByText('Fixture Staff')).toHaveCount(2);
    await expect(preview.getByText('A post without a URL keeps its full plain-text content.')).toBeVisible();
    await expect(preview.getByText('Plain <unsafe> text')).toBeVisible();
    await expect(preview.getByText('Unusable post')).toHaveCount(0);
    await expect(preview.locator('a[href="http://127.0.0.1:9090/posts/rendering-1"]').first()).toBeVisible();

    const settingsPage = await page.context().newPage();
    await settingsPage.goto(`${renderingPublicationPath}/settings`);
    await settle(settingsPage);
    await settingsPage.getByLabel('Title', { exact: true }).fill('Changed after preview');
    const settingsResponse = settingsPage.waitForResponse((response) =>
      response.url().endsWith('/settings') && response.request().method() === 'POST');
    await settingsPage.getByRole('button', { name: 'Save settings' }).click();
    expect((await settingsResponse).status()).toBe(204);
    await settingsPage.close();

    const confirmResponse = page.waitForResponse((response) =>
      response.url().endsWith('/send/confirm') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Send', exact: true }).click();
    expect((await confirmResponse).status()).toBe(204);
    const newsletter = await latestEmail(request, immediateEmail, 'Older linked post');
    expect(newsletter.from.name).toBe('Changed after preview');
    expect(newsletter.reply_to.email).toBe(adminEmail);
    expect(newsletter.html).toContain('Rendering fixture description.');
    expect(newsletter.html).not.toContain('x'.repeat(500));
    expect(newsletter.html.indexOf('Older linked post'))
      .toBeLessThan(newsletter.html.indexOf('Newer linked post'));
    expect(newsletter.html.indexOf('Newer linked post'))
      .toBeLessThan(newsletter.html.indexOf('Full text post'));

    await page.goto(`${renderingPublicationPath}/send`);
    await expect(page.locator('input[type=checkbox]')).toHaveCount(0);
    await page.goto(renderingPublicationPath);
    await expect(page.getByText(/^Sent /)).toHaveCount(3);
    await expect(page.getByRole('link', { name: 'Send' })).toHaveAttribute('aria-disabled', 'true');
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
    const csv = ['email,notes'];
    for (let index = 1; index <= 51; index += 1) {
      csv.push(`subscriber${String(index).padStart(3, '0')}@example.test,ordinary`);
    }
    csv.push('invalid-address,invalid');
    csv.push(`${deliveryEmail},duplicate`);
    csv.push('"quoted@example.test","a note with a comma, an escaped ""quote"", and\na newline"');
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
    await expect(page.getByRole('columnheader')).toHaveText([
      'Email', 'Subscribed at', 'Unsubscribed at', '',
    ]);
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
    const menuAction = row.locator('details').getByRole('button', { name: 'Unsubscribe' });
    await expect.poll(async () => {
      if (!(await menuAction.isVisible())) await row.locator('summary').click();
      await page.waitForTimeout(200);
      return menuAction.isVisible();
    }).toBe(true);
    await menuAction.click();
    await expect(row.getByRole('dialog')).toBeVisible();
    const unsubscribeResponse = page.waitForResponse((response) =>
      response.url().includes('/app/subscribers/') && response.request().method() === 'POST');
    await row.getByRole('dialog').getByRole('button', { name: 'Unsubscribe' }).click();
    expect((await unsubscribeResponse).status()).toBe(204);
    await expect(row.getByRole('button', { name: 'Unsubscribe' })).toHaveCount(0);

    const quotedSearchResponse = page.waitForResponse((response) =>
      response.url().endsWith('/subscribers') && response.request().method() === 'POST');
    await page.getByPlaceholder('Search email').fill('quoted@example.test');
    await page.getByRole('button', { name: 'Search' }).click();
    expect((await quotedSearchResponse).status()).toBe(204);
    await expect(page.locator('tr').filter({ hasText: 'quoted@example.test' })).toHaveCount(1);
    expect((await emails(request)).some((message) =>
      message.to?.some((to) => to.email === 'quoted@example.test'))).toBe(false);
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
    await page.route('**/send', async (route) => {
      if (route.request().method() === 'POST') {
        await new Promise((resolve) => setTimeout(resolve, 500));
      }
      await route.continue();
    });
    const previewResponse = page.waitForResponse((response) =>
      response.url().endsWith('/send') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Preview' }).click();
    await expect(page.getByRole('button', { name: 'Preparing preview…' })).toBeDisabled();
    expect((await previewResponse).status()).toBe(204);
    await page.unroute('**/send');
    await expect(page.getByRole('heading', { name: 'JSON post' })).toBeVisible();
    await expect(page.frameLocator('[title="Newsletter preview"]')
      .getByText('JSON feed body.')).toBeVisible();
    await expect(page.frameLocator('[title="Newsletter preview"]')
      .getByText('This post should not be in the newsletter.')).toHaveCount(0);

    await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
    await expect(page.getByRole('dialog')).toBeHidden();
    const repeatPreviewResponse = page.waitForResponse((response) =>
      response.url().endsWith('/send') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Preview' }).click();
    expect((await repeatPreviewResponse).status()).toBe(204);
    await expect(page.getByRole('dialog')).toBeVisible();

    const confirmResponse = page.waitForResponse((response) =>
      response.url().endsWith('/send/confirm') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Send', exact: true }).click();
    expect((await confirmResponse).status()).toBe(204);

    const newsletter = await latestEmail(request, deliveryEmail, 'JSON post');
    expect(newsletter.from.name).toBe('Updated Gazette');
    expect(newsletter.reply_to.email).toBe('replies@example.test');
    expect(newsletter.html).toContain('{{unsubscribe_url}}');
    expect(newsletter.html).toContain('max-width:596px');
    expect(newsletter.html).toContain('A short introduction');
    expect(newsletter.html).toContain('#123456');
    expect(newsletter.html).toContain('Read online');
    expect(newsletter.list_unsubscribe).toBeTruthy();
    expect(newsletter.headers).toContainEqual({
      name: 'List-Unsubscribe-Post',
      value: 'List-Unsubscribe=One-Click',
    });
    const unsubscribeUrl = newsletter.personalization[0].data.unsubscribe_url;
    const unsubscribeToken = new URL(unsubscribeUrl).pathname.split('/').pop();
    expect(unsubscribeToken.split('.')).toHaveLength(3);
    expect((await request.get('/unsubscribe/not-a-jwt')).status()).toBe(404);
    expect((await emails(request)).some((message) =>
      message.subject === 'JSON post'
      && message.to?.some((to) => to.email === 'subscriber051@example.test'))).toBe(false);

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

    await submitSubscription(page, publicationPath, deliveryEmail);
    const confirmation = await latestEmail(request, deliveryEmail, 'Confirm your subscription');
    const confirmationUrl = confirmation.text.match(/https?:\/\/\S+\/confirm\/\S+/)?.[0];
    expect(confirmationUrl).toBeTruthy();
    await page.goto(confirmationUrl);
    await expect(page.getByRole('heading', { name: 'Subscription confirmed' })).toBeVisible();
    await latestEmail(request, deliveryEmail, 'Welcome');

    await page.goto(`${publicationPath}/settings`);
    await settle(page);
    await page.getByLabel('Feed URL').fill('http://127.0.0.1:9090/atom.xml');
    const feedChangeResponse = page.waitForResponse((response) =>
      response.url().endsWith('/settings') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save settings' }).click();
    expect((await feedChangeResponse).status()).toBe(204);
    await page.getByRole('navigation', { name: 'Publication' })
      .getByRole('link', { name: 'Posts' }).click();
    await expect(page.getByRole('heading', { name: 'JSON post', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Linked Atom post' })).toBeVisible();
    await expect(page.getByText(/^Sent /)).toHaveCount(1);
  });

  test('mobile navigation, copy feedback, and archive confirmation follow the spec', async ({ page, request, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.setViewportSize({ width: 375, height: 812 });
    await signIn(page, request, adminEmail);

    await expect(page.locator('aside')).toBeHidden();
    await page.getByRole('button', { name: 'Open menu' }).click();
    const navigation = page.locator('#mobile-navigation');
    await expect(navigation).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(navigation).toBeHidden();

    await page.goto(atomPublicationPath);
    await settle(page);
    await page.getByRole('button', { name: 'Copy' }).click();
    const copy = page.getByRole('button', { name: 'Copied' });
    await expect(copy).toBeVisible();
    await expect(copy).not.toHaveClass(/text-primary|hover:underline/);
    await expect(page.getByRole('button', { name: 'Copy', exact: true })).toBeVisible();

    await page.goto(`${atomPublicationPath}/settings`);
    await settle(page);
    await expect(page.getByRole('heading', { name: 'Archive publication' })).toBeVisible();
    await expect(page.getByLabel('Title', { exact: true })).toHaveCSS('background-color', 'rgb(255, 255, 255)');
    await page.getByRole('button', { name: 'Archive publication' }).click();
    const dialog = page.locator('#archive-publication');
    await expect(dialog).toBeVisible();
    const box = await dialog.boundingBox();
    expect(Math.abs(box.x + box.width / 2 - 375 / 2)).toBeLessThan(2);
    const archiveResponse = page.waitForResponse((response) =>
      response.url().endsWith('/archive') && response.request().method() === 'POST');
    await dialog.getByRole('button', { name: 'Archive', exact: true }).click();
    expect((await archiveResponse).status()).toBe(204);
    await expect(page).toHaveURL(/\/app$/);
    await expect(page.getByRole('link', { name: 'Archived publications' })).toBeVisible();
  });
});
