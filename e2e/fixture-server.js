const http = require('node:http');

const port = 9090;
let feedVersion = 1;

function rss() {
  const secondItem = feedVersion >= 2
    ? `
      <item>
        <guid>post-2</guid>
        <title>Second post</title>
        <link>http://127.0.0.1:${port}/posts/2</link>
        <pubDate>Mon, 21 Sep 2026 06:00:00 GMT</pubDate>
        <description><![CDATA[<p>The second post body.</p>]]></description>
      </item>`
    : '';

  return `<?xml version="1.0" encoding="UTF-8" ?>
    <rss version="2.0">
      <channel>
        <title>Fixture Gazette</title>
        <link>http://127.0.0.1:${port}/site</link>
        <description>News from the Playwright fixture.</description>
        <item>
          <guid>post-1</guid>
          <title>First post</title>
          <link>http://127.0.0.1:${port}/posts/1</link>
          <pubDate>Sun, 20 Sep 2026 06:00:00 GMT</pubDate>
          <description><![CDATA[<p>The first post body.</p>]]></description>
        </item>${secondItem}
      </channel>
    </rss>`;
}

function respond(response, status, contentType, body) {
  response.writeHead(status, { 'content-type': contentType });
  response.end(body);
}

const server = http.createServer((request, response) => {
  const url = new URL(request.url, `http://127.0.0.1:${port}`);

  if (request.method === 'POST' && url.pathname === '/control/feed-version') {
    feedVersion = Number(url.searchParams.get('value') || '1');
    return respond(response, 204, 'text/plain', '');
  }

  if (url.pathname === '/site') {
    return respond(response, 200, 'text/html', `
      <!doctype html><title>Fixture Gazette</title>
      <link rel="alternate" type="application/rss+xml" href="/feed.xml">`);
  }

  if (url.pathname === '/multi') {
    return respond(response, 200, 'text/html', `
      <!doctype html><title>Multiple feeds</title>
      <link rel="alternate" type="application/rss+xml" href="/feed.xml">
      <link rel="alternate" type="application/feed+json" href="/feed.json">`);
  }

  if (url.pathname === '/descriptionless') {
    return respond(response, 200, 'text/html', `
      <!doctype html><title>Descriptionless feed</title>
      <link rel="alternate" type="application/feed+json" href="/feed.json">`);
  }

  if (url.pathname === '/no-feed') {
    return respond(response, 200, 'text/html', '<!doctype html><title>No feed</title>');
  }

  if (url.pathname === '/feed.xml') {
    return respond(response, 200, 'application/rss+xml', rss());
  }

  if (url.pathname === '/feed.json') {
    return respond(response, 200, 'application/feed+json', JSON.stringify({
      version: 'https://jsonfeed.org/version/1.1',
      title: 'Fixture JSON Feed',
      home_page_url: `http://127.0.0.1:${port}/site`,
      feed_url: `http://127.0.0.1:${port}/feed.json`,
      items: [{
        id: 'json-post-1',
        url: `http://127.0.0.1:${port}/posts/json-1`,
        title: 'JSON post',
        content_html: '<p>JSON feed body.</p>',
      }, {
        id: 'json-post-2',
        url: `http://127.0.0.1:${port}/posts/json-2`,
        title: 'Unselected JSON post',
        content_html: '<p>This post should not be in the newsletter.</p>',
      }],
    }));
  }

  respond(response, 404, 'text/plain', 'Not found');
});

server.listen(port, '127.0.0.1', () => {
  console.log(`Fixture feed server listening on http://127.0.0.1:${port}`);
});
