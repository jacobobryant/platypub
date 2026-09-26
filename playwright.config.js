const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.js',
  fullyParallel: false,
  workers: 1,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  reporter: [['line']],
  use: {
    baseURL: 'http://127.0.0.1:8081',
    headless: true,
    trace: 'retain-on-failure',
  },
  webServer: [
    {
      command: 'node e2e/fixture-server.js',
      url: 'http://127.0.0.1:9090/site',
      reuseExistingServer: false,
      timeout: 10_000,
    },
    {
      command: 'bash e2e/start-app.sh',
      url: 'http://127.0.0.1:8081/',
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
});
