import api from './api';
import calendarService, {
  CALENDAR_UNEXPECTED_RESPONSE_MESSAGE,
  CalendarEventDto,
  CalendarEventPage,
} from './calendarService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const event: CalendarEventDto = {
  id: 'event-1',
  siteId: 'engineering',
  title: 'Release planning',
  description: 'Plan the next release',
  location: 'Room 42',
  startDate: '2026-05-14T09:00:00',
  endDate: '2026-05-14T10:00:00',
  allDay: false,
  recurrence: null,
  createdBy: 'alice',
  createdDate: '2026-05-13T12:00:00',
};

const eventWithNullableDetails: CalendarEventDto = {
  ...event,
  description: null,
  location: null,
  recurrence: null,
};

const page: CalendarEventPage = {
  content: [event],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 50,
};

describe('calendarService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded calendar event pages', async () => {
    mockedApi.get.mockResolvedValueOnce(page);

    await expect(calendarService.listEvents('engineering', 1, 10)).resolves.toEqual(page);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites/engineering/calendar/events', {
      params: { page: 1, size: 10 },
    });
  });

  it('accepts calendar events with nullable optional detail fields', async () => {
    const nullablePage = {
      ...page,
      content: [eventWithNullableDetails],
    };
    mockedApi.get.mockResolvedValueOnce(nullablePage);

    await expect(calendarService.listEvents('engineering')).resolves.toEqual(nullablePage);
  });

  it('rejects HTML fallback for calendar event pages', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(calendarService.listEvents('engineering')).rejects.toThrow(
      CALENDAR_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed calendar event page items', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...page,
      content: [{ ...event, allDay: 'false' }],
    });

    await expect(calendarService.listEvents('engineering')).rejects.toThrow(
      CALENDAR_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded range event arrays', async () => {
    mockedApi.get.mockResolvedValueOnce([event]);

    await expect(
      calendarService.getEventsByRange('engineering', '2026-05-01T00:00', '2026-06-01T00:00')
    ).resolves.toEqual([event]);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites/engineering/calendar/events/range', {
      params: { from: '2026-05-01T00:00', to: '2026-06-01T00:00' },
    });
  });

  it('rejects malformed range event arrays', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...event, createdBy: null }]);

    await expect(
      calendarService.getEventsByRange('engineering', '2026-05-01T00:00', '2026-06-01T00:00')
    ).rejects.toThrow(CALENDAR_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded get-event readbacks', async () => {
    mockedApi.get.mockResolvedValueOnce(event);

    await expect(calendarService.getEvent('engineering', 'event-1')).resolves.toEqual(event);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites/engineering/calendar/events/event-1');
  });

  it('returns guarded create-event readbacks and forwards payload', async () => {
    const request = {
      title: 'Release planning',
      description: 'Plan the next release',
      location: 'Room 42',
      startDate: '2026-05-14T09:00:00',
      endDate: '2026-05-14T10:00:00',
      allDay: false,
      recurrence: 'FREQ=WEEKLY',
    };
    mockedApi.post.mockResolvedValueOnce({ ...event, recurrence: 'FREQ=WEEKLY' });

    await expect(calendarService.createEvent('engineering', request)).resolves.toEqual({
      ...event,
      recurrence: 'FREQ=WEEKLY',
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/sites/engineering/calendar/events', request);
  });

  it('rejects malformed create-event readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...event, startDate: null });

    await expect(
      calendarService.createEvent('engineering', {
        title: 'Release planning',
        startDate: '2026-05-14T09:00:00',
        endDate: '2026-05-14T10:00:00',
      })
    ).rejects.toThrow(CALENDAR_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded update-event readbacks and forwards payload', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...event, title: 'Updated planning' });

    await expect(
      calendarService.updateEvent('engineering', 'event-1', { title: 'Updated planning' })
    ).resolves.toEqual({ ...event, title: 'Updated planning' });

    expect(mockedApi.put).toHaveBeenCalledWith('/sites/engineering/calendar/events/event-1', {
      title: 'Updated planning',
    });
  });

  it('rejects malformed update-event readbacks', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...event, createdDate: 123 });

    await expect(
      calendarService.updateEvent('engineering', 'event-1', { title: 'Updated planning' })
    ).rejects.toThrow(CALENDAR_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('keeps delete endpoint wiring unchanged', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await calendarService.deleteEvent('engineering', 'event-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/sites/engineering/calendar/events/event-1');
  });
});
