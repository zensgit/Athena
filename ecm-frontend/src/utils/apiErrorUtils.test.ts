import { extractApiErrorResponse, formatApiErrorMessage } from './apiErrorUtils';

describe('apiErrorUtils', () => {
  it('extracts structured api error responses with details', () => {
    const error = {
      response: {
        data: {
          timestamp: '2026-03-30T10:00:00Z',
          status: 400,
          error: 'Bad Request',
          message: 'Property validation failed',
          path: '/api/v1/nodes/123/aspects',
          details: ['Missing mandatory property cm:title', 'Value must be one of [A, B]'],
        },
      },
    };

    expect(extractApiErrorResponse(error)).toEqual({
      timestamp: '2026-03-30T10:00:00Z',
      status: 400,
      error: 'Bad Request',
      message: 'Property validation failed',
      path: '/api/v1/nodes/123/aspects',
      details: ['Missing mandatory property cm:title', 'Value must be one of [A, B]'],
    });
  });

  it('formats api errors with bullet details', () => {
    const error = {
      response: {
        data: {
          message: 'Property validation failed',
          details: ['Missing mandatory property cm:title', 'Value must be one of [A, B]'],
        },
      },
    };

    expect(formatApiErrorMessage(error, 'Fallback')).toBe(
      "Property validation failed\n• Missing mandatory property cm:title\n• Value must be one of [A, B]"
    );
  });

  it('falls back to generic message when response data is not structured', () => {
    expect(formatApiErrorMessage(new Error('Network down'), 'Fallback')).toBe('Network down');
    expect(formatApiErrorMessage({}, 'Fallback')).toBe('Fallback');
  });
});
