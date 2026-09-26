const http = require('node:http');

const port = 9090;
let feedVersion = 1;

function atom() {
  return `<?xml version="1.0" encoding="utf-8"?>
    <feed xmlns="http://www.w3.org/2005/Atom">
      <title>Atom Fixture</title>
      <subtitle>An Atom publication.</subtitle>
      <link href="http://127.0.0.1:${port}/atom.xml" rel="self"/>
      <author>
        <name>Atom Author</name>
        <uri>http://127.0.0.1:${port}/authors/atom</uri>
      </author>
      <id>urn:fixture:atom</id>
      <updated>2026-09-22T06:00:00Z</updated>
      <entry>
        <title>Linked Atom post</title>
        <link href="http://127.0.0.1:${port}/posts/atom"/>
        <id>urn:fixture:atom:1</id>
        <updated>2026-09-22T06:00:00Z</updated>
        <content type="text">Atom body.</content>
      </entry>
    </feed>`;
}

function renderingFeed() {
  return JSON.stringify({
    version: 'https://jsonfeed.org/version/1.1',
    title: 'Rendering Fixture',
    description: 'Rendering fixture description.',
    home_page_url: `http://127.0.0.1:${port}/rendering`,
    feed_url: `http://127.0.0.1:${port}/rendering.json`,
    authors: [{
      name: 'Fixture Staff',
      url: `http://127.0.0.1:${port}/authors/staff`,
      avatar: `http://127.0.0.1:${port}/staff.png`,
    }],
    items: [{
      id: 'rendering-1',
      url: `http://127.0.0.1:${port}/posts/rendering-1`,
      title: 'Older linked post',
      date_published: '2026-09-20T06:00:00Z',
      content_text: `Plain <unsafe> text ${'x'.repeat(520)}`,
      authors: [{ name: 'Post Author', url: `http://127.0.0.1:${port}/authors/post` }],
      tags: ['Include Me'],
    }, {
      id: 'rendering-2',
      url: `http://127.0.0.1:${port}/posts/rendering-2`,
      title: 'Newer linked post',
      date_published: '2026-09-21T06:00:00Z',
      content_html: '<p>Newer linked body.</p>',
      tags: ['remove-me'],
    }, {
      id: 'rendering-3',
      title: 'Full text post',
      content_text: 'A post without a URL keeps its full plain-text content.',
    }, {
      id: 'rendering-empty',
      title: 'Unusable post',
    }],
  });
}

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

  if (url.pathname === '/redirect') {
    response.writeHead(302, { location: '/site' });
    return response.end();
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

  if (url.pathname === '/atom.xml') {
    return respond(response, 200, 'application/atom+xml', atom());
  }

  if (url.pathname === '/empty.json') {
    return respond(response, 200, 'application/feed+json', JSON.stringify({
      version: 'https://jsonfeed.org/version/1.1',
      title: 'Empty Fixture',
      items: [{ id: 'empty-1', title: 'Unusable post' }],
    }));
  }

  if (url.pathname === '/invalid-feed') {
    return respond(response, 200, 'application/rss+xml', 'not a feed');
  }

  if (url.pathname === '/rendering.json') {
    return respond(response, 200, 'application/feed+json', renderingFeed());
  }

  if (url.pathname === '/many.json') {
    return respond(response, 200, 'application/feed+json', JSON.stringify({
      version: 'https://jsonfeed.org/version/1.1',
      title: 'Many Posts Fixture',
      items: Array.from({ length: 21 }, (_, index) => ({
        id: `many-${index + 1}`,
        title: `Many post ${String(index + 1).padStart(2, '0')}`,
        content_text: `Body ${index + 1}`,
        date_published: `2026-09-${String(index + 1).padStart(2, '0')}T06:00:00Z`,
      })),
    }));
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
