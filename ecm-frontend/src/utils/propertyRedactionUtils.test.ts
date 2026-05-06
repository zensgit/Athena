import {
  ENCRYPTED_PROPERTY_DISPLAY_VALUE,
  containsProtectedPropertyPayload,
  formatPropertyDisplayValue,
  isProtectedPropertyPayload,
  redactProtectedPropertyText,
  redactProtectedPropertyValue,
} from './propertyRedactionUtils';

describe('propertyRedactionUtils', () => {
  it('detects protected payload strings without matching ordinary enc text', () => {
    expect(isProtectedPropertyPayload('enc:v1:ciphertext')).toBe(true);
    expect(isProtectedPropertyPayload('enc:')).toBe(false);
    expect(isProtectedPropertyPayload('enc:v1')).toBe(false);
    expect(isProtectedPropertyPayload('plain')).toBe(false);
  });

  it('redacts protected payload strings recursively', () => {
    expect(redactProtectedPropertyValue({
      publicCode: 'PUB-1',
      secretCode: 'enc:v1:ciphertext',
      nested: { token: 'enc:v2:nested' },
      list: ['safe', 'enc:v3:list'],
    })).toEqual({
      publicCode: 'PUB-1',
      secretCode: ENCRYPTED_PROPERTY_DISPLAY_VALUE,
      nested: { token: ENCRYPTED_PROPERTY_DISPLAY_VALUE },
      list: ['safe', ENCRYPTED_PROPERTY_DISPLAY_VALUE],
    });
  });

  it('detects protected payload strings recursively', () => {
    expect(containsProtectedPropertyPayload({
      publicCode: 'PUB-1',
      nested: { token: 'enc:v2:nested' },
    })).toBe(true);
    expect(containsProtectedPropertyPayload({ publicCode: 'PUB-1' })).toBe(false);
  });

  it('redacts protected payloads inside highlighted markup', () => {
    expect(redactProtectedPropertyText('matched <em>enc:v1:ciphertext</em> value')).toBe(
      `matched <em>${ENCRYPTED_PROPERTY_DISPLAY_VALUE}</em> value`
    );
  });

  it('formats property display values without exposing protected payloads', () => {
    expect(formatPropertyDisplayValue('enc:v1:ciphertext')).toBe(ENCRYPTED_PROPERTY_DISPLAY_VALUE);
    expect(formatPropertyDisplayValue(['safe', 'enc:v1:ciphertext'])).toBe(`safe, ${ENCRYPTED_PROPERTY_DISPLAY_VALUE}`);
    expect(formatPropertyDisplayValue({ token: 'enc:v1:ciphertext' })).toBe(
      `{"token":"${ENCRYPTED_PROPERTY_DISPLAY_VALUE}"}`
    );
  });
});
