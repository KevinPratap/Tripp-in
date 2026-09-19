/**
 * The API runs a global ValidationPipe with whitelist: true. Any DTO declared as a
 * class must carry validation decorators on its fields, otherwise class-validator
 * treats every property as unknown and strips it, so the controller receives an
 * object with no fields and every request fails validation. This spec exists because
 * exactly that silently broke activity voting.
 *
 * Fields are listed explicitly: TypeScript fields without initialisers do not exist
 * at runtime, so they cannot be discovered by reflection.
 */
import { getMetadataStorage } from 'class-validator';
import { VoteActivityDto } from '../../trips/collab.controller';

const DTO_FIELDS: Array<[any, string[]]> = [
  [VoteActivityDto, ['activityId', 'voterName', 'vote', 'comment']]
];

describe('Request DTO validation metadata', () => {
  it.each(DTO_FIELDS)('%s decorates every declared field', (DtoClass, fields) => {
    const decorated = new Set(
      getMetadataStorage()
        .getTargetValidationMetadatas(DtoClass, '', false, false)
        .map((entry) => entry.propertyName)
    );

    for (const field of fields) {
      expect({ dto: DtoClass.name, field, decorated: decorated.has(field) }).toEqual({
        dto: DtoClass.name,
        field,
        decorated: true
      });
    }
  });
});
