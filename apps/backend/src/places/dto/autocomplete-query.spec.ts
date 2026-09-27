import { ValidationPipe } from '@nestjs/common';
import { AutocompleteQueryDto } from './autocomplete-query.dto';

/**
 * The decorators on this DTO are checked for existence in
 * common/validation/dto-decorators.spec.ts. This spec checks they do the job, through the same
 * ValidationPipe configuration main.ts installs, because a query string arrives as text: `limit`
 * reaches the pipe as "5", and without the Type transform it fails IsInt and takes the whole request
 * with it.
 */
const pipe = new ValidationPipe({ whitelist: true, transform: true });
const meta = { type: 'query' as const, metatype: AutocompleteQueryDto };

describe('AutocompleteQueryDto through the API validation pipe', () => {
  it('accepts a query and turns the text limit into a number', async () => {
    const result = await pipe.transform({ q: 'lisb', limit: '5' }, meta);
    expect(result).toBeInstanceOf(AutocompleteQueryDto);
    expect(result.q).toBe('lisb');
    expect(result.limit).toBe(5);
    expect(typeof result.limit).toBe('number');
  });

  it('accepts a query with no limit, so the handler applies its own default', async () => {
    const result = await pipe.transform({ q: 'tokyo' }, meta);
    expect(result.q).toBe('tokyo');
    expect(result.limit).toBeUndefined();
  });

  it('rejects a single character, which would match most of the planet', async () => {
    await expect(pipe.transform({ q: 'l' }, meta)).rejects.toThrow();
  });

  it('rejects a missing query rather than sending an empty term upstream', async () => {
    await expect(pipe.transform({}, meta)).rejects.toThrow();
  });

  it('rejects a limit outside the bound, so one caller cannot ask for a thousand rows', async () => {
    await expect(pipe.transform({ q: 'lisbon', limit: '999' }, meta)).rejects.toThrow();
    await expect(pipe.transform({ q: 'lisbon', limit: '0' }, meta)).rejects.toThrow();
  });

  it('rejects a limit that is not a number', async () => {
    await expect(pipe.transform({ q: 'lisbon', limit: 'many' }, meta)).rejects.toThrow();
  });

  it('strips an unknown property instead of passing it through', async () => {
    const result = await pipe.transform({ q: 'lisbon', apiKey: 'sneaky' }, meta);
    expect(result).not.toHaveProperty('apiKey');
  });

  it('rejects an over-long query', async () => {
    await expect(pipe.transform({ q: 'x'.repeat(200) }, meta)).rejects.toThrow();
  });
});
