import api from './api';

export const CALENDAR_UNEXPECTED_RESPONSE_MESSAGE =
  'Calendar endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

export interface CalendarEventDto {
  id: string;
  siteId: string;
  title: string;
  description?: string | null;
  location?: string | null;
  startDate: string;
  endDate: string;
  allDay: boolean;
  recurrence?: string | null;
  createdBy: string;
  createdDate: string;
}

export interface CalendarEventPage {
  content: CalendarEventDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const assertUnexpectedResponse = (): never => {
  throw new Error(CALENDAR_UNEXPECTED_RESPONSE_MESSAGE);
};

const isCalendarEventDto = (value: unknown): value is CalendarEventDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.siteId === 'string'
    && typeof value.title === 'string'
    && isStringOrNullish(value.description)
    && isStringOrNullish(value.location)
    && typeof value.startDate === 'string'
    && typeof value.endDate === 'string'
    && typeof value.allDay === 'boolean'
    && isStringOrNullish(value.recurrence)
    && typeof value.createdBy === 'string'
    && typeof value.createdDate === 'string';
};

const assertCalendarEventDto = (value: unknown): CalendarEventDto => (
  isCalendarEventDto(value) ? value : assertUnexpectedResponse()
);

const isCalendarEventArray = (value: unknown): value is CalendarEventDto[] => (
  Array.isArray(value) && value.every(isCalendarEventDto)
);

const assertCalendarEventArray = (value: unknown): CalendarEventDto[] => (
  isCalendarEventArray(value) ? value : assertUnexpectedResponse()
);

const isCalendarEventPage = (value: unknown): value is CalendarEventPage => {
  if (!isObject(value) || !Array.isArray(value.content)) {
    return false;
  }
  return value.content.every(isCalendarEventDto)
    && isNumber(value.totalElements)
    && isNumber(value.totalPages)
    && isNumber(value.number)
    && isNumber(value.size);
};

const assertCalendarEventPage = (value: unknown): CalendarEventPage => (
  isCalendarEventPage(value) ? value : assertUnexpectedResponse()
);

class CalendarService {
  async listEvents(siteId: string, page = 0, size = 50): Promise<CalendarEventPage> {
    const result = await api.get<unknown>(`/sites/${siteId}/calendar/events`, { params: { page, size } });
    return assertCalendarEventPage(result);
  }

  async getEventsByRange(siteId: string, from: string, to: string): Promise<CalendarEventDto[]> {
    const result = await api.get<unknown>(`/sites/${siteId}/calendar/events/range`, { params: { from, to } });
    return assertCalendarEventArray(result);
  }

  async getEvent(siteId: string, eventId: string): Promise<CalendarEventDto> {
    const result = await api.get<unknown>(`/sites/${siteId}/calendar/events/${eventId}`);
    return assertCalendarEventDto(result);
  }

  async createEvent(siteId: string, data: {
    title: string; description?: string; location?: string;
    startDate: string; endDate: string; allDay?: boolean; recurrence?: string;
  }): Promise<CalendarEventDto> {
    const result = await api.post<unknown>(`/sites/${siteId}/calendar/events`, data);
    return assertCalendarEventDto(result);
  }

  async updateEvent(siteId: string, eventId: string, data: {
    title?: string; description?: string; location?: string;
    startDate?: string; endDate?: string; allDay?: boolean; recurrence?: string;
  }): Promise<CalendarEventDto> {
    const result = await api.put<unknown>(`/sites/${siteId}/calendar/events/${eventId}`, data);
    return assertCalendarEventDto(result);
  }

  async deleteEvent(siteId: string, eventId: string): Promise<void> {
    return api.delete(`/sites/${siteId}/calendar/events/${eventId}`);
  }
}

const calendarService = new CalendarService();
export default calendarService;
