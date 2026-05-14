import api from './api';
import { User } from 'types';
import userGroupService, {
  Group,
  GroupPage,
  USER_GROUP_UNEXPECTED_RESPONSE_MESSAGE,
  UserPage,
} from './userGroupService';

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

const user: User = {
  id: 'user-1',
  username: 'alice',
  email: 'alice@example.com',
  roles: ['USER'],
  firstName: 'Alice',
  lastName: 'Anderson',
  enabled: true,
  locked: false,
};

const userWithNullableDetails: User = {
  id: 'user-2',
  username: 'bob',
  email: 'bob@example.com',
  roles: [],
};

const group: Group = {
  id: 'group-1',
  name: 'engineering',
  displayName: 'Engineering',
  description: 'Engineering team',
  email: 'engineering@example.com',
  enabled: true,
  groupType: 'TEAM',
  users: [user],
};

const groupWithNullableDetails: Group = {
  name: 'minimal',
};

const userPage: UserPage = {
  content: [user, userWithNullableDetails],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 100,
};

const groupPage: GroupPage = {
  content: [group, groupWithNullableDetails],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 100,
};

describe('userGroupService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded user content for searchUsers', async () => {
    mockedApi.get.mockResolvedValueOnce(userPage);

    await expect(userGroupService.searchUsers('al')).resolves.toEqual(userPage.content);

    expect(mockedApi.get).toHaveBeenCalledWith('/users', {
      params: { query: 'al', page: 0, size: 20 },
    });
  });

  it('returns guarded user content for listUsers', async () => {
    mockedApi.get.mockResolvedValueOnce(userPage);

    await expect(userGroupService.listUsers()).resolves.toEqual(userPage.content);

    expect(mockedApi.get).toHaveBeenCalledWith('/users', {
      params: { query: '', page: 0, size: 100 },
    });
  });

  it('rejects HTML fallback for listUsers', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(userGroupService.listUsers()).rejects.toThrow(
      USER_GROUP_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed user page items', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...userPage,
      content: [{ ...user, roles: 'USER' }],
    });

    await expect(userGroupService.listUsers()).rejects.toThrow(
      USER_GROUP_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded group content for listGroups', async () => {
    mockedApi.get.mockResolvedValueOnce(groupPage);

    await expect(userGroupService.listGroups()).resolves.toEqual(groupPage.content);

    expect(mockedApi.get).toHaveBeenCalledWith('/groups', {
      params: { page: 0, size: 100 },
    });
  });

  it('rejects HTML fallback for listGroups', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(userGroupService.listGroups()).rejects.toThrow(
      USER_GROUP_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed group page items', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...groupPage,
      content: [{ ...group, name: 42 }],
    });

    await expect(userGroupService.listGroups()).rejects.toThrow(
      USER_GROUP_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded createUser readback and forwards payload', async () => {
    const payload = {
      username: 'alice',
      email: 'alice@example.com',
      password: 'secret',
      firstName: 'Alice',
      lastName: 'Anderson',
    };
    mockedApi.post.mockResolvedValueOnce(user);

    await expect(userGroupService.createUser(payload)).resolves.toEqual(user);

    expect(mockedApi.post).toHaveBeenCalledWith('/users', payload);
  });

  it('rejects malformed createUser readback', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...user, email: null });

    await expect(
      userGroupService.createUser({
        username: 'alice',
        email: 'alice@example.com',
        password: 'secret',
      }),
    ).rejects.toThrow(USER_GROUP_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded updateUser readback and forwards payload', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...user, firstName: 'Alicia' });

    await expect(
      userGroupService.updateUser('alice', { firstName: 'Alicia' }),
    ).resolves.toEqual({ ...user, firstName: 'Alicia' });

    expect(mockedApi.put).toHaveBeenCalledWith('/users/alice', { firstName: 'Alicia' });
  });

  it('rejects malformed updateUser readback', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...user, roles: null });

    await expect(
      userGroupService.updateUser('alice', { firstName: 'Alicia' }),
    ).rejects.toThrow(USER_GROUP_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded createGroup readback and forwards payload', async () => {
    mockedApi.post.mockResolvedValueOnce(group);

    await expect(userGroupService.createGroup('engineering', 'Engineering')).resolves.toEqual(group);

    expect(mockedApi.post).toHaveBeenCalledWith('/groups', {
      name: 'engineering',
      displayName: 'Engineering',
    });
  });

  it('rejects malformed createGroup readback', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...group, name: null });

    await expect(userGroupService.createGroup('engineering')).rejects.toThrow(
      USER_GROUP_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('keeps deleteGroup wiring as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await userGroupService.deleteGroup('engineering');

    expect(mockedApi.delete).toHaveBeenCalledWith('/groups/engineering');
  });

  it('keeps addUserToGroup wiring as a no-content endpoint', async () => {
    mockedApi.post.mockResolvedValueOnce(undefined);

    await userGroupService.addUserToGroup('engineering', 'alice');

    expect(mockedApi.post).toHaveBeenCalledWith('/groups/engineering/members/alice');
  });

  it('keeps removeUserFromGroup wiring as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await userGroupService.removeUserFromGroup('engineering', 'alice');

    expect(mockedApi.delete).toHaveBeenCalledWith('/groups/engineering/members/alice');
  });
});
