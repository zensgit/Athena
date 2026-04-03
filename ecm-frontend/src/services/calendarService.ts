import api from './api';

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

class CalendarService {
  async listEvents(siteId: string, page = 0, size = 50): Promise<CalendarEventPage> {
    return api.get<CalendarEventPage>(`/sites/${siteId}/calendar/events`, { params: { page, size } });
  }

  async getEventsByRange(siteId: string, from: string, to: string): Promise<CalendarEventDto[]> {
    return api.get<CalendarEventDto[]>(`/sites/${siteId}/calendar/events/range`, { params: { from, to } });
  }

  async getEvent(siteId: string, eventId: string): Promise<CalendarEventDto> {
    return api.get<CalendarEventDto>(`/sites/${siteId}/calendar/events/${eventId}`);
  }

  async createEvent(siteId: string, data: {
    title: string; description?: string; location?: string;
    startDate: string; endDate: string; allDay?: boolean; recurrence?: string;
  }): Promise<CalendarEventDto> {
    return api.post<CalendarEventDto>(`/sites/${siteId}/calendar/events`, data);
  }

  async updateEvent(siteId: string, eventId: string, data: {
    title?: string; description?: string; location?: string;
    startDate?: string; endDate?: string; allDay?: boolean; recurrence?: string;
  }): Promise<CalendarEventDto> {
    return api.put<CalendarEventDto>(`/sites/${siteId}/calendar/events/${eventId}`, data);
  }

  async deleteEvent(siteId: string, eventId: string): Promise<void> {
    return api.delete(`/sites/${siteId}/calendar/events/${eventId}`);
  }
}

const calendarService = new CalendarService();
export default calendarService;
