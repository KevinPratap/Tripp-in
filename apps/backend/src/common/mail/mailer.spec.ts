import { ConsoleMailer, ResendMailer, createMailer } from './mailer';

describe('createMailer', () => {
  it('uses Resend when a key and a from address are both configured', () => {
    const mailer = createMailer({ RESEND_API_KEY: 're_test', MAIL_FROM: 'hello@trippin.app' } as NodeJS.ProcessEnv);
    expect(mailer).toBeInstanceOf(ResendMailer);
    expect(mailer.canDeliver).toBe(true);
    expect(mailer.name).toBe('resend');
  });

  it('falls back to the console when nothing is configured', () => {
    const mailer = createMailer({} as NodeJS.ProcessEnv);
    expect(mailer).toBeInstanceOf(ConsoleMailer);
    expect(mailer.canDeliver).toBe(false);
  });

  it('falls back to the console when the key is set but the from address is not', () => {
    // A half configured mailer would drop every sign in link silently. Better to say it is not
    // delivering than to accept messages nowhere.
    const mailer = createMailer({ RESEND_API_KEY: 're_test' } as NodeJS.ProcessEnv);
    expect(mailer.canDeliver).toBe(false);
  });

  it('ignores whitespace-only configuration', () => {
    const mailer = createMailer({ RESEND_API_KEY: '   ', MAIL_FROM: '  ' } as NodeJS.ProcessEnv);
    expect(mailer.canDeliver).toBe(false);
  });
});

describe('ConsoleMailer', () => {
  it('reports that it cannot deliver, so nothing built on it claims an email was sent', async () => {
    const mailer = new ConsoleMailer();
    expect(mailer.canDeliver).toBe(false);
    // Still resolves: the caller's flow is not broken by the absence of a provider.
    await expect(mailer.send({ to: 'a@b.com', subject: 's', text: 't' })).resolves.toBeUndefined();
  });
});

describe('ResendMailer', () => {
  const originalFetch = global.fetch;
  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('posts the message to Resend with the key as a bearer token', async () => {
    const fetchMock = jest.fn().mockResolvedValue({ ok: true, status: 200 });
    global.fetch = fetchMock as unknown as typeof fetch;

    await new ResendMailer('re_secret', 'hello@trippin.app').send({
      to: 'kevin@example.com',
      subject: 'Your link',
      text: 'plain',
      html: '<p>rich</p>'
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('https://api.resend.com/emails');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer re_secret');

    const body = JSON.parse(init.body);
    expect(body).toMatchObject({
      from: 'hello@trippin.app',
      to: ['kevin@example.com'],
      subject: 'Your link',
      text: 'plain',
      html: '<p>rich</p>'
    });
  });

  it('omits the html field entirely when there is no html body', async () => {
    const fetchMock = jest.fn().mockResolvedValue({ ok: true, status: 200 });
    global.fetch = fetchMock as unknown as typeof fetch;

    await new ResendMailer('re_secret', 'hello@trippin.app').send({
      to: 'kevin@example.com',
      subject: 's',
      text: 't'
    });

    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).not.toHaveProperty('html');
  });

  it('throws when the provider refuses, so the caller does not claim success', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 422,
      text: async () => 'domain not verified'
    }) as unknown as typeof fetch;

    await expect(
      new ResendMailer('re_secret', 'hello@trippin.app').send({ to: 'a@b.com', subject: 's', text: 't' })
    ).rejects.toThrow(/HTTP 422/);
  });

  it('keeps the api key out of the thrown error', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 401,
      text: async () => 'invalid key'
    }) as unknown as typeof fetch;

    await expect(
      new ResendMailer('re_supersecret', 'hello@trippin.app').send({ to: 'a@b.com', subject: 's', text: 't' })
    ).rejects.not.toThrow(/re_supersecret/);
  });

  it('propagates a network failure rather than swallowing it', async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error('socket hang up')) as unknown as typeof fetch;

    await expect(
      new ResendMailer('re_secret', 'hello@trippin.app').send({ to: 'a@b.com', subject: 's', text: 't' })
    ).rejects.toThrow(/socket hang up/);
  });
});
