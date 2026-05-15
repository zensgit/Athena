import api from './api';
import localizedContentService, {
  LOCALIZED_CONTENT_UNEXPECTED_RESPONSE_MESSAGE,
  LocalizedContentDto,
} from './localizedContentService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const fullDto: LocalizedContentDto = {
  id: 'lc-uuid-1',
  nodeId: 'node-uuid-1',
  locale: 'en-us',
  title: 'Welcome',
  description: 'Welcome to the document',
  createdDate: '2026-05-14T10:00:00',
  createdBy: 'admin',
  lastModifiedDate: '2026-05-14T11:00:00',
};

const nullableDto: LocalizedContentDto = {
  id: 'lc-uuid-2',
  nodeId: 'node-uuid-1',
  locale: 'fr',
  title: null,
  description: null,
  createdDate: '2026-05-14T10:00:00',
  createdBy: 'admin',
  lastModifiedDate: null,
};

const setNavigatorLanguages = (languages: readonly string[] | undefined): void => {
  Object.defineProperty(navigator, 'languages', {
    configurable: true,
    get: () => languages,
  });
  Object.defineProperty(navigator, 'language', {
    configurable: true,
    get: () => (languages && languages.length > 0 ? languages[0] : ''),
  });
};

describe('localizedContentService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('listLocalizations', () => {
    it('returns guarded array and accepts nullable detail fields', async () => {
      mockedApi.get.mockResolvedValueOnce([fullDto, nullableDto]);

      await expect(
        localizedContentService.listLocalizations('node-uuid-1'),
      ).resolves.toEqual([fullDto, nullableDto]);

      expect(mockedApi.get).toHaveBeenCalledWith('/nodes/node-uuid-1/localizations');
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(
        localizedContentService.listLocalizations('node-uuid-1'),
      ).rejects.toThrow(LOCALIZED_CONTENT_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('rejects non-array body', async () => {
      mockedApi.get.mockResolvedValueOnce({ items: [] });

      await expect(
        localizedContentService.listLocalizations('node-uuid-1'),
      ).rejects.toThrow(LOCALIZED_CONTENT_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('rejects malformed array entries with non-string locale', async () => {
      mockedApi.get.mockResolvedValueOnce([{ ...fullDto, locale: 42 }]);

      await expect(
        localizedContentService.listLocalizations('node-uuid-1'),
      ).rejects.toThrow(LOCALIZED_CONTENT_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('upsertLocalization', () => {
    it('returns guarded readback, forwards payload, and encodes locale path segment', async () => {
      mockedApi.put.mockResolvedValueOnce(fullDto);

      await expect(
        localizedContentService.upsertLocalization('node-uuid-1', 'zh-CN', {
          title: 'Welcome',
          description: 'Welcome to the document',
        }),
      ).resolves.toEqual(fullDto);

      expect(mockedApi.put).toHaveBeenCalledWith(
        '/nodes/node-uuid-1/localizations/zh-CN',
        { title: 'Welcome', description: 'Welcome to the document' },
      );
    });

    it('encodes locale path segments that need escaping', async () => {
      mockedApi.put.mockResolvedValueOnce(fullDto);

      await localizedContentService.upsertLocalization('node-uuid-1', 'zh CN', {});

      expect(mockedApi.put).toHaveBeenCalledWith(
        '/nodes/node-uuid-1/localizations/zh%20CN',
        {},
      );
    });

    it('rejects malformed readback', async () => {
      mockedApi.put.mockResolvedValueOnce({ ...fullDto, id: 42 });

      await expect(
        localizedContentService.upsertLocalization('node-uuid-1', 'en-us', {}),
      ).rejects.toThrow(LOCALIZED_CONTENT_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('rejects HTML fallback readback', async () => {
      mockedApi.put.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(
        localizedContentService.upsertLocalization('node-uuid-1', 'en-us', {}),
      ).rejects.toThrow(LOCALIZED_CONTENT_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('deleteLocalization', () => {
    it('wires delete as a no-content endpoint and encodes locale', async () => {
      mockedApi.delete.mockResolvedValueOnce(undefined);

      await localizedContentService.deleteLocalization('node-uuid-1', 'zh CN');

      expect(mockedApi.delete).toHaveBeenCalledWith(
        '/nodes/node-uuid-1/localizations/zh%20CN',
      );
    });
  });

  describe('resolveLocalization', () => {
    afterEach(() => {
      setNavigatorLanguages(['en-US']);
    });

    it('returns guarded readback and forwards joined navigator.languages Accept-Language header', async () => {
      setNavigatorLanguages(['zh-CN', 'zh', 'en']);
      mockedApi.get.mockResolvedValueOnce(fullDto);

      await expect(
        localizedContentService.resolveLocalization('node-uuid-1'),
      ).resolves.toEqual(fullDto);

      expect(mockedApi.get).toHaveBeenCalledWith(
        '/nodes/node-uuid-1/localization',
        { headers: { 'Accept-Language': 'zh-CN,zh,en' } },
      );
    });

    it('falls back to navigator.language when navigator.languages is empty', async () => {
      setNavigatorLanguages([]);
      Object.defineProperty(navigator, 'language', {
        configurable: true,
        get: () => 'fr-FR',
      });
      mockedApi.get.mockResolvedValueOnce(fullDto);

      await localizedContentService.resolveLocalization('node-uuid-1');

      expect(mockedApi.get).toHaveBeenCalledWith(
        '/nodes/node-uuid-1/localization',
        { headers: { 'Accept-Language': 'fr-FR' } },
      );
    });

    it("defaults to 'en' when both navigator.languages and navigator.language are empty", async () => {
      setNavigatorLanguages([]);
      Object.defineProperty(navigator, 'language', {
        configurable: true,
        get: () => '',
      });
      mockedApi.get.mockResolvedValueOnce(fullDto);

      await localizedContentService.resolveLocalization('node-uuid-1');

      expect(mockedApi.get).toHaveBeenCalledWith(
        '/nodes/node-uuid-1/localization',
        { headers: { 'Accept-Language': 'en' } },
      );
    });

    it('rejects malformed readback', async () => {
      setNavigatorLanguages(['en-US']);
      mockedApi.get.mockResolvedValueOnce({ ...fullDto, createdBy: null });

      await expect(
        localizedContentService.resolveLocalization('node-uuid-1'),
      ).rejects.toThrow(LOCALIZED_CONTENT_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('rejects HTML fallback readback', async () => {
      setNavigatorLanguages(['en-US']);
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(
        localizedContentService.resolveLocalization('node-uuid-1'),
      ).rejects.toThrow(LOCALIZED_CONTENT_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });
});
