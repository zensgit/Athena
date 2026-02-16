jest.mock('keycloak-js', () => {
  class KeycloakMock {
    authenticated = false;
    token?: string;
    tokenParsed?: unknown;
    subject?: string;

    init = jest.fn().mockResolvedValue(false);
    login = jest.fn().mockResolvedValue(undefined);
    logout = jest.fn().mockResolvedValue(undefined);
    updateToken = jest.fn().mockResolvedValue(true);
  }

  return {
    __esModule: true,
    default: KeycloakMock,
  };
});

jest.mock('axios', () => {
  const instance = {
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
    get: jest.fn().mockResolvedValue({ data: {} }),
    request: jest.fn().mockResolvedValue({ data: {} }),
    post: jest.fn().mockResolvedValue({ data: {} }),
    put: jest.fn().mockResolvedValue({ data: {} }),
    patch: jest.fn().mockResolvedValue({ data: {} }),
    delete: jest.fn().mockResolvedValue({ data: {} }),
  };

  return {
    __esModule: true,
    default: {
      create: jest.fn(() => instance),
    },
  };
});
