import React, { Suspense, useDeferredValue, useEffect, useState } from 'react';
import {
  Alert,
  Avatar,
  Autocomplete,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  IconButton,
  InputAdornment,
  List,
  ListItem,
  ListItemAvatar,
  ListItemButton,
  ListItemText,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import {
  Add,
  ChatBubbleOutline,
  CheckCircle,
  Clear,
  ContentCopy,
  DeleteOutline,
  Edit,
  Group as GroupIcon,
  LockOutlined,
  NotificationsActive,
  NotificationsOff,
  Person,
  Refresh,
  Search,
  Star,
  Visibility,
  WorkspacePremium,
  ChevronRight,
} from '@mui/icons-material';
import { alpha } from '@mui/material/styles';
import { format } from 'date-fns';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAppSelector } from 'store';
import authService from 'services/authService';
import commentService, { Comment as MentionedComment } from 'services/commentService';
import followingService from 'services/followingService';
import nodeService from 'services/nodeService';
import peopleService, {
  PersonActivityItem,
  PersonFavoriteItem,
  PersonFavoriteSiteWriteRequest,
  PersonFavoriteWriteRequest,
  PersonFavoriteSiteItem,
  PersonProfileUpdateRequest,
  PersonPreferences,
  PersonSiteItem,
  PersonSiteMembershipRequestItem,
  PersonSiteMembershipRequestWriteRequest,
} from 'services/peopleService';
import { Group } from 'services/userGroupService';
import { Node, User } from 'types';
import { toast } from 'react-toastify';

const DocumentPreview = React.lazy(() => import('components/preview/DocumentPreview'));

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const SEARCH_PAGE_SIZE = 12;
const FAVORITES_PAGE_SIZE = 24;
const MODERATION_PAGE_SIZE = 6;
type FavoritePickerMode = 'ANY' | 'FOLDER_ONLY';
type SectionNodeScope = 'ALL' | 'DOCUMENT' | 'FOLDER' | 'OTHER';

const formatDateTime = (value?: string) => {
  if (!value) {
    return 'N/A';
  }
  try {
    return format(new Date(value), 'PPp');
  } catch {
    return value;
  }
};

const formatPersonName = (user?: User | null) => {
  if (!user) {
    return 'Select a person';
  }
  const label = `${user.firstName || ''} ${user.lastName || ''}`.trim();
  return label || user.username;
};

const getInitials = (user?: User | null) => {
  if (!user) {
    return '?';
  }
  const parts = [user.firstName, user.lastName].filter(Boolean);
  if (parts.length > 0) {
    return parts.map((part) => part?.charAt(0).toUpperCase()).join('').slice(0, 2);
  }
  return user.username.charAt(0).toUpperCase();
};

const buildMention = (username?: string | null) => {
  if (!username) {
    return '';
  }
  return `@${username}`;
};

const getPreferenceNamespace = (key: string) => {
  const dotIndex = key.indexOf('.');
  return dotIndex > 0 ? key.slice(0, dotIndex) : key;
};

type PreferenceNamespaceGroup = {
  namespace: string;
  entries: Array<[string, any]>;
};

const groupPreferencesByNamespace = (preferences: Record<string, any>): PreferenceNamespaceGroup[] => {
  const grouped = new Map<string, Array<[string, any]>>();
  Object.entries(preferences)
    .sort(([left], [right]) => left.localeCompare(right))
    .forEach(([key, value]) => {
      const namespace = getPreferenceNamespace(key);
      const current = grouped.get(namespace) || [];
      current.push([key, value]);
      grouped.set(namespace, current);
    });
  return Array.from(grouped.entries())
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([namespace, entries]) => ({ namespace, entries }));
};

const formatPreferenceValue = (value: unknown) => {
  if (value == null) {
    return 'N/A';
  }
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
};

const matchesFavoritePickerMode = (node: Node, mode: FavoritePickerMode) =>
  mode === 'FOLDER_ONLY' ? node.nodeType === 'FOLDER' : true;

const normalizeLookupKey = (value?: string | null) => value?.trim().toLowerCase() || '';

const addLookupKey = <T,>(map: Map<string, T>, value: string | undefined | null, item: T) => {
  const key = normalizeLookupKey(value);
  if (key) {
    map.set(key, item);
  }
};

const matchesAnyNormalizedText = (values: Array<string | undefined | null>, query: string) => {
  const normalizedQuery = normalizeLookupKey(query);
  if (!normalizedQuery) {
    return true;
  }
  return values.some((value) => normalizeLookupKey(value).includes(normalizedQuery));
};

const getSectionNodeScope = (nodeType?: string | null): Exclude<SectionNodeScope, 'ALL'> => {
  const normalizedNodeType = normalizeLookupKey(nodeType);
  if (normalizedNodeType === 'document') {
    return 'DOCUMENT';
  }
  if (normalizedNodeType === 'folder') {
    return 'FOLDER';
  }
  return 'OTHER';
};

const matchesSectionNodeScope = (nodeType: string | undefined | null, scope: SectionNodeScope) =>
  scope === 'ALL' || getSectionNodeScope(nodeType) === scope;

const isFolderNodeType = (nodeType?: string | null) => normalizeLookupKey(nodeType) === 'folder';

const getNodeKindLabel = (nodeType?: string | null) => {
  if (isFolderNodeType(nodeType)) {
    return 'Workspace folder';
  }
  if (normalizeLookupKey(nodeType) === 'document') {
    return 'Document';
  }
  if (normalizeLookupKey(nodeType)) {
    return 'Linked item';
  }
  return 'Unknown item';
};

const getNodeActionHint = (nodeType?: string | null) => {
  if (isFolderNodeType(nodeType)) {
    return 'Open in browser';
  }
  if (normalizeLookupKey(nodeType) === 'document') {
    return 'Preview / Discuss';
  }
  return 'Open in browser';
};

const getSectionNodeScopeCounts = <T extends { nodeType?: string | null }>(items: T[]) => {
  const counts: Record<SectionNodeScope, number> = {
    ALL: items.length,
    DOCUMENT: 0,
    FOLDER: 0,
    OTHER: 0,
  };

  items.forEach((item) => {
    counts[getSectionNodeScope(item.nodeType)] += 1;
  });

  return counts;
};

const useNodePickerSearch = (open: boolean, query: string, mode: FavoritePickerMode) => {
  const deferredQuery = useDeferredValue(query.trim());
  const [results, setResults] = useState<Node[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setResults([]);
      setLoading(false);
      setError(null);
      return;
    }

    if (deferredQuery.length < 2) {
      setResults([]);
      setLoading(false);
      setError(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    nodeService
      .searchNodes({
        name: deferredQuery,
        page: 0,
        size: 10,
        includeChildren: true,
        sortBy: 'name',
        sortDirection: 'asc',
      })
      .then(({ nodes }) => {
        if (cancelled) {
          return;
        }
        setResults(nodes.filter((node) => matchesFavoritePickerMode(node, mode)));
      })
      .catch(() => {
        if (!cancelled) {
          setResults([]);
          setError('Failed to search nodes');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [deferredQuery, mode, open]);

  return { results, loading, error };
};

const PeopleDirectoryPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const authUser = useAppSelector((state) => state.auth.user) ?? authService.getCurrentUser();
  const selectedUsernameFromQuery = searchParams.get('username')?.trim() || '';
  const [query, setQuery] = useState('');
  const deferredQuery = useDeferredValue(query.trim());
  const [searchPage, setSearchPage] = useState<PageResponse<User> | null>(null);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [selectedUsername, setSelectedUsername] = useState(selectedUsernameFromQuery || (authUser?.username ?? ''));
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [selectedGroups, setSelectedGroups] = useState<Group[]>([]);
  const [selectedFavorites, setSelectedFavorites] = useState<PageResponse<PersonFavoriteItem> | null>(null);
  const [favoritesScope, setFavoritesScope] = useState<SectionNodeScope>('ALL');
  const [favoritesFilter, setFavoritesFilter] = useState('');
  const deferredFavoritesFilter = useDeferredValue(favoritesFilter.trim());
  const [selectedAuthoredComments, setSelectedAuthoredComments] = useState<PageResponse<MentionedComment> | null>(null);
  const [selectedMentionedComments, setSelectedMentionedComments] = useState<PageResponse<MentionedComment> | null>(null);
  const [selectedPreferences, setSelectedPreferences] = useState<PersonPreferences | null>(null);
  const [selectedPreferenceNamespace, setSelectedPreferenceNamespace] = useState('');
  const [selectedPreferenceNamespaces, setSelectedPreferenceNamespaces] = useState<string[]>([]);
  const [followedUserIds, setFollowedUserIds] = useState<Set<string>>(new Set());
  const [isFollowingSelectedUser, setIsFollowingSelectedUser] = useState(false);
  const [followActionLoading, setFollowActionLoading] = useState(false);
  const [selectedActivities, setSelectedActivities] = useState<PersonActivityItem[]>([]);
  const [recentActivityScope, setRecentActivityScope] = useState<SectionNodeScope>('ALL');
  const [recentActivityFilter, setRecentActivityFilter] = useState('');
  const deferredRecentActivityFilter = useDeferredValue(recentActivityFilter.trim());
  const [authoredCommentScope, setAuthoredCommentScope] = useState<SectionNodeScope>('ALL');
  const [authoredCommentFilter, setAuthoredCommentFilter] = useState('');
  const deferredAuthoredCommentFilter = useDeferredValue(authoredCommentFilter.trim());
  const [mentionedCommentScope, setMentionedCommentScope] = useState<SectionNodeScope>('ALL');
  const [mentionedCommentFilter, setMentionedCommentFilter] = useState('');
  const deferredMentionedCommentFilter = useDeferredValue(mentionedCommentFilter.trim());
  const [selectedSites, setSelectedSites] = useState<PersonSiteItem[]>([]);
  const [sitesScope, setSitesScope] = useState<SectionNodeScope>('ALL');
  const [sitesFilter, setSitesFilter] = useState('');
  const deferredSitesFilter = useDeferredValue(sitesFilter.trim());
  const [selectedFavoriteSites, setSelectedFavoriteSites] = useState<PersonFavoriteSiteItem[]>([]);
  const [favoriteSitesScope, setFavoriteSitesScope] = useState<SectionNodeScope>('ALL');
  const [favoriteSitesFilter, setFavoriteSitesFilter] = useState('');
  const deferredFavoriteSitesFilter = useDeferredValue(favoriteSitesFilter.trim());
  const [selectedSiteMembershipRequests, setSelectedSiteMembershipRequests] = useState<PersonSiteMembershipRequestItem[]>([]);
  const [previewNode, setPreviewNode] = useState<Node | null>(null);
  const [previewCommentsOpen, setPreviewCommentsOpen] = useState(false);
  const [previewCommentDraftText, setPreviewCommentDraftText] = useState<string | null>(null);
  const [previewCommentId, setPreviewCommentId] = useState<string | null>(null);
  const [previewLoadingId, setPreviewLoadingId] = useState<string | null>(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileReloadToken, setProfileReloadToken] = useState(0);
  const [preferenceReloadToken, setPreferenceReloadToken] = useState(0);
  const [profileEditorOpen, setProfileEditorOpen] = useState(false);
  const [preferencesEditorOpen, setPreferencesEditorOpen] = useState(false);
  const [preferenceEntryEditorOpen, setPreferenceEntryEditorOpen] = useState(false);
  const [profileSaveLoading, setProfileSaveLoading] = useState(false);
  const [preferencesLoading, setPreferencesLoading] = useState(false);
  const [preferencesError, setPreferencesError] = useState<string | null>(null);
  const [siteRequestActionLoadingId, setSiteRequestActionLoadingId] = useState<string | null>(null);
  const [preferenceActionLoadingKey, setPreferenceActionLoadingKey] = useState<string | null>(null);
  const [preferenceEntryKey, setPreferenceEntryKey] = useState('');
  const [preferenceEntryValueText, setPreferenceEntryValueText] = useState('');
  const [siteMembershipRequestEditorOpen, setSiteMembershipRequestEditorOpen] = useState(false);
  const [siteMembershipRequestSaving, setSiteMembershipRequestSaving] = useState(false);
  const [siteMembershipRequestEditingSiteId, setSiteMembershipRequestEditingSiteId] = useState<string | null>(null);
  const [moderationSiteFilter, setModerationSiteFilter] = useState('');
  const deferredModerationSiteFilter = useDeferredValue(moderationSiteFilter.trim());
  const [moderationRequesterFilter, setModerationRequesterFilter] = useState('');
  const deferredModerationRequesterFilter = useDeferredValue(moderationRequesterFilter.trim());
  const [moderationStatusFilter, setModerationStatusFilter] = useState('PENDING');
  const [moderationPage, setModerationPage] = useState(0);
  const [moderationReloadToken, setModerationReloadToken] = useState(0);
  const [moderationRequestsPage, setModerationRequestsPage] = useState<PageResponse<PersonSiteMembershipRequestItem> | null>(null);
  const [moderationRequestsLoading, setModerationRequestsLoading] = useState(false);
  const [moderationRequestsError, setModerationRequestsError] = useState<string | null>(null);
  const [moderationActionLoadingKey, setModerationActionLoadingKey] = useState<string | null>(null);
  const [favoriteEditorOpen, setFavoriteEditorOpen] = useState(false);
  const [favoriteSiteEditorOpen, setFavoriteSiteEditorOpen] = useState(false);
  const [favoriteActionLoading, setFavoriteActionLoading] = useState(false);
  const [favoriteSiteActionLoading, setFavoriteSiteActionLoading] = useState(false);
  const [favoriteDraftNodeId, setFavoriteDraftNodeId] = useState('');
  const [favoriteDraftNode, setFavoriteDraftNode] = useState<Node | null>(null);
  const [favoriteNodeSearchQuery, setFavoriteNodeSearchQuery] = useState('');
  const favoriteNodeSearch = useNodePickerSearch(favoriteEditorOpen, favoriteNodeSearchQuery, 'ANY');
  const [favoriteSiteDraftNodeId, setFavoriteSiteDraftNodeId] = useState('');
  const [favoriteSiteDraftNode, setFavoriteSiteDraftNode] = useState<Node | null>(null);
  const [favoriteSiteSearchQuery, setFavoriteSiteSearchQuery] = useState('');
  const favoriteSiteSearch = useNodePickerSearch(favoriteSiteEditorOpen, favoriteSiteSearchQuery, 'FOLDER_ONLY');
  const [siteMembershipRequestDraft, setSiteMembershipRequestDraft] = useState<PersonSiteMembershipRequestWriteRequest>({
    siteId: '',
    siteTitle: '',
    role: '',
    message: '',
  });
  const [profileDraft, setProfileDraft] = useState<PersonProfileUpdateRequest>({
    displayName: '',
    firstName: '',
    lastName: '',
    phone: '',
    department: '',
    jobTitle: '',
    avatarUrl: '',
    locale: 'en_US',
    timezone: 'UTC',
  });
  const [preferencesDraftText, setPreferencesDraftText] = useState('{}');
  const canModerateRequests = Boolean(authUser?.roles?.includes('ROLE_ADMIN'));

  useEffect(() => {
    if (selectedUsernameFromQuery) {
      setSelectedUsername(selectedUsernameFromQuery);
      return;
    }
    if (!selectedUsername && authUser?.username) {
      setSelectedUsername(authUser.username);
    }
  }, [authUser?.username, selectedUsername, selectedUsernameFromQuery]);

  useEffect(() => {
    if (!authUser?.username) {
      setFollowedUserIds(new Set());
      return;
    }

    let cancelled = false;

    followingService
      .list()
      .then((subscriptions) => {
        if (cancelled) {
          return;
        }
        setFollowedUserIds(
          new Set(
            subscriptions
              .filter((subscription) => subscription.targetType === 'USER')
              .map((subscription) => subscription.targetId),
          ),
        );
      })
      .catch(() => {
        if (!cancelled) {
          setFollowedUserIds(new Set());
        }
      });

    return () => {
      cancelled = true;
    };
  }, [authUser?.username]);

  useEffect(() => {
    let cancelled = false;
    setSearchLoading(true);
    setSearchError(null);

    peopleService
      .search(deferredQuery, 0, SEARCH_PAGE_SIZE)
      .then((page) => {
        if (cancelled) {
          return;
        }
        setSearchPage(page);
      })
      .catch(() => {
        if (!cancelled) {
          setSearchError('Failed to load people directory search results');
          setSearchPage(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setSearchLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [deferredQuery]);

  useEffect(() => {
    if (!selectedUsername) {
      setSelectedUser(null);
      setSelectedGroups([]);
      setSelectedFavorites(null);
      setSelectedAuthoredComments(null);
      setSelectedMentionedComments(null);
      setSelectedPreferences(null);
      setSelectedPreferenceNamespaces([]);
      setPreferencesLoading(false);
      setPreferencesError(null);
      setSelectedActivities([]);
      setSelectedSites([]);
      setSelectedFavoriteSites([]);
      setSelectedSiteMembershipRequests([]);
      setSiteMembershipRequestEditorOpen(false);
      setSiteMembershipRequestEditingSiteId(null);
      setFavoriteEditorOpen(false);
      setFavoriteSiteEditorOpen(false);
      setFavoriteDraftNodeId('');
      setFavoriteSiteDraftNodeId('');
      setIsFollowingSelectedUser(false);
      setFollowActionLoading(false);
      setSiteMembershipRequestDraft({
        siteId: '',
        siteTitle: '',
        role: '',
        message: '',
      });
      setProfileError(null);
      return;
    }

    let cancelled = false;
    setProfileLoading(true);
    setProfileError(null);
    setSelectedAuthoredComments(null);
    setSelectedMentionedComments(null);

    Promise.all([
      peopleService.get(selectedUsername),
      peopleService.getGroups(selectedUsername),
      peopleService.getFavorites(selectedUsername, 0, FAVORITES_PAGE_SIZE),
      commentService.getUserComments(selectedUsername, 0, 6),
      commentService.getMentionedComments(selectedUsername, 0, 6),
      peopleService.getActivities(selectedUsername),
      peopleService.getSites(selectedUsername),
      peopleService.getFavoriteSites(selectedUsername),
      peopleService.getSiteMembershipRequests(selectedUsername),
      selectedUsername !== authUser?.username
        ? followingService.check('USER', selectedUsername).catch(() => false)
        : Promise.resolve(false),
    ])
      .then(([
      user,
      groups,
      favorites,
      authoredComments,
      mentionedComments,
      activities,
        sites,
        favoriteSites,
        siteMembershipRequests,
        following,
      ]) => {
        if (cancelled) {
          return;
        }
        setSelectedUser(user);
        setSelectedGroups(groups);
        setSelectedFavorites(favorites);
        setSelectedAuthoredComments(authoredComments);
        setSelectedMentionedComments(mentionedComments);
        setSelectedActivities(activities);
        setSelectedSites(sites);
        setSelectedFavoriteSites(favoriteSites);
        setSelectedSiteMembershipRequests(siteMembershipRequests);
        setIsFollowingSelectedUser(following);
      })
      .catch(() => {
        if (!cancelled) {
          setProfileError('Failed to load the selected profile');
          setSelectedUser(null);
          setSelectedGroups([]);
          setSelectedFavorites(null);
          setSelectedAuthoredComments(null);
          setSelectedMentionedComments(null);
          setSelectedPreferences(null);
          setSelectedPreferenceNamespaces([]);
          setPreferencesLoading(false);
          setPreferencesError(null);
          setSelectedActivities([]);
          setSelectedSites([]);
          setSelectedFavoriteSites([]);
          setSelectedSiteMembershipRequests([]);
          setIsFollowingSelectedUser(false);
          setSiteMembershipRequestEditorOpen(false);
          setSiteMembershipRequestEditingSiteId(null);
          setSiteMembershipRequestDraft({
            siteId: '',
            siteTitle: '',
            role: '',
            message: '',
          });
        }
      })
      .finally(() => {
        if (!cancelled) {
          setProfileLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [authUser?.username, profileReloadToken, selectedUsername]);

  useEffect(() => {
    if (!selectedUsername) {
      setSelectedPreferences(null);
      setSelectedPreferenceNamespaces([]);
      setPreferencesLoading(false);
      setPreferencesError(null);
      return;
    }

    let cancelled = false;
    setPreferencesLoading(true);
    setPreferencesError(null);

    Promise.all([
      peopleService.getPreferences(
        selectedUsername,
        selectedPreferenceNamespace ? `${selectedPreferenceNamespace}.` : undefined,
      ),
      peopleService.getPreferenceNamespaces(selectedUsername),
    ])
      .then(([preferences, namespaces]) => {
        if (cancelled) {
          return;
        }
        setSelectedPreferences(preferences);
        setSelectedPreferenceNamespaces(namespaces);
        if (selectedPreferenceNamespace && !namespaces.includes(selectedPreferenceNamespace)) {
          setSelectedPreferenceNamespace('');
        }
      })
      .catch(() => {
        if (!cancelled) {
          setSelectedPreferences(null);
          setSelectedPreferenceNamespaces([]);
          setPreferencesError('Failed to load preferences');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setPreferencesLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [preferenceReloadToken, selectedPreferenceNamespace, selectedUsername]);

  useEffect(() => {
    setModerationPage(0);
  }, [deferredModerationRequesterFilter, deferredModerationSiteFilter, moderationStatusFilter]);

  useEffect(() => {
    if (!canModerateRequests) {
      setModerationRequestsPage(null);
      setModerationRequestsError(null);
      setModerationRequestsLoading(false);
      return;
    }

    let cancelled = false;
    setModerationRequestsLoading(true);
    setModerationRequestsError(null);

    const normalizedStatus = moderationStatusFilter.trim();
    peopleService
      .getVisibleSiteMembershipRequests({
        siteId: deferredModerationSiteFilter || undefined,
        requester: deferredModerationRequesterFilter || undefined,
        status: normalizedStatus && normalizedStatus !== 'ALL' ? normalizedStatus : undefined,
        page: moderationPage,
        size: MODERATION_PAGE_SIZE,
      })
      .then((page) => {
        if (!cancelled) {
          setModerationRequestsPage(page);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setModerationRequestsPage(null);
          setModerationRequestsError('Failed to load site membership moderation queue');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setModerationRequestsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [
    canModerateRequests,
    deferredModerationRequesterFilter,
    deferredModerationSiteFilter,
    moderationPage,
    moderationReloadToken,
    moderationStatusFilter,
  ]);

  const activeUser = selectedUser ?? authUser ?? null;
  const groupCount = selectedGroups.length;
  const favoriteCount = selectedFavorites?.totalElements ?? 0;
  const authoredCommentCount = selectedAuthoredComments?.totalElements ?? 0;
  const mentionedCommentCount = selectedMentionedComments?.totalElements ?? 0;
  const siteCount = selectedSites.length;
  const favoriteSiteCount = selectedFavoriteSites.length;
  const activityCount = selectedActivities.length;
  const requestCount = selectedSiteMembershipRequests.length;
  const roleCount = activeUser?.roles?.length ?? 0;
  const isCurrentUser = Boolean(activeUser && authUser?.username === activeUser.username);
  const canEditProfile = Boolean(isCurrentUser && activeUser?.username);
  const filteredActivities = selectedActivities.filter((activity) =>
    matchesSectionNodeScope(activity.nodeType, recentActivityScope) &&
    matchesAnyNormalizedText(
      [activity.title, activity.summary, activity.type, activity.nodeName, activity.occurredAt],
      deferredRecentActivityFilter,
    ),
  );
  const filteredAuthoredComments =
    selectedAuthoredComments?.content?.filter((comment) =>
      matchesSectionNodeScope(comment.nodeType, authoredCommentScope) &&
      matchesAnyNormalizedText(
        [comment.author, comment.nodeName, comment.content, comment.created, comment.edited, ...(comment.mentionedUsers || [])],
        deferredAuthoredCommentFilter,
      ),
    ) ?? [];
  const filteredMentionedComments =
    selectedMentionedComments?.content?.filter((comment) =>
      matchesSectionNodeScope(comment.nodeType, mentionedCommentScope) &&
      matchesAnyNormalizedText(
        [comment.author, comment.nodeName, comment.content, comment.created, comment.edited, ...(comment.mentionedUsers || [])],
        deferredMentionedCommentFilter,
      ),
    ) ?? [];
  const filteredFavorites =
    selectedFavorites?.content?.filter((favorite) =>
      matchesSectionNodeScope(favorite.nodeType, favoritesScope) &&
      matchesAnyNormalizedText(
        [favorite.nodeName, favorite.nodeId, favorite.nodeType, favorite.createdAt],
        deferredFavoritesFilter,
      ),
    ) ?? [];
  const filteredSites = selectedSites.filter((site) =>
    matchesSectionNodeScope('FOLDER', sitesScope) &&
    matchesAnyNormalizedText(
      [site.title, site.siteId, site.description, site.role, site.visibility, site.createdAt, site.lastModifiedAt],
      deferredSitesFilter,
    ),
  );
  const filteredFavoriteSites = selectedFavoriteSites.filter((site) =>
    matchesSectionNodeScope(site.folderType || site.nodeType, favoriteSitesScope) &&
    matchesAnyNormalizedText(
      [site.title, site.siteId, site.nodeId, site.path, site.folderType, site.nodeType],
      deferredFavoriteSitesFilter,
    ),
  );
  const recentActivityScopeCounts = getSectionNodeScopeCounts(selectedActivities);
  const authoredCommentScopeCounts = getSectionNodeScopeCounts(selectedAuthoredComments?.content ?? []);
  const mentionedCommentScopeCounts = getSectionNodeScopeCounts(selectedMentionedComments?.content ?? []);
  const favoritesScopeCounts = getSectionNodeScopeCounts(selectedFavorites?.content ?? []);
  const sitesScopeCounts = getSectionNodeScopeCounts(selectedSites.map(() => ({ nodeType: 'FOLDER' })));
  const favoriteSitesScopeCounts = getSectionNodeScopeCounts(selectedFavoriteSites.map((site) => ({ nodeType: site.folderType || site.nodeType })));
  const favoritesHasFilter = Boolean(deferredFavoritesFilter) || favoritesScope !== 'ALL';
  const sitesHasFilter = Boolean(deferredSitesFilter) || sitesScope !== 'ALL';
  const favoriteSitesHasFilter = Boolean(deferredFavoriteSitesFilter) || favoriteSitesScope !== 'ALL';
  const recentActivityHasFilter = Boolean(deferredRecentActivityFilter);
  const authoredCommentHasFilter = Boolean(deferredAuthoredCommentFilter);
  const mentionedCommentHasFilter = Boolean(deferredMentionedCommentFilter);
  const selectedSiteLookup = new Map<string, PersonSiteItem>();
  selectedSites.forEach((site) => {
    addLookupKey(selectedSiteLookup, site.siteId, site);
    addLookupKey(selectedSiteLookup, site.title, site);
  });
  const selectedFavoriteSiteLookup = new Map<string, PersonFavoriteSiteItem>();
  selectedFavoriteSites.forEach((site) => {
    addLookupKey(selectedFavoriteSiteLookup, site.siteId, site);
    addLookupKey(selectedFavoriteSiteLookup, site.title, site);
    addLookupKey(selectedFavoriteSiteLookup, site.nodeId, site);
  });
  const selectedFavoriteLookup = new Map<string, PersonFavoriteItem>();
  selectedFavorites?.content.forEach((favorite) => {
    addLookupKey(selectedFavoriteLookup, favorite.nodeId, favorite);
    addLookupKey(selectedFavoriteLookup, favorite.nodeName, favorite);
  });
  const pendingSiteRequestLookup = new Map<string, PersonSiteMembershipRequestItem>();
  selectedSiteMembershipRequests
    .filter((request) => normalizeLookupKey(request.status) === 'pending')
    .forEach((request) => {
      addLookupKey(pendingSiteRequestLookup, request.siteId, request);
      addLookupKey(pendingSiteRequestLookup, request.siteTitle, request);
    });
  const preferenceEntries = Object.entries(selectedPreferences?.preferences || {})
    .sort(([left], [right]) => left.localeCompare(right));
  const preferenceGroups = groupPreferencesByNamespace(selectedPreferences?.preferences || {});
  const hasPreferenceNamespaceFilter = Boolean(selectedPreferenceNamespace);

  const handleToggleUserFollow = async () => {
    if (!activeUser?.username || activeUser.username === authUser?.username) {
      return;
    }
    setFollowActionLoading(true);
    try {
      if (isFollowingSelectedUser) {
        await followingService.unfollow('USER', activeUser.username);
        setIsFollowingSelectedUser(false);
        setFollowedUserIds((previous) => {
          const next = new Set(previous);
          next.delete(activeUser.username);
          return next;
        });
        toast.success('User unfollowed');
      } else {
        await followingService.follow('USER', activeUser.username);
        setIsFollowingSelectedUser(true);
        setFollowedUserIds((previous) => {
          const next = new Set(previous);
          next.add(activeUser.username);
          return next;
        });
        toast.success('User followed');
      }
    } catch {
      toast.error('Failed to update following');
    } finally {
      setFollowActionLoading(false);
    }
  };

  const openProfileEditor = () => {
    setProfileDraft({
      displayName: selectedPreferences?.displayName || '',
      firstName: selectedPreferences?.firstName || activeUser?.firstName || '',
      lastName: selectedPreferences?.lastName || activeUser?.lastName || '',
      phone: selectedPreferences?.phone || '',
      department: selectedPreferences?.department || '',
      jobTitle: selectedPreferences?.jobTitle || '',
      avatarUrl: selectedPreferences?.avatarUrl || '',
      locale: selectedPreferences?.locale || 'en_US',
      timezone: selectedPreferences?.timezone || 'UTC',
    });
    setProfileEditorOpen(true);
  };

  const openPreferencesEditor = () => {
    setPreferencesDraftText(JSON.stringify(selectedPreferences?.preferences || {}, null, 2));
    setPreferencesEditorOpen(true);
  };

  const handleExportPreferences = async () => {
    if (!activeUser?.username) {
      return;
    }

    setPreferenceActionLoadingKey('__export__');
    try {
      const exported = await peopleService.exportPreferences(activeUser.username);
      const blob = new Blob([JSON.stringify(exported, null, 2)], { type: 'application/json;charset=utf-8' });
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `${activeUser.username}-preferences-${new Date().toISOString().slice(0, 10)}.json`;
      anchor.rel = 'noreferrer';
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      window.URL.revokeObjectURL(url);
      toast.success('Preferences exported');
    } catch {
      toast.error('Failed to export preferences');
    } finally {
      setPreferenceActionLoadingKey(null);
    }
  };

  const openPreferenceEntryEditor = (key = '', value?: unknown) => {
    setPreferenceEntryKey(key || (selectedPreferenceNamespace ? `${selectedPreferenceNamespace}.` : ''));
    setPreferenceEntryValueText(
      value === undefined
        ? ''
        : typeof value === 'string'
          ? value
          : JSON.stringify(value, null, 2)
    );
    setPreferenceEntryEditorOpen(true);
  };

  const handleCopyMention = async (username?: string | null) => {
    const mention = buildMention(username);
    if (!mention) {
      return;
    }

    try {
      await navigator.clipboard.writeText(mention);
      toast.success(`Copied ${mention}`);
    } catch {
      toast.error('Failed to copy mention');
    }
  };

  const openDocumentPreview = async (
    node: Node,
    options?: {
      discuss?: boolean;
      draftText?: string | null;
      commentId?: string | null;
    }
  ) => {
    if (node.nodeType === 'FOLDER') {
      navigate(`/browse/${node.id}`);
      return;
    }

    setPreviewNode(node);
    setPreviewCommentsOpen(Boolean(options?.discuss));
    setPreviewCommentDraftText(
      options?.draftText ?? (options?.discuss && node.creator ? `@${node.creator} ` : null)
    );
    setPreviewCommentId(options?.commentId || null);
  };

  const openDocumentPreviewById = async (
    nodeId: string,
    options?: {
      discuss?: boolean;
      draftText?: string | null;
      commentId?: string | null;
    }
  ) => {
    if (!nodeId) {
      return;
    }

    setPreviewLoadingId(nodeId);
    try {
      const node = await nodeService.getNode(nodeId);
      await openDocumentPreview(node, options);
    } catch {
      toast.error('Failed to load document preview');
    } finally {
      setPreviewLoadingId((current) => (current === nodeId ? null : current));
    }
  };

  const closeDocumentPreview = () => {
    setPreviewNode(null);
    setPreviewCommentsOpen(false);
    setPreviewCommentDraftText(null);
    setPreviewCommentId(null);
  };

  const openNodeByType = (
    nodeId?: string | null,
    nodeType?: string | null,
    options?: {
      discuss?: boolean;
      draftText?: string | null;
      commentId?: string | null;
    }
  ) => {
    if (!nodeId) {
      return;
    }
    if (isFolderNodeType(nodeType)) {
      navigate(`/browse/${nodeId}`);
      return;
    }
    void openDocumentPreviewById(nodeId, options);
  };

  const handleFavoritePreview = (favorite: PersonFavoriteItem) => {
    openNodeByType(favorite.nodeId, favorite.nodeType);
  };

  const handleFavoriteDiscuss = (favorite: PersonFavoriteItem) => {
    openNodeByType(favorite.nodeId, favorite.nodeType, { discuss: true });
  };

  const isDocumentNodeType = (nodeType?: string | null) => normalizeLookupKey(nodeType) === 'document';

  const createFavoriteByNodeId = async (nodeId: string) => {
    if (!activeUser?.username || !nodeId) {
      return false;
    }
    const payload: PersonFavoriteWriteRequest = { nodeId };
    setFavoriteActionLoading(true);
    try {
      await peopleService.createFavorite(activeUser.username, payload);
      toast.success('Favorite added');
      setProfileReloadToken((value) => value + 1);
      return true;
    } catch {
      toast.error('Failed to add favorite');
      return false;
    } finally {
      setFavoriteActionLoading(false);
    }
  };

  const handleRemoveFavorite = async (nodeId?: string) => {
    if (!activeUser?.username || !nodeId) {
      return;
    }
    setFavoriteActionLoading(true);
    try {
      await peopleService.deleteFavorite(activeUser.username, nodeId);
      toast.success('Removed from favorites');
      setProfileReloadToken((value) => value + 1);
    } catch {
      toast.error('Failed to remove favorite');
    } finally {
      setFavoriteActionLoading(false);
    }
  };

  const openFavoriteEditor = () => {
    if (!canEditProfile) {
      return;
    }
    setFavoriteDraftNodeId('');
    setFavoriteDraftNode(null);
    setFavoriteNodeSearchQuery('');
    setFavoriteEditorOpen(true);
  };

  const closeFavoriteEditor = () => {
    setFavoriteEditorOpen(false);
    setFavoriteDraftNodeId('');
    setFavoriteDraftNode(null);
    setFavoriteNodeSearchQuery('');
  };

  const handleCreateFavorite = async () => {
    if (!activeUser?.username) {
      return;
    }
    const nodeId = favoriteDraftNodeId.trim();
    if (!nodeId) {
      toast.error('Node ID is required');
      return;
    }
    const created = await createFavoriteByNodeId(nodeId);
    if (created) {
      closeFavoriteEditor();
    }
  };

  const handleQuickFavoriteToggle = async (nodeId?: string, nodeType?: string) => {
    if (!canEditProfile || !nodeId || !isDocumentNodeType(nodeType)) {
      return;
    }
    const favoriteMatch = selectedFavoriteLookup.get(normalizeLookupKey(nodeId));
    if (favoriteMatch?.nodeId) {
      await handleRemoveFavorite(favoriteMatch.nodeId);
      return;
    }
    await createFavoriteByNodeId(nodeId);
  };

  const renderDocumentQuickActions = (
    nodeId?: string,
    nodeType?: string,
    options?: {
      previewLabel: string;
      discussLabel: string;
      favoriteFallbackLabel?: string;
      onPreview?: () => void;
      onDiscuss?: () => void;
      draftText?: string | null;
      commentId?: string | null;
    }
  ) => {
    if (!nodeId || !isDocumentNodeType(nodeType)) {
      return undefined;
    }
    const favoriteMatch = selectedFavoriteLookup.get(normalizeLookupKey(nodeId));
    const favoriteLabel = favoriteMatch ? 'Remove favorite' : 'Add favorite';
    const favoriteAriaLabel = `${favoriteLabel} ${options?.favoriteFallbackLabel || options?.previewLabel}`;

    return (
      <Stack direction="row" spacing={0.5}>
        <IconButton
          edge="end"
          size="small"
          aria-label={options?.previewLabel}
          disabled={previewLoadingId === nodeId}
          onClick={() => {
            if (options?.onPreview) {
              options.onPreview();
              return;
            }
            void openDocumentPreviewById(nodeId);
          }}
        >
          <Visibility fontSize="small" />
        </IconButton>
        <IconButton
          edge="end"
          size="small"
          aria-label={options?.discussLabel}
          disabled={previewLoadingId === nodeId}
          onClick={() => {
            if (options?.onDiscuss) {
              options.onDiscuss();
              return;
            }
            void openDocumentPreviewById(nodeId, {
              discuss: true,
              draftText: options?.draftText ?? null,
              commentId: options?.commentId ?? null,
            });
          }}
        >
          <ChatBubbleOutline fontSize="small" />
        </IconButton>
        {canEditProfile && (
          <IconButton
            edge="end"
            size="small"
            aria-label={favoriteAriaLabel}
            disabled={favoriteActionLoading}
            onClick={() => void handleQuickFavoriteToggle(nodeId, nodeType)}
          >
            {favoriteMatch ? <DeleteOutline fontSize="small" /> : <Star fontSize="small" />}
          </IconButton>
        )}
      </Stack>
    );
  };

  const openFavoriteSiteEditor = () => {
    if (!canEditProfile) {
      return;
    }
    setFavoriteSiteDraftNodeId('');
    setFavoriteSiteDraftNode(null);
    setFavoriteSiteSearchQuery('');
    setFavoriteSiteEditorOpen(true);
  };

  const openFavoriteSiteEditorForSearch = (searchQuery: string) => {
    openFavoriteSiteEditor();
    setFavoriteSiteSearchQuery(searchQuery);
    setFavoriteSiteDraftNodeId('');
    setFavoriteSiteDraftNode(null);
  };

  const closeFavoriteSiteEditor = () => {
    setFavoriteSiteEditorOpen(false);
    setFavoriteSiteDraftNodeId('');
    setFavoriteSiteDraftNode(null);
    setFavoriteSiteSearchQuery('');
  };

  const handleCreateFavoriteSite = async () => {
    if (!activeUser?.username) {
      return;
    }
    const nodeId = favoriteSiteDraftNodeId.trim();
    if (!nodeId) {
      toast.error('Workspace folder node ID is required');
      return;
    }

    const payload: PersonFavoriteSiteWriteRequest = { nodeId };
    setFavoriteSiteActionLoading(true);
    try {
      await peopleService.createFavoriteSite(activeUser.username, payload);
      toast.success('Favorite site added');
      closeFavoriteSiteEditor();
      setProfileReloadToken((value) => value + 1);
    } catch {
      toast.error('Failed to add favorite site');
    } finally {
      setFavoriteSiteActionLoading(false);
    }
  };

  const handleRemoveFavoriteSite = async (siteId?: string) => {
    if (!activeUser?.username || !siteId) {
      return;
    }
    try {
      await peopleService.deleteFavoriteSite(activeUser.username, siteId);
      toast.success('Removed favorite site');
      setProfileReloadToken((value) => value + 1);
    } catch {
      toast.error('Failed to remove favorite site');
    }
  };

  const handleQuickFavoriteSiteToggle = (site: PersonSiteItem) => {
    if (!canEditProfile) {
      return;
    }

    const favoriteMatch =
      selectedFavoriteSiteLookup.get(normalizeLookupKey(site.title)) ||
      selectedFavoriteSiteLookup.get(normalizeLookupKey(site.siteId));

    if (favoriteMatch?.nodeId) {
      void handleRemoveFavoriteSite(favoriteMatch.siteId || favoriteMatch.nodeId);
      return;
    }

    openFavoriteSiteEditorForSearch(site.title || site.siteId);
  };

  const handleSiteOpen = (nodeId?: string | null) => {
    if (!nodeId) {
      return;
    }
    navigate(`/browse/${nodeId}`);
  };

  const handleFavoriteNodeSelect = (node: Node | null) => {
    setFavoriteDraftNode(node);
    setFavoriteDraftNodeId(node?.id || '');
    if (!node) {
      setFavoriteNodeSearchQuery('');
      return;
    }
    setFavoriteNodeSearchQuery(node.name || node.id);
  };

  const handleFavoriteSiteNodeSelect = (node: Node | null) => {
    setFavoriteSiteDraftNode(node);
    setFavoriteSiteDraftNodeId(node?.id || '');
    if (!node) {
      setFavoriteSiteSearchQuery('');
      return;
    }
    setFavoriteSiteSearchQuery(node.name || node.id);
  };

  const handleMentionedCommentPreview = (comment: MentionedComment) => {
    openNodeByType(comment.nodeId, comment.nodeType);
  };

  const handleMentionedCommentDiscuss = (comment: MentionedComment) => {
    openNodeByType(comment.nodeId, comment.nodeType, {
      discuss: true,
      draftText: comment.author ? `@${comment.author} ` : null,
      commentId: comment.id,
    });
  };

  const handleAuthoredCommentPreview = (comment: MentionedComment) => {
    openNodeByType(comment.nodeId, comment.nodeType);
  };

  const handleAuthoredCommentDiscuss = (comment: MentionedComment) => {
    openNodeByType(comment.nodeId, comment.nodeType, {
      discuss: true,
      draftText: comment.author ? `@${comment.author} ` : null,
      commentId: comment.id,
    });
  };

  const renderCommentQuickActions = (
    comment: MentionedComment,
    options: {
      previewLabel: string;
      discussLabel: string;
      favoriteFallbackLabel?: string;
      draftText?: string | null;
      onPreview: () => void;
      onDiscuss: () => void;
    }
  ) => {
    if (!comment.nodeId) {
      return undefined;
    }

    if (isDocumentNodeType(comment.nodeType)) {
      return renderDocumentQuickActions(comment.nodeId, comment.nodeType, {
        previewLabel: options.previewLabel,
        discussLabel: options.discussLabel,
        favoriteFallbackLabel: options.favoriteFallbackLabel || comment.nodeName || comment.id,
        draftText: options.draftText ?? null,
        commentId: comment.id,
        onPreview: options.onPreview,
        onDiscuss: options.onDiscuss,
      });
    }

    return (
      <Stack direction="row" spacing={0.5}>
        <IconButton
          edge="end"
          size="small"
          aria-label={options.previewLabel}
          disabled={previewLoadingId === comment.nodeId}
          onClick={options.onPreview}
        >
          <Visibility fontSize="small" />
        </IconButton>
        <IconButton
          edge="end"
          size="small"
          aria-label={options.discussLabel}
          disabled={previewLoadingId === comment.nodeId}
          onClick={options.onDiscuss}
        >
          <ChatBubbleOutline fontSize="small" />
        </IconButton>
      </Stack>
    );
  };

  const handleActivityPreview = (activity: PersonActivityItem) => {
    openNodeByType(activity.nodeId, activity.nodeType);
  };

  const handleActivityDiscuss = (activity: PersonActivityItem) => {
    openNodeByType(activity.nodeId, activity.nodeType, {
      discuss: true,
      draftText: buildMention(activeUser?.username) || null,
    });
  };

  const handleRecentActivityOpen = (activity: PersonActivityItem) => {
    if (!activity.nodeId) {
      return;
    }

    if (isFolderNodeType(activity.nodeType)) {
      navigate(`/browse/${activity.nodeId}`);
      return;
    }

    openNodeByType(activity.nodeId, activity.nodeType);
  };

  const renderActivityQuickActions = (activity: PersonActivityItem) => {
    if (!activity.nodeId) {
      return undefined;
    }

    if (isDocumentNodeType(activity.nodeType)) {
      return renderDocumentQuickActions(activity.nodeId, activity.nodeType, {
        previewLabel: `Preview activity ${activity.title}`,
        discussLabel: `Discuss activity ${activity.title}`,
        favoriteFallbackLabel: activity.nodeName || activity.title || activity.id,
        draftText: buildMention(activeUser?.username) || null,
        onPreview: () => handleActivityPreview(activity),
        onDiscuss: () => handleActivityDiscuss(activity),
      });
    }

    return (
      <Button
        size="small"
        variant="outlined"
        startIcon={<ChevronRight fontSize="small" />}
        onClick={() => handleRecentActivityOpen(activity)}
      >
        {isFolderNodeType(activity.nodeType) ? 'Open workspace' : 'Open item'}
      </Button>
    );
  };

  const renderScopeChips = (
    scope: SectionNodeScope,
    setScope: React.Dispatch<React.SetStateAction<SectionNodeScope>>,
    ariaLabelPrefix: string,
    counts: Record<SectionNodeScope, number>,
  ) => (
    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
      {(
        [
          { value: 'ALL', label: 'All' },
          { value: 'DOCUMENT', label: 'Documents' },
          { value: 'FOLDER', label: 'Folders' },
          { value: 'OTHER', label: 'Other' },
        ] satisfies Array<{ value: SectionNodeScope; label: string }>
      ).map((option) => (
        <Chip
          key={`${ariaLabelPrefix}-${option.value}`}
          size="small"
          label={`${option.label} (${counts[option.value] ?? 0})`}
          variant={scope === option.value ? 'filled' : 'outlined'}
          color={scope === option.value ? 'primary' : 'default'}
          clickable
          onClick={() => setScope(option.value)}
          aria-label={`${ariaLabelPrefix} ${option.label} ${counts[option.value] ?? 0} items`}
        />
      ))}
    </Stack>
  );

  const handleSaveProfile = async () => {
    if (!activeUser?.username) {
      return;
    }
    setProfileSaveLoading(true);
    try {
      const updated = await peopleService.updateProfile(activeUser.username, profileDraft);
      setSelectedPreferences(updated);
      setSelectedUser((current) => current ? ({
        ...current,
        firstName: updated.firstName || '',
        lastName: updated.lastName || '',
      }) : current);
      toast.success('Profile updated');
      setProfileEditorOpen(false);
    } catch {
      toast.error('Failed to update profile');
    } finally {
      setProfileSaveLoading(false);
    }
  };

  const handleSavePreferences = async () => {
    if (!activeUser?.username) {
      return;
    }
    let parsed: Record<string, any>;
    try {
      parsed = preferencesDraftText.trim() ? JSON.parse(preferencesDraftText) : {};
    } catch {
      toast.error('Preferences JSON is invalid');
      return;
    }

    setProfileSaveLoading(true);
    try {
      await peopleService.importPreferences(activeUser.username, parsed);
      setPreferenceReloadToken((current) => current + 1);
      toast.success('Preferences imported');
      setPreferencesEditorOpen(false);
    } catch {
      toast.error('Failed to import preferences');
    } finally {
      setProfileSaveLoading(false);
    }
  };

  const handleSavePreferenceEntry = async () => {
    if (!activeUser?.username) {
      return;
    }
    const normalizedKey = preferenceEntryKey.trim();
    if (!normalizedKey) {
      toast.error('Preference key is required');
      return;
    }

    const rawValue = preferenceEntryValueText.trim();
    let parsedValue: any = rawValue;
    if (!rawValue) {
      parsedValue = null;
    } else {
      try {
        parsedValue = JSON.parse(rawValue);
      } catch {
        parsedValue = rawValue;
      }
    }

    setPreferenceActionLoadingKey(normalizedKey);
    try {
      await peopleService.setPreference(activeUser.username, normalizedKey, parsedValue);
      setPreferenceReloadToken((current) => current + 1);
      toast.success(`Preference ${normalizedKey} saved`);
      setPreferenceEntryEditorOpen(false);
    } catch {
      toast.error('Failed to save preference');
    } finally {
      setPreferenceActionLoadingKey(null);
    }
  };

  const handleDeletePreference = async (key: string) => {
    if (!activeUser?.username || !key) {
      return;
    }
    setPreferenceActionLoadingKey(key);
    try {
      await peopleService.deletePreference(activeUser.username, key);
      setPreferenceReloadToken((current) => current + 1);
      toast.success(`Preference ${key} removed`);
    } catch {
      toast.error('Failed to remove preference');
    } finally {
      setPreferenceActionLoadingKey(null);
    }
  };

  const handleClearPreferences = async () => {
    if (!activeUser?.username) {
      return;
    }
    if (!window.confirm('Clear all stored preferences for this profile?')) {
      return;
    }
    setPreferenceActionLoadingKey('__all__');
    try {
      await peopleService.clearPreferences(activeUser.username);
      setPreferenceReloadToken((current) => current + 1);
      toast.success('All preferences cleared');
    } catch {
      toast.error('Failed to clear preferences');
    } finally {
      setPreferenceActionLoadingKey(null);
    }
  };

  const openSiteMembershipRequestEditor = (request?: PersonSiteMembershipRequestItem) => {
    if (!canEditProfile) {
      return;
    }

    setSiteMembershipRequestEditingSiteId(request?.siteId || null);
    setSiteMembershipRequestDraft({
      siteId: request?.siteId || '',
      siteTitle: request?.siteTitle || '',
      role: request?.role || 'Contributor',
      message: request?.message || '',
    });
    setSiteMembershipRequestEditorOpen(true);
  };

  const closeSiteMembershipRequestEditor = () => {
    setSiteMembershipRequestEditorOpen(false);
    setSiteMembershipRequestEditingSiteId(null);
    setSiteMembershipRequestDraft({
      siteId: '',
      siteTitle: '',
      role: '',
      message: '',
    });
  };

  const handleSaveSiteMembershipRequest = async () => {
    if (!activeUser?.username) {
      return;
    }

    const siteId = siteMembershipRequestDraft.siteId.trim();
    if (!siteId) {
      toast.error('Site ID is required');
      return;
    }

    const payload: PersonSiteMembershipRequestWriteRequest = {
      siteId,
      siteTitle: (siteMembershipRequestDraft.siteTitle || '').trim() || siteId,
      role: (siteMembershipRequestDraft.role || '').trim() || undefined,
      message: (siteMembershipRequestDraft.message || '').trim() || undefined,
    };

    setSiteMembershipRequestSaving(true);
    try {
      if (siteMembershipRequestEditingSiteId) {
        await peopleService.updateSiteMembershipRequest(activeUser.username, siteMembershipRequestEditingSiteId, payload);
        toast.success('Membership request updated');
      } else {
        await peopleService.createSiteMembershipRequest(activeUser.username, payload);
        toast.success('Membership request created');
      }
      closeSiteMembershipRequestEditor();
      setProfileReloadToken((value) => value + 1);
    } catch {
      toast.error('Failed to save membership request');
    } finally {
      setSiteMembershipRequestSaving(false);
    }
  };

  const handleWithdrawSiteMembershipRequest = async (siteId?: string) => {
    if (!activeUser?.username || !siteId) {
      return;
    }
    setSiteRequestActionLoadingId(siteId);
    try {
      await peopleService.withdrawSiteMembershipRequest(activeUser.username, siteId);
      setSelectedSiteMembershipRequests((current) => current.filter((item) => item.siteId !== siteId));
      toast.success('Membership request withdrawn');
    } catch {
      toast.error('Failed to withdraw membership request');
    } finally {
      setSiteRequestActionLoadingId((current) => (current === siteId ? null : current));
    }
  };

  const openFavoriteSiteRequestEditor = (site: PersonFavoriteSiteItem) => {
    if (!canEditProfile) {
      return;
    }

    const existingRequest =
      pendingSiteRequestLookup.get(normalizeLookupKey(site.title)) ||
      pendingSiteRequestLookup.get(normalizeLookupKey(site.siteId));

    if (existingRequest) {
      openSiteMembershipRequestEditor(existingRequest);
      return;
    }

    openSiteMembershipRequestEditor({
      siteId: site.title || site.siteId || '',
      siteTitle: site.title || site.siteId || '',
      role: 'Contributor',
      message: '',
      status: 'PENDING',
    } as PersonSiteMembershipRequestItem);
  };

  const moderationActionKey = (request: PersonSiteMembershipRequestItem, decision: 'APPROVED' | 'REJECTED') =>
    `${request.username || 'unknown'}:${request.siteId || 'unknown'}:${decision}`;

  const handleModerateSiteMembershipRequest = async (
    request: PersonSiteMembershipRequestItem,
    decision: 'APPROVED' | 'REJECTED'
  ) => {
    if (!request.username || !request.siteId) {
      return;
    }

    const actionKey = moderationActionKey(request, decision);
    setModerationActionLoadingKey(actionKey);
    try {
      if (decision === 'APPROVED') {
        await peopleService.approveSiteMembershipRequest(request.username, request.siteId);
      } else {
        await peopleService.rejectSiteMembershipRequest(request.username, request.siteId);
      }
      toast.success(`Membership request ${decision.toLowerCase()}`);
      setProfileReloadToken((value) => value + 1);
      setModerationReloadToken((value) => value + 1);
    } catch {
      toast.error(`Failed to ${decision.toLowerCase()} membership request`);
    } finally {
      setModerationActionLoadingKey((current) => (current === actionKey ? null : current));
    }
  };

  return (
    <Box
      sx={{
        position: 'relative',
        overflow: 'hidden',
        minHeight: 'calc(100vh - 128px)',
        '&::before': {
          content: '""',
          position: 'absolute',
          inset: 0,
          background: 'radial-gradient(circle at top right, rgba(25,118,210,0.16), transparent 34%), radial-gradient(circle at 12% 18%, rgba(0,150,136,0.14), transparent 28%), linear-gradient(180deg, rgba(250,252,255,1) 0%, rgba(245,248,252,1) 100%)',
          pointerEvents: 'none',
        },
      }}
    >
      <Box sx={{ position: 'relative', zIndex: 1 }}>
        <Paper
          elevation={0}
          sx={{
            p: 3,
            mb: 2,
            border: '1px solid',
            borderColor: 'divider',
            background: `linear-gradient(135deg, ${alpha('#1565c0', 0.08)} 0%, ${alpha('#26a69a', 0.12)} 100%)`,
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          <Box
            sx={{
              position: 'absolute',
              inset: 'auto -64px -64px auto',
              width: 220,
              height: 220,
              borderRadius: '50%',
              background: alpha('#1565c0', 0.08),
              filter: 'blur(2px)',
            }}
          />
          <Stack spacing={1.5} sx={{ position: 'relative' }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" gap={2} flexWrap="wrap">
              <Box>
                <Typography variant="overline" color="primary.main" fontWeight={700}>
                  Directory
                </Typography>
                <Typography variant="h4" fontWeight={700} gutterBottom>
                  People Directory
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 760 }}>
                  Search the directory, inspect a profile, review group membership, and jump directly to a user&apos;s favorites.
                </Typography>
              </Box>
              <Button
                variant="outlined"
                startIcon={<Refresh />}
                onClick={() => setProfileReloadToken((value) => value + 1)}
                disabled={profileLoading}
              >
                Refresh profile
              </Button>
            </Box>

            <Grid container spacing={1.5}>
              <Grid item xs={12} sm={4}>
                <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'background.paper' }}>
                  <Typography variant="caption" color="text.secondary">
                    Directory search
                  </Typography>
                  <Typography variant="h6">{searchPage?.totalElements ?? 0}</Typography>
                </Paper>
              </Grid>
              <Grid item xs={12} sm={4}>
                <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'background.paper' }}>
                  <Typography variant="caption" color="text.secondary">
                    Sites
                  </Typography>
                  <Typography variant="h6">{siteCount}</Typography>
                </Paper>
              </Grid>
              <Grid item xs={12} sm={4}>
                <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'background.paper' }}>
                  <Typography variant="caption" color="text.secondary">
                    Activities
                  </Typography>
                  <Typography variant="h6">{activityCount}</Typography>
                </Paper>
              </Grid>
            </Grid>
          </Stack>
        </Paper>

        <Grid container spacing={2}>
          <Grid item xs={12} lg={4}>
            <Paper
              sx={{
                p: 2,
                position: { lg: 'sticky' },
                top: { lg: 96 },
                border: '1px solid',
                borderColor: 'divider',
              }}
            >
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={1.5}>
                <Box>
                  <Typography variant="h6">Search</Typography>
                  <Typography variant="caption" color="text.secondary">
                    Find users by username, name, or email
                  </Typography>
                </Box>
                <Search color="primary" />
              </Box>

              <TextField
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search people..."
                fullWidth
                size="small"
                autoComplete="off"
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search fontSize="small" />
                    </InputAdornment>
                  ),
                  endAdornment: query ? (
                    <InputAdornment position="end">
                      <IconButton size="small" onClick={() => setQuery('')} aria-label="Clear search">
                        <Clear fontSize="small" />
                      </IconButton>
                    </InputAdornment>
                  ) : null,
                }}
              />

              <Box display="flex" justifyContent="space-between" alignItems="center" mt={1.5} mb={1}>
                <Typography variant="caption" color="text.secondary">
                  {searchLoading
                    ? 'Searching...'
                    : searchPage
                      ? `${searchPage.totalElements} people found`
                      : 'Waiting for results'}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Page {searchPage?.number ? searchPage.number + 1 : 1}
                </Typography>
              </Box>

              {searchError && (
                <Alert severity="warning" sx={{ mb: 2 }}>
                  {searchError}
                </Alert>
              )}

              {searchLoading && !searchPage ? (
                <Box display="flex" justifyContent="center" py={4}>
                  <CircularProgress size={24} />
                </Box>
              ) : (
                <List dense disablePadding sx={{ maxHeight: { lg: 'calc(100vh - 320px)' }, overflow: 'auto' }}>
                  {(searchPage?.content || []).map((user, index) => {
                    const selected = activeUser?.username === user.username;
                    const name = formatPersonName(user);
                    return (
                      <React.Fragment key={user.username}>
                        {index > 0 && <Divider />}
                        <ListItem disablePadding>
                          <ListItemButton
                            selected={selected}
                            onClick={() => setSelectedUsername(user.username)}
                            sx={{
                              borderRadius: 1,
                              my: 0.5,
                              alignItems: 'flex-start',
                            }}
                          >
                            <ListItemAvatar>
                              <Avatar sx={{ bgcolor: selected ? 'primary.main' : 'grey.300', color: selected ? 'primary.contrastText' : 'text.primary' }}>
                                {getInitials(user)}
                              </Avatar>
                            </ListItemAvatar>
                            <ListItemText
                              primary={
                                <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
                                  <Typography variant="subtitle2" component="span">
                                    {name}
                                  </Typography>
                                  {user.username === authUser?.username && (
                                    <Chip size="small" label="You" color="primary" variant="outlined" />
                                  )}
                                  {user.username !== authUser?.username && followedUserIds.has(user.username) && (
                                    <Chip size="small" label="Following" color="primary" />
                                  )}
                                  {user.enabled === false && <Chip size="small" label="Disabled" color="default" />}
                                </Stack>
                              }
                              secondary={
                                <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                                  <Typography variant="caption" color="text.secondary">
                                    @{user.username}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {user.email}
                                  </Typography>
                                  <Box>
                                    <Chip
                                      size="small"
                                      label={`@${user.username}`}
                                      icon={<ContentCopy fontSize="small" />}
                                      variant="outlined"
                                      onClick={() => void handleCopyMention(user.username)}
                                    />
                                  </Box>
                                </Stack>
                              }
                            />
                          </ListItemButton>
                        </ListItem>
                      </React.Fragment>
                    );
                  })}
                </List>
              )}

              {!searchLoading && searchPage && searchPage.content.length === 0 && (
                <Alert severity="info" sx={{ mt: 2 }}>
                  No people matched your query.
                </Alert>
              )}
            </Paper>
          </Grid>

          <Grid item xs={12} lg={8}>
            <Stack spacing={2}>
              <Paper
                sx={{
                  p: 3,
                  border: '1px solid',
                  borderColor: 'divider',
                  background: `linear-gradient(135deg, ${alpha('#0d47a1', 0.06)} 0%, ${alpha('#00897b', 0.08)} 100%)`,
                }}
              >
                {profileLoading && !selectedUser ? (
                  <Box display="flex" justifyContent="center" py={6}>
                    <CircularProgress />
                  </Box>
                ) : profileError ? (
                  <Alert severity="warning">{profileError}</Alert>
                ) : activeUser ? (
                  <Stack spacing={2}>
                    <Box display="flex" alignItems="center" gap={2} flexWrap="wrap">
                      <Avatar sx={{ width: 72, height: 72, bgcolor: 'primary.main', fontSize: '1.4rem' }}>
                        {getInitials(activeUser)}
                      </Avatar>
                      <Box flex={1} minWidth={0}>
                        <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap" sx={{ mb: 0.5 }}>
                          <Typography variant="h4" fontWeight={700} noWrap>
                            {formatPersonName(activeUser)}
                          </Typography>
                          {isCurrentUser && <Chip size="small" color="primary" label="Current user" />}
                          {activeUser.enabled ? (
                            <Chip size="small" color="success" icon={<CheckCircle fontSize="small" />} label="Enabled" />
                          ) : (
                            <Chip size="small" color="default" icon={<LockOutlined fontSize="small" />} label="Disabled" />
                          )}
                          {activeUser.locked && <Chip size="small" color="warning" label="Locked" />}
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          @{activeUser.username} • {activeUser.email}
                        </Typography>
                        {(selectedPreferences?.jobTitle || selectedPreferences?.department || selectedPreferences?.phone) && (
                          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                            {[selectedPreferences?.jobTitle, selectedPreferences?.department, selectedPreferences?.phone]
                              .filter(Boolean)
                              .join(' • ')}
                          </Typography>
                        )}
                        <Box display="flex" gap={1} flexWrap="wrap" mt={1}>
                          <Chip
                            size="small"
                            label={`@${activeUser.username}`}
                            icon={<ContentCopy fontSize="small" />}
                            variant="outlined"
                            onClick={() => void handleCopyMention(activeUser.username)}
                          />
                          <Button
                            size="small"
                            variant="text"
                            onClick={() => void handleCopyMention(activeUser.username)}
                          >
                            Copy mention
                          </Button>
                          {!isCurrentUser && (
                            <Button
                              size="small"
                              variant={isFollowingSelectedUser ? 'contained' : 'outlined'}
                              startIcon={isFollowingSelectedUser ? <NotificationsActive fontSize="small" /> : <NotificationsOff fontSize="small" />}
                              onClick={() => void handleToggleUserFollow()}
                              disabled={followActionLoading}
                            >
                              {isFollowingSelectedUser ? 'Following user' : 'Follow user'}
                            </Button>
                          )}
                          {canEditProfile && (
                            <>
                              <Button size="small" variant="outlined" onClick={openProfileEditor}>
                                Edit profile
                              </Button>
                              <Button size="small" variant="outlined" onClick={openPreferencesEditor} disabled={hasPreferenceNamespaceFilter}>
                                Import / replace JSON
                              </Button>
                            </>
                          )}
                        </Box>
                      </Box>
                    </Box>

                    <Grid container spacing={1.5}>
                      <Grid item xs={12} sm={4}>
                        <Paper variant="outlined" sx={{ p: 1.5 }}>
                          <Typography variant="caption" color="text.secondary">
                            Account ID
                          </Typography>
                          <Typography variant="subtitle2" sx={{ wordBreak: 'break-all' }}>
                            {activeUser.id}
                          </Typography>
                        </Paper>
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <Paper variant="outlined" sx={{ p: 1.5 }}>
                          <Typography variant="caption" color="text.secondary">
                            Role coverage
                          </Typography>
                          <Typography variant="subtitle2">{roleCount} roles</Typography>
                        </Paper>
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <Paper variant="outlined" sx={{ p: 1.5 }}>
                          <Typography variant="caption" color="text.secondary">
                            Membership
                          </Typography>
                          <Typography variant="subtitle2">{groupCount} groups</Typography>
                        </Paper>
                      </Grid>
                    </Grid>

                    <Grid container spacing={1.5}>
                      <Grid item xs={12} sm={4}>
                        <Paper variant="outlined" sx={{ p: 1.5 }}>
                          <Typography variant="caption" color="text.secondary">
                            Locale
                          </Typography>
                          <Typography variant="subtitle2">{selectedPreferences?.locale || 'N/A'}</Typography>
                        </Paper>
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <Paper variant="outlined" sx={{ p: 1.5 }}>
                          <Typography variant="caption" color="text.secondary">
                            Timezone
                          </Typography>
                          <Typography variant="subtitle2">{selectedPreferences?.timezone || 'N/A'}</Typography>
                        </Paper>
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <Paper variant="outlined" sx={{ p: 1.5 }}>
                          <Typography variant="caption" color="text.secondary">
                            Favorite sites
                          </Typography>
                          <Typography variant="subtitle2">{favoriteSiteCount}</Typography>
                        </Paper>
                      </Grid>
                    </Grid>

                    <Box>
                      <Typography variant="subtitle2" gutterBottom>
                        Roles
                      </Typography>
                      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                        {(activeUser.roles || []).length > 0 ? (
                          activeUser.roles.map((role) => (
                            <Chip key={role} label={role} icon={<WorkspacePremium fontSize="small" />} variant="outlined" />
                          ))
                        ) : (
                          <Typography variant="body2" color="text.secondary">
                            No roles assigned
                          </Typography>
                        )}
                      </Stack>
                    </Box>
                  </Stack>
                ) : (
                  <Typography color="text.secondary">Select a user to inspect the profile.</Typography>
                )}
              </Paper>

              <Grid container spacing={2}>
                <Grid item xs={12} md={5}>
                  <Paper sx={{ p: 2, border: '1px solid', borderColor: 'divider' }}>
                    <Box display="flex" alignItems="center" justifyContent="space-between" mb={1.5}>
                      <Box display="flex" alignItems="center" gap={1}>
                        <GroupIcon color="primary" />
                        <Typography variant="h6">Groups</Typography>
                      </Box>
                      <Typography variant="caption" color="text.secondary">
                        {groupCount} memberships
                      </Typography>
                    </Box>

                    {profileLoading && !selectedGroups.length ? (
                      <Box display="flex" justifyContent="center" py={3}>
                        <CircularProgress size={24} />
                      </Box>
                    ) : selectedGroups.length === 0 ? (
                      <Alert severity="info">No group memberships found for this user.</Alert>
                    ) : (
                      <List dense disablePadding>
                        {selectedGroups.map((group, index) => (
                          <React.Fragment key={group.id || group.name}>
                            {index > 0 && <Divider />}
                            <ListItem disablePadding>
                              <ListItemAvatar>
                                <Avatar sx={{ bgcolor: 'secondary.light', color: 'secondary.contrastText' }}>
                                  {group.displayName?.charAt(0).toUpperCase() || group.name.charAt(0).toUpperCase()}
                                </Avatar>
                              </ListItemAvatar>
                              <ListItemText
                                primary={group.displayName || group.name}
                                secondary={
                                  <Stack spacing={0.25}>
                                    <Typography variant="caption" color="text.secondary">
                                      {group.name}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                      {group.description || 'No description provided'}
                                    </Typography>
                                  </Stack>
                                }
                              />
                              {group.enabled ? (
                                <Chip size="small" label="Enabled" color="success" variant="outlined" />
                              ) : (
                                <Chip size="small" label="Disabled" variant="outlined" />
                              )}
                            </ListItem>
                          </React.Fragment>
                        ))}
                      </List>
                    )}
                  </Paper>
                </Grid>

                <Grid item xs={12} md={7}>
                  <Paper sx={{ p: 2, border: '1px solid', borderColor: 'divider' }}>
                    <Box display="flex" alignItems="center" justifyContent="space-between" mb={1.5}>
                      <Box display="flex" alignItems="center" gap={1}>
                        <Star color="primary" />
                        <Typography variant="h6">Favorites</Typography>
                      </Box>
                      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                        <Typography variant="caption" color="text.secondary">
                          {filteredFavorites.length}/{selectedFavorites?.content?.length ?? 0} visible
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {favoriteCount} total
                        </Typography>
                        {canEditProfile && (
                          <Button size="small" variant="outlined" startIcon={<Add />} onClick={openFavoriteEditor}>
                            Add favorite
                          </Button>
                        )}
                      </Stack>
                    </Box>

                    <Stack spacing={1} sx={{ mb: 1.5 }}>
                      <TextField
                        size="small"
                        value={favoritesFilter}
                        onChange={(event) => setFavoritesFilter(event.target.value)}
                        placeholder="Filter favorites by name, node ID, type, or time"
                        fullWidth
                        InputProps={{
                          startAdornment: (
                            <InputAdornment position="start">
                              <Search fontSize="small" />
                            </InputAdornment>
                          ),
                          endAdornment: favoritesFilter ? (
                            <InputAdornment position="end">
                              <IconButton size="small" onClick={() => setFavoritesFilter('')} aria-label="Clear favorites filter">
                                <Clear fontSize="small" />
                              </IconButton>
                            </InputAdornment>
                          ) : null,
                        }}
                      />
                      <Stack spacing={0.5}>
                        <Typography variant="caption" color="text.secondary">
                          Quick scopes by node type
                        </Typography>
                        {renderScopeChips(favoritesScope, setFavoritesScope, 'Favorites scope', favoritesScopeCounts)}
                      </Stack>
                    </Stack>

                    {profileLoading && !selectedFavorites ? (
                      <Box display="flex" justifyContent="center" py={3}>
                        <CircularProgress size={24} />
                      </Box>
                    ) : selectedFavorites?.content?.length ? (
                      filteredFavorites.length > 0 ? (
                      <List dense disablePadding>
                        {filteredFavorites.map((favorite, index) => (
                          <React.Fragment key={favorite.id}>
                            {index > 0 && <Divider />}
                            {(() => {
                              const isFolder = isFolderNodeType(favorite.nodeType);
                              const isDocument = isDocumentNodeType(favorite.nodeType);
                              const favoriteNodeKind = getNodeKindLabel(favorite.nodeType);
                              const favoriteNodeAction = getNodeActionHint(favorite.nodeType);
                              const quickActions = renderDocumentQuickActions(favorite.nodeId, favorite.nodeType, {
                                previewLabel: `Preview favorite ${favorite.nodeName}`,
                                discussLabel: `Discuss favorite ${favorite.nodeName}`,
                                favoriteFallbackLabel: favorite.nodeName,
                                onPreview: () => {
                                  void handleFavoritePreview(favorite);
                                },
                                onDiscuss: () => {
                                  void handleFavoriteDiscuss(favorite);
                                },
                              });
                              const secondaryAction = isDocument
                                ? quickActions
                                : (
                                  <Stack direction="row" spacing={0.75}>
                                    <Button
                                      size="small"
                                      variant="outlined"
                                      onClick={() => navigate(`/browse/${favorite.nodeId}`)}
                                    >
                                      Open workspace
                                    </Button>
                                    {canEditProfile && (
                                      <Button
                                        size="small"
                                        color="error"
                                        onClick={() => void handleRemoveFavorite(favorite.nodeId)}
                                      >
                                        Remove favorite
                                      </Button>
                                    )}
                                  </Stack>
                                );

                              return (
                                <ListItem disablePadding secondaryAction={secondaryAction}>
                                  <ListItemButton
                                    onClick={() => {
                                      if (isFolder) {
                                        navigate(`/browse/${favorite.nodeId}`);
                                        return;
                                      }
                                      if (isDocument) {
                                        void handleFavoritePreview(favorite);
                                      }
                                    }}
                                    sx={{ borderRadius: 1, my: 0.5, alignItems: 'flex-start' }}
                                  >
                                    <ListItemAvatar>
                                      <Avatar sx={{ bgcolor: alpha('#f9a825', 0.18), color: '#f9a825' }}>
                                        {isFolder ? <GroupIcon fontSize="small" /> : <Person fontSize="small" />}
                                      </Avatar>
                                    </ListItemAvatar>
                                    <ListItemText
                                      primary={favorite.nodeName}
                                      secondary={
                                        <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                                          <Typography variant="caption" color="text.secondary">
                                            {favoriteNodeKind} · {favoriteNodeAction}
                                          </Typography>
                                          <Typography variant="caption" color="text.secondary">
                                            Favorited {formatDateTime(favorite.createdAt)}
                                          </Typography>
                                        </Stack>
                                      }
                                    />
                                    <ChevronRight color="disabled" />
                                  </ListItemButton>
                                </ListItem>
                              );
                            })()}
                          </React.Fragment>
                        ))}
                      </List>
                      ) : (
                        <Alert severity="info">No favorites match the current filter.</Alert>
                      )
                    ) : favoritesHasFilter ? (
                      <Alert severity="info">No favorites match the current filter.</Alert>
                    ) : (
                      <Alert severity="info">No favorites recorded for this user.</Alert>
                    )}
                  </Paper>
                </Grid>
              </Grid>

              <Grid container spacing={2}>
                <Grid item xs={12} md={6}>
                  <Paper sx={{ p: 2, border: '1px solid', borderColor: 'divider' }}>
                    <Box display="flex" alignItems="center" justifyContent="space-between" mb={1.5}>
                      <Box display="flex" alignItems="center" gap={1}>
                        <GroupIcon color="primary" />
                        <Typography variant="h6">Sites</Typography>
                      </Box>
                      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                        <Typography variant="caption" color="text.secondary">
                          {filteredSites.length}/{selectedSites.length} visible
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {siteCount} workspaces
                        </Typography>
                      </Stack>
                    </Box>

                    <Stack spacing={1} sx={{ mb: 1.5 }}>
                      <TextField
                        size="small"
                        value={sitesFilter}
                        onChange={(event) => setSitesFilter(event.target.value)}
                        placeholder="Filter sites by title, role, visibility, or path"
                        fullWidth
                        InputProps={{
                          startAdornment: (
                            <InputAdornment position="start">
                              <Search fontSize="small" />
                            </InputAdornment>
                          ),
                          endAdornment: sitesFilter ? (
                            <InputAdornment position="end">
                              <IconButton size="small" onClick={() => setSitesFilter('')} aria-label="Clear site filter">
                                <Clear fontSize="small" />
                              </IconButton>
                            </InputAdornment>
                          ) : null,
                        }}
                      />
                      <Stack spacing={0.5}>
                        <Typography variant="caption" color="text.secondary">
                          Quick scopes by node type
                        </Typography>
                        {renderScopeChips(sitesScope, setSitesScope, 'Sites scope', sitesScopeCounts)}
                      </Stack>
                    </Stack>

                    {profileLoading && selectedSites.length === 0 ? (
                      <Box display="flex" justifyContent="center" py={3}>
                        <CircularProgress size={24} />
                      </Box>
                    ) : selectedSites.length > 0 ? (
                      filteredSites.length > 0 ? (
                      <List dense disablePadding>
                        {filteredSites.map((site, index) => (
                          <React.Fragment key={site.siteId}>
                            {index > 0 && <Divider />}
                            <ListItem
                              disableGutters
                              secondaryAction={canEditProfile ? (
                                <Stack direction="row" spacing={0.75}>
                                  {site.siteId && (
                                    <Button
                                      size="small"
                                      variant="outlined"
                                      onClick={() => handleSiteOpen(site.siteId)}
                                    >
                                      Open workspace
                                    </Button>
                                  )}
                                  {selectedFavoriteSiteLookup.get(normalizeLookupKey(site.title)) || selectedFavoriteSiteLookup.get(normalizeLookupKey(site.siteId)) ? (
                                    <Button
                                      size="small"
                                      color="error"
                                      onClick={() => {
                                        const favoriteMatch =
                                          selectedFavoriteSiteLookup.get(normalizeLookupKey(site.title)) ||
                                          selectedFavoriteSiteLookup.get(normalizeLookupKey(site.siteId));
                                        if (favoriteMatch) {
                                          void handleRemoveFavoriteSite(favoriteMatch.siteId || favoriteMatch.nodeId);
                                        }
                                      }}
                                    >
                                      Remove favorite
                                    </Button>
                                  ) : (
                                    <Button size="small" variant="outlined" onClick={() => handleQuickFavoriteSiteToggle(site)}>
                                      Add favorite
                                    </Button>
                                  )}
                                </Stack>
                              ) : undefined}
                            >
                              <ListItemButton
                                onClick={() => handleSiteOpen(site.siteId)}
                                sx={{ borderRadius: 1, my: 0.5, alignItems: 'flex-start' }}
                              >
                                <ListItemText
                                  primary={site.title}
                                  secondary={
                                    <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                                      <Typography variant="caption" color="text.secondary">
                                        {getNodeKindLabel('FOLDER')} · {getNodeActionHint('FOLDER')}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        {site.siteId} · {site.visibility}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        {site.description || 'No description provided'}
                                      </Typography>
                                      <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ pt: 0.25 }}>
                                        <Chip size="small" variant="outlined" label={site.role} />
                                        {site.memberCount !== undefined && (
                                          <Chip size="small" variant="outlined" label={`${site.memberCount} members`} />
                                        )}
                                      </Stack>
                                      {selectedFavoriteSiteLookup.get(normalizeLookupKey(site.title)) || selectedFavoriteSiteLookup.get(normalizeLookupKey(site.siteId)) ? (
                                        <Typography variant="caption" color="success.main">
                                          Favorited workspace
                                        </Typography>
                                      ) : null}
                                    </Stack>
                                  }
                                />
                                <ChevronRight color="disabled" />
                              </ListItemButton>
                            </ListItem>
                          </React.Fragment>
                        ))}
                      </List>
                      ) : (
                        <Alert severity="info">No sites match the current filter.</Alert>
                      )
                    ) : sitesHasFilter ? (
                      <Alert severity="info">No sites match the current filter.</Alert>
                    ) : (
                      <Alert severity="info">No site-style workspaces derived for this user.</Alert>
                    )}
                  </Paper>
                </Grid>

                <Grid item xs={12} md={6}>
                  <Paper sx={{ p: 2, border: '1px solid', borderColor: 'divider' }}>
                    <Box display="flex" alignItems="center" justifyContent="space-between" mb={1.5}>
                      <Box display="flex" alignItems="center" gap={1}>
                        <Star color="primary" />
                        <Typography variant="h6">Favorite Sites</Typography>
                      </Box>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Typography variant="caption" color="text.secondary">
                          {filteredFavoriteSites.length}/{selectedFavoriteSites.length} visible
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {favoriteSiteCount} workspaces
                        </Typography>
                        {canEditProfile && (
                          <Button size="small" variant="outlined" startIcon={<Add />} onClick={openFavoriteSiteEditor}>
                            Add workspace
                          </Button>
                        )}
                      </Stack>
                    </Box>

                    <Stack spacing={1} sx={{ mb: 1.5 }}>
                      <TextField
                        size="small"
                        value={favoriteSitesFilter}
                        onChange={(event) => setFavoriteSitesFilter(event.target.value)}
                        placeholder="Filter favorite sites by title, site ID, path, or request status"
                        fullWidth
                        InputProps={{
                          startAdornment: (
                            <InputAdornment position="start">
                              <Search fontSize="small" />
                            </InputAdornment>
                          ),
                          endAdornment: favoriteSitesFilter ? (
                            <InputAdornment position="end">
                              <IconButton size="small" onClick={() => setFavoriteSitesFilter('')} aria-label="Clear favorite site filter">
                                <Clear fontSize="small" />
                              </IconButton>
                            </InputAdornment>
                          ) : null,
                        }}
                      />
                      <Stack spacing={0.5}>
                        <Typography variant="caption" color="text.secondary">
                          Quick scopes by node type
                        </Typography>
                        {renderScopeChips(favoriteSitesScope, setFavoriteSitesScope, 'Favorite sites scope', favoriteSitesScopeCounts)}
                      </Stack>
                    </Stack>

                    {profileLoading && selectedFavoriteSites.length === 0 ? (
                      <Box display="flex" justifyContent="center" py={3}>
                        <CircularProgress size={24} />
                      </Box>
                    ) : selectedFavoriteSites.length > 0 ? (
                      filteredFavoriteSites.length > 0 ? (
                      <List dense disablePadding>
                        {filteredFavoriteSites.map((site, index) => {
                          const selectedSiteMatch =
                            selectedSiteLookup.get(normalizeLookupKey(site.title)) ||
                            selectedSiteLookup.get(normalizeLookupKey(site.siteId));
                          const pendingRequest =
                            pendingSiteRequestLookup.get(normalizeLookupKey(site.title)) ||
                            pendingSiteRequestLookup.get(normalizeLookupKey(site.siteId));
                          const isMemberSite = Boolean(selectedSiteMatch);
                          const favoriteSiteNodeType = site.folderType || site.nodeType || 'FOLDER';

                          return (
                          <React.Fragment key={`${site.nodeId || site.siteId || site.title}-${index}`}>
                            {index > 0 && <Divider />}
                            <ListItem
                              disableGutters
                              secondaryAction={canEditProfile ? (
                                <Stack direction="row" spacing={0.75}>
                                  {site.nodeId && (
                                    <Button
                                      size="small"
                                      variant="outlined"
                                      onClick={() => handleSiteOpen(site.nodeId)}
                                    >
                                      Open workspace
                                    </Button>
                                  )}
                                  {!isMemberSite && !pendingRequest && (
                                    <Button size="small" variant="outlined" onClick={() => openFavoriteSiteRequestEditor(site)}>
                                      Request access
                                    </Button>
                                  )}
                                  {!isMemberSite && pendingRequest && (
                                    <>
                                      <Button size="small" variant="outlined" onClick={() => openFavoriteSiteRequestEditor(site)}>
                                        Edit request
                                      </Button>
                                      <Button
                                        size="small"
                                        color="warning"
                                        onClick={() => void handleWithdrawSiteMembershipRequest(pendingRequest.siteId || site.siteId)}
                                      >
                                        Withdraw
                                      </Button>
                                    </>
                                  )}
                                  {canEditProfile && site.nodeId && (
                                    <Button
                                      size="small"
                                      color="error"
                                      onClick={() => void handleRemoveFavoriteSite(site.siteId || site.nodeId)}
                                    >
                                      Remove
                                    </Button>
                                  )}
                                  {isMemberSite && <Chip size="small" color="success" variant="outlined" label="In sites" />}
                                </Stack>
                              ) : undefined}
                            >
                              <ListItemButton
                                onClick={() => handleSiteOpen(site.nodeId || site.siteId)}
                                sx={{ borderRadius: 1, my: 0.5, alignItems: 'flex-start' }}
                              >
                                <ListItemText
                                  primary={site.title}
                                  secondary={
                                    <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                                      <Typography variant="caption" color="text.secondary">
                                        {getNodeKindLabel(favoriteSiteNodeType)} · {getNodeActionHint(favoriteSiteNodeType)}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        {site.folderType || site.nodeType || 'FOLDER'}
                                        {site.path ? ` · ${site.path}` : ''}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        Favorited {formatDateTime(site.favoritedAt)}
                                      </Typography>
                                      {pendingRequest ? (
                                        <Typography variant="caption" color="text.secondary">
                                          Request {pendingRequest.status}
                                        </Typography>
                                      ) : null}
                                    </Stack>
                                  }
                                />
                                <ChevronRight color="disabled" />
                              </ListItemButton>
                            </ListItem>
                          </React.Fragment>
                          );
                        })}
                      </List>
                      ) : (
                        <Alert severity="info">No favorite sites match the current filter.</Alert>
                      )
                    ) : favoriteSitesHasFilter ? (
                      <Alert severity="info">No favorite sites match the current filter.</Alert>
                    ) : (
                      <Alert severity="info">No favorite site-style workspaces found.</Alert>
                    )}
                  </Paper>
                </Grid>
              </Grid>

              <Grid container spacing={2}>
                <Grid item xs={12} md={5}>
                  <Paper sx={{ p: 2, border: '1px solid', borderColor: 'divider' }}>
                    <Box display="flex" alignItems="center" justifyContent="space-between" mb={1.5}>
                      <Typography variant="h6">Preferences</Typography>
                      <Box display="flex" alignItems="center" gap={1} flexWrap="wrap" justifyContent="flex-end">
                        <Typography variant="caption" color="text.secondary">
                          {preferenceEntries.length} stored values
                        </Typography>
                        {canEditProfile && (
                          <>
                            <Button size="small" variant="outlined" onClick={() => openPreferenceEntryEditor()}>
                              Set value
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              onClick={() => void handleExportPreferences()}
                              disabled={hasPreferenceNamespaceFilter || preferenceActionLoadingKey === '__export__'}
                            >
                              {preferenceActionLoadingKey === '__export__' ? 'Exporting...' : 'Export JSON'}
                            </Button>
                            <Button size="small" variant="outlined" onClick={openPreferencesEditor} disabled={hasPreferenceNamespaceFilter}>
                              Import / replace JSON
                            </Button>
                            <Button
                              size="small"
                              color="warning"
                              disabled={preferenceEntries.length === 0 || preferenceActionLoadingKey === '__all__' || hasPreferenceNamespaceFilter}
                              onClick={() => void handleClearPreferences()}
                            >
                              {preferenceActionLoadingKey === '__all__' ? 'Clearing...' : 'Clear all'}
                            </Button>
                          </>
                        )}
                      </Box>
                    </Box>

                    {preferencesLoading ? (
                      <Box display="flex" justifyContent="center" p={2}>
                        <CircularProgress size={20} />
                      </Box>
                    ) : preferencesError ? (
                      <Alert severity="error">{preferencesError}</Alert>
                    ) : selectedPreferences ? (
                      <Stack spacing={1.25}>
                        <Typography variant="body2" color="text.secondary">
                          Display name: {selectedPreferences.displayName || 'N/A'}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Profile: {[selectedPreferences.firstName, selectedPreferences.lastName].filter(Boolean).join(' ') || 'N/A'}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Contact: {[selectedPreferences.phone, selectedPreferences.department, selectedPreferences.jobTitle].filter(Boolean).join(' • ') || 'N/A'}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Last login: {formatDateTime(selectedPreferences.lastLoginDate)}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Password updated: {formatDateTime(selectedPreferences.lastPasswordChangeDate)}
                        </Typography>

                        <Box
                          sx={{
                            p: 1,
                            borderRadius: 2,
                            border: '1px solid',
                            borderColor: 'divider',
                            background: 'linear-gradient(180deg, rgba(255,255,255,0.04), rgba(255,255,255,0))',
                          }}
                        >
                          <Box display="flex" alignItems="center" justifyContent="space-between" gap={1} mb={1}>
                            <Typography variant="caption" color="text.secondary">
                              Namespace filter
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {preferenceEntries.length} visible values • {preferenceGroups.length} namespace{preferenceGroups.length === 1 ? '' : 's'}
                            </Typography>
                          </Box>
                          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                            <Chip
                              label={`All (${preferenceEntries.length})`}
                              size="small"
                              color={selectedPreferenceNamespace ? 'default' : 'primary'}
                              variant={selectedPreferenceNamespace ? 'outlined' : 'filled'}
                              clickable
                              onClick={() => setSelectedPreferenceNamespace('')}
                              aria-label="Show all preference namespaces"
                            />
                            {selectedPreferenceNamespaces.map((namespace) => {
                              const namespaceCount = preferenceGroups.find((group) => group.namespace === namespace)?.entries.length ?? 0;
                              const isActive = selectedPreferenceNamespace === namespace;
                              return (
                                <Chip
                                  key={namespace}
                                  label={`${namespace} (${namespaceCount})`}
                                  size="small"
                                  color={isActive ? 'primary' : 'default'}
                                  variant={isActive ? 'filled' : 'outlined'}
                                  clickable
                                  onClick={() => setSelectedPreferenceNamespace(isActive ? '' : namespace)}
                                  aria-label={`Filter preferences by ${namespace} namespace`}
                                />
                              );
                            })}
                          </Stack>
                        </Box>

                        {hasPreferenceNamespaceFilter && (
                          <Alert severity="info">
                            Filtered mode shows only {selectedPreferenceNamespace}.* preferences. Import, export, and raw JSON editing stay disabled to avoid overwriting hidden namespaces.
                          </Alert>
                        )}

                        {preferenceGroups.length > 0 ? (
                          <Stack spacing={1}>
                            {preferenceGroups.map((group) => (
                              <Paper key={group.namespace} variant="outlined" sx={{ p: 1.25 }}>
                                <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1}>
                                  <Typography variant="subtitle2" sx={{ wordBreak: 'break-word' }}>
                                    {group.namespace}
                                  </Typography>
                                  <Chip
                                    label={`${group.entries.length} item${group.entries.length === 1 ? '' : 's'}`}
                                    size="small"
                                    variant="outlined"
                                  />
                                </Box>
                                <Stack spacing={1}>
                                  {group.entries.map(([key, value]) => {
                                    const isBusy = preferenceActionLoadingKey === key;
                                    return (
                                      <Paper key={key} variant="outlined" sx={{ p: 1.25 }}>
                                        <Box display="flex" justifyContent="space-between" gap={1}>
                                          <Box minWidth={0}>
                                            <Typography variant="subtitle2" sx={{ wordBreak: 'break-word' }}>
                                              {key}
                                            </Typography>
                                            <Typography variant="body2" color="text.secondary" sx={{ wordBreak: 'break-word' }}>
                                              {formatPreferenceValue(value)}
                                            </Typography>
                                          </Box>
                                          {canEditProfile && (
                                            <Stack direction="row" spacing={0.5}>
                                              <Button
                                                size="small"
                                                variant="text"
                                                onClick={() => openPreferenceEntryEditor(key, value)}
                                                disabled={isBusy}
                                              >
                                                Edit
                                              </Button>
                                              <IconButton
                                                size="small"
                                                color="error"
                                                onClick={() => void handleDeletePreference(key)}
                                                disabled={isBusy}
                                                aria-label={`Delete preference ${key}`}
                                              >
                                                <DeleteOutline fontSize="small" />
                                              </IconButton>
                                            </Stack>
                                          )}
                                        </Box>
                                      </Paper>
                                    );
                                  })}
                                </Stack>
                              </Paper>
                            ))}
                          </Stack>
                        ) : (
                          <Alert severity="info">
                            No stored preferences in {hasPreferenceNamespaceFilter ? `${selectedPreferenceNamespace}.` : 'the current profile'}.
                            {canEditProfile ? ' Use Set value to add one.' : ''}
                          </Alert>
                        )}
                      </Stack>
                    ) : (
                      <Alert severity="info">No preference payload available.</Alert>
                    )}
                  </Paper>
                </Grid>

                <Grid item xs={12} md={7}>
                  <Paper sx={{ p: 2, border: '1px solid', borderColor: 'divider' }}>
                    <Stack spacing={1.5} mb={1.5}>
                      <Box display="flex" alignItems="center" justifyContent="space-between" gap={1}>
                        <Typography variant="h6">Recent Activity</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {activityCount} recent events
                        </Typography>
                      </Box>
                      <Stack spacing={0.5}>
                        <Typography variant="caption" color="text.secondary">
                          Quick scopes by node type
                        </Typography>
                        {renderScopeChips(recentActivityScope, setRecentActivityScope, 'Recent activity scope', recentActivityScopeCounts)}
                      </Stack>
                      <TextField
                        size="small"
                        value={recentActivityFilter}
                        onChange={(event) => setRecentActivityFilter(event.target.value)}
                        placeholder="Filter by title, summary, type, node, or time"
                        fullWidth
                      />
                    </Stack>

                    {profileLoading && selectedActivities.length === 0 ? (
                      <Box display="flex" justifyContent="center" py={3}>
                        <CircularProgress size={24} />
                      </Box>
                    ) : filteredActivities.length > 0 ? (
                      <List dense disablePadding>
                        {filteredActivities.slice(0, 6).map((activity, index) => (
                          <React.Fragment key={activity.id}>
                            {index > 0 && <Divider />}
                            <ListItem
                              disablePadding
                              secondaryAction={renderActivityQuickActions(activity)}
                            >
                              <ListItemButton
                                onClick={() => handleRecentActivityOpen(activity)}
                                sx={{
                                  borderRadius: 1,
                                  my: 0.5,
                                  alignItems: 'flex-start',
                                  '&:hover .recent-activity-chevron': {
                                    color: 'primary.main',
                                  },
                                }}
                              >
                                <ListItemText
                                  primary={
                                    <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
                                      <Typography variant="subtitle2">{activity.title}</Typography>
                                      <Chip size="small" variant="outlined" label={activity.type} />
                                      {activity.nodeName && (
                                        <Chip size="small" variant="outlined" color="primary" label={activity.nodeName} />
                                      )}
                                    </Stack>
                                  }
                                  secondary={
                                    <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                                      <Typography variant="caption" color="text.secondary">
                                        {getNodeKindLabel(activity.nodeType)} · {getNodeActionHint(activity.nodeType)}
                                        {activity.nodeId ? ` · ${activity.nodeId}` : ''}
                                      </Typography>
                                      {activity.summary && (
                                        <Typography variant="caption" color="text.secondary">
                                          {activity.summary}
                                        </Typography>
                                      )}
                                      <Stack direction="row" alignItems="center" spacing={0.5} flexWrap="wrap">
                                        <Typography variant="caption" color="text.secondary">
                                          {formatDateTime(activity.occurredAt)}
                                        </Typography>
                                        <Stack direction="row" alignItems="center" spacing={0.25} color="text.secondary">
                                          <Typography variant="caption" color="inherit">
                                            Click row to open
                                          </Typography>
                                          <ChevronRight fontSize="inherit" className="recent-activity-chevron" />
                                        </Stack>
                                      </Stack>
                                    </Stack>
                                  }
                                />
                              </ListItemButton>
                            </ListItem>
                          </React.Fragment>
                        ))}
                      </List>
                    ) : recentActivityHasFilter ? (
                      <Alert severity="info">No recent activity matches the current filter.</Alert>
                    ) : (
                      <Alert severity="info">No recent activity available for this user.</Alert>
                    )}
                  </Paper>
                </Grid>
              </Grid>

              {canModerateRequests && (
                <Paper sx={{ p: 2, border: '1px solid', borderColor: 'divider' }}>
                  <Box display="flex" alignItems="center" justifyContent="space-between" mb={1.5} gap={1}>
                    <Box display="flex" alignItems="center" gap={1}>
                      <Typography variant="h6">Moderation Queue</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {moderationRequestsPage?.totalElements ?? 0} visible requests
                      </Typography>
                    </Box>
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={<Refresh />}
                      onClick={() => setModerationReloadToken((value) => value + 1)}
                    >
                      Refresh
                    </Button>
                  </Box>

                  <Grid container spacing={1.5} sx={{ mb: 1.5 }}>
                    <Grid item xs={12} md={3}>
                      <TextField
                        select
                        size="small"
                        label="Status"
                        value={moderationStatusFilter}
                        onChange={(event) => setModerationStatusFilter(event.target.value)}
                        fullWidth
                      >
                        <MenuItem value="PENDING">Pending</MenuItem>
                        <MenuItem value="APPROVED">Approved</MenuItem>
                        <MenuItem value="REJECTED">Rejected</MenuItem>
                        <MenuItem value="ALL">All</MenuItem>
                      </TextField>
                    </Grid>
                    <Grid item xs={12} md={4}>
                      <TextField
                        size="small"
                        label="Filter by site"
                        value={moderationSiteFilter}
                        onChange={(event) => setModerationSiteFilter(event.target.value)}
                        placeholder="finance-site"
                        fullWidth
                      />
                    </Grid>
                    <Grid item xs={12} md={5}>
                      <TextField
                        size="small"
                        label="Filter by requester"
                        value={moderationRequesterFilter}
                        onChange={(event) => setModerationRequesterFilter(event.target.value)}
                        placeholder="alice"
                        fullWidth
                      />
                    </Grid>
                  </Grid>

                  {moderationRequestsError && (
                    <Alert severity="warning" sx={{ mb: 2 }}>
                      {moderationRequestsError}
                    </Alert>
                  )}

                  {moderationRequestsLoading ? (
                    <Box py={3} display="flex" justifyContent="center">
                      <CircularProgress size={24} />
                    </Box>
                  ) : moderationRequestsPage?.content?.length ? (
                    <>
                      <List dense disablePadding>
                        {moderationRequestsPage.content.map((request, index) => {
                          const approveKey = `APPROVED:${request.username || 'unknown'}:${request.siteId || 'unknown'}`;
                          const rejectKey = `REJECTED:${request.username || 'unknown'}:${request.siteId || 'unknown'}`;
                          const isPending = request.status === 'PENDING';
                          return (
                            <React.Fragment key={`${request.username || 'unknown'}:${request.siteId || index}`}>
                              {index > 0 && <Divider />}
                              <ListItem
                                disableGutters
                                secondaryAction={isPending && request.siteId ? (
                                  <Stack direction="row" spacing={0.75}>
                                    <Button
                                      size="small"
                                      color="success"
                                      startIcon={<CheckCircle />}
                                      disabled={moderationActionLoadingKey === approveKey || moderationActionLoadingKey === rejectKey}
                                      onClick={() => void handleModerateSiteMembershipRequest(request, 'APPROVED')}
                                    >
                                      {moderationActionLoadingKey === approveKey ? 'Approving...' : 'Approve'}
                                    </Button>
                                    <Button
                                      size="small"
                                      color="error"
                                      startIcon={<Clear />}
                                      disabled={moderationActionLoadingKey === approveKey || moderationActionLoadingKey === rejectKey}
                                      onClick={() => void handleModerateSiteMembershipRequest(request, 'REJECTED')}
                                    >
                                      {moderationActionLoadingKey === rejectKey ? 'Rejecting...' : 'Reject'}
                                    </Button>
                                  </Stack>
                                ) : undefined}
                              >
                                <ListItemText
                                  primary={(
                                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                                      <Typography variant="subtitle2">{request.siteTitle}</Typography>
                                      <Chip
                                        size="small"
                                        variant="outlined"
                                        color={request.status === 'APPROVED' ? 'success' : request.status === 'REJECTED' ? 'error' : 'default'}
                                        label={request.status}
                                      />
                                      {request.username && <Chip size="small" variant="outlined" label={`@${request.username}`} />}
                                    </Stack>
                                  )}
                                  secondary={(
                                    <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                                      <Typography variant="caption" color="text.secondary">
                                        {request.siteId || 'Unknown site'}{request.role ? ` · ${request.role}` : ''}
                                      </Typography>
                                      {request.message && (
                                        <Typography variant="caption" color="text.secondary">
                                          {request.message}
                                        </Typography>
                                      )}
                                      <Typography variant="caption" color="text.secondary">
                                        Requested {formatDateTime(request.requestedAt)}
                                      </Typography>
                                      {(request.decisionBy || request.decisionAt || request.decisionComment) && (
                                        <Typography variant="caption" color="text.secondary">
                                          Decision {request.decisionAt ? formatDateTime(request.decisionAt) : 'N/A'}
                                          {request.decisionBy ? ` by ${request.decisionBy}` : ''}
                                          {request.decisionComment ? ` · ${request.decisionComment}` : ''}
                                        </Typography>
                                      )}
                                    </Stack>
                                  )}
                                />
                              </ListItem>
                            </React.Fragment>
                          );
                        })}
                      </List>

                      <Box display="flex" alignItems="center" justifyContent="space-between" mt={1.5} gap={1}>
                        <Typography variant="caption" color="text.secondary">
                          Page {Math.min((moderationRequestsPage?.number ?? 0) + 1, Math.max(moderationRequestsPage?.totalPages ?? 1, 1))} of {Math.max(moderationRequestsPage?.totalPages ?? 1, 1)}
                        </Typography>
                        <Stack direction="row" spacing={1}>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={(moderationRequestsPage?.number ?? 0) <= 0}
                            onClick={() => setModerationPage((value) => Math.max(value - 1, 0))}
                          >
                            Prev
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={Boolean(moderationRequestsPage && moderationRequestsPage.number + 1 >= moderationRequestsPage.totalPages)}
                            onClick={() => setModerationPage((value) => value + 1)}
                          >
                            Next
                          </Button>
                        </Stack>
                      </Box>
                    </>
                  ) : (
                    <Alert severity="info">No visible site membership requests match the current moderation filters.</Alert>
                  )}
                </Paper>
              )}

              <Paper sx={{ p: 2, border: '1px solid', borderColor: 'divider' }}>
                <Box display="flex" alignItems="center" justifyContent="space-between" mb={1.5}>
                  <Box display="flex" alignItems="center" gap={1}>
                    <Typography variant="h6">Site Membership Requests</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {requestCount} requests
                    </Typography>
                  </Box>
                  {canEditProfile && (
                    <Button size="small" variant="outlined" startIcon={<Add />} onClick={() => openSiteMembershipRequestEditor()}>
                      New request
                    </Button>
                  )}
                </Box>

                {selectedSiteMembershipRequests.length > 0 ? (
                  <List dense disablePadding>
                    {selectedSiteMembershipRequests.map((request, index) => (
                      <React.Fragment key={`${request.siteId || request.siteTitle}-${index}`}>
                        {index > 0 && <Divider />}
                        <ListItem
                          disableGutters
                          secondaryAction={canEditProfile && request.siteId ? (
                            <Stack direction="row" spacing={0.75}>
                              <Button size="small" variant="outlined" startIcon={<Edit />} onClick={() => openSiteMembershipRequestEditor(request)}>
                                Edit
                              </Button>
                              <Button
                                size="small"
                                color="warning"
                                disabled={siteRequestActionLoadingId === request.siteId}
                                onClick={() => void handleWithdrawSiteMembershipRequest(request.siteId)}
                              >
                                {siteRequestActionLoadingId === request.siteId ? 'Withdrawing...' : 'Withdraw'}
                              </Button>
                            </Stack>
                          ) : undefined}
                        >
                          <ListItemText
                            primary={
                              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                                <Typography variant="subtitle2">{request.siteTitle}</Typography>
                                <Chip
                                  size="small"
                                  color={request.status === 'APPROVED' ? 'success' : request.status === 'REJECTED' ? 'error' : 'default'}
                                  variant="outlined"
                                  label={request.status}
                                />
                              </Stack>
                            }
                            secondary={
                              <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                                <Typography variant="caption" color="text.secondary">
                                  {request.siteId || 'Unknown site'}{request.role ? ` · ${request.role}` : ''}
                                </Typography>
                                {request.message && (
                                  <Typography variant="caption" color="text.secondary">
                                    {request.message}
                                  </Typography>
                                )}
                                <Typography variant="caption" color="text.secondary">
                                  Requested {formatDateTime(request.requestedAt)}
                                </Typography>
                                {(request.decisionBy || request.decisionAt || request.decisionComment) && (
                                  <Typography variant="caption" color="text.secondary">
                                    Reviewed {request.decisionAt ? formatDateTime(request.decisionAt) : 'N/A'}
                                    {request.decisionBy ? ` by ${request.decisionBy}` : ''}
                                    {request.decisionComment ? ` · ${request.decisionComment}` : ''}
                                  </Typography>
                                )}
                              </Stack>
                            }
                          />
                        </ListItem>
                      </React.Fragment>
                    ))}
                  </List>
                ) : (
                  <Alert severity="info">No site membership requests recorded.</Alert>
                )}
              </Paper>

                <Paper
                sx={{
                  p: 2,
                  border: '1px solid',
                  borderColor: 'divider',
                  background: `linear-gradient(135deg, ${alpha('#2e7d32', 0.05)} 0%, ${alpha('#1976d2', 0.06)} 100%)`,
                }}
                >
                  <Stack spacing={1.5} mb={1.5}>
                    <Box display="flex" alignItems="center" justifyContent="space-between" gap={1}>
                      <Box>
                        <Typography variant="h6">Authored Comments</Typography>
                      <Typography variant="caption" color="text.secondary">
                        Recent comments written by this user
                      </Typography>
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      {authoredCommentCount} items
                    </Typography>
                  </Box>
                  <Stack spacing={0.5}>
                    <Typography variant="caption" color="text.secondary">
                      Quick scopes by node type
                    </Typography>
                    {renderScopeChips(authoredCommentScope, setAuthoredCommentScope, 'Authored comments scope', authoredCommentScopeCounts)}
                  </Stack>
                  <TextField
                    size="small"
                    value={authoredCommentFilter}
                    onChange={(event) => setAuthoredCommentFilter(event.target.value)}
                    placeholder="Filter by author, content, node, or time"
                    fullWidth
                  />
                </Stack>

                {profileLoading && !selectedAuthoredComments ? (
                  <Box display="flex" justifyContent="center" py={3}>
                    <CircularProgress size={24} />
                  </Box>
                ) : filteredAuthoredComments.length > 0 ? (
                      <List dense disablePadding>
                        {filteredAuthoredComments.map((comment, index) => (
                          <React.Fragment key={comment.id}>
                            {index > 0 && <Divider />}
                            <ListItem
                              alignItems="flex-start"
                              sx={{ py: 1.25 }}
                              secondaryAction={renderCommentQuickActions(comment, {
                                previewLabel: `Preview authored comment ${comment.id}`,
                                discussLabel: `Discuss authored comment ${comment.id}`,
                                favoriteFallbackLabel: comment.nodeName || comment.id,
                                onPreview: () => handleAuthoredCommentPreview(comment),
                                onDiscuss: () => handleAuthoredCommentDiscuss(comment),
                                draftText: comment.author ? `@${comment.author} ` : null,
                              })}
                            >
                              <ListItemButton
                                onClick={() => handleAuthoredCommentPreview(comment)}
                                sx={{ borderRadius: 1, my: 0.5, alignItems: 'flex-start' }}
                              >
                                <ListItemText
                                  primary={
                                    <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
                                      <Typography variant="subtitle2">{comment.author}</Typography>
                                      {comment.nodeName && (
                                        <Chip size="small" variant="outlined" color="primary" label={comment.nodeName} />
                                      )}
                                      <Typography variant="caption" color="text.secondary">
                                        {formatDateTime(comment.created)}
                                      </Typography>
                                    </Stack>
                                  }
                                  secondary={
                                    <Stack spacing={0.75} sx={{ mt: 0.75 }}>
                                      <Typography variant="caption" color="text.secondary">
                                        {getNodeKindLabel(comment.nodeType)} · {getNodeActionHint(comment.nodeType)}
                                        {comment.nodeId ? ` · ${comment.nodeId}` : ''}
                                      </Typography>
                                      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                                        {comment.content}
                                      </Typography>
                                      <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                        {(comment.mentionedUsers || []).map((mentionedUser) => (
                                          <Chip
                                            key={`${comment.id}-${mentionedUser}`}
                                            size="small"
                                            variant="outlined"
                                            label={`@${mentionedUser}`}
                                          />
                                        ))}
                                      </Stack>
                                    </Stack>
                                  }
                                />
                                <ChevronRight color="disabled" />
                              </ListItemButton>
                          </ListItem>
                        </React.Fragment>
                      ))}
                  </List>
                ) : authoredCommentHasFilter ? (
                  <Alert severity="info">No authored comments match the current filter.</Alert>
                ) : (
                  <Alert severity="info">No authored comments recorded for this user.</Alert>
                )}
              </Paper>

              <Paper
                sx={{
                  p: 2,
                  border: '1px solid',
                  borderColor: 'divider',
                  background: `linear-gradient(135deg, ${alpha('#6a1b9a', 0.05)} 0%, ${alpha('#1976d2', 0.06)} 100%)`,
                }}
                >
                  <Stack spacing={1.5} mb={1.5}>
                    <Box display="flex" alignItems="center" justifyContent="space-between" gap={1}>
                      <Box>
                        <Typography variant="h6">Mentioned Comments</Typography>
                      <Typography variant="caption" color="text.secondary">
                        Recent comments mentioning this user
                      </Typography>
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      {mentionedCommentCount} items
                    </Typography>
                  </Box>
                  <Stack spacing={0.5}>
                    <Typography variant="caption" color="text.secondary">
                      Quick scopes by node type
                    </Typography>
                    {renderScopeChips(mentionedCommentScope, setMentionedCommentScope, 'Mentioned comments scope', mentionedCommentScopeCounts)}
                  </Stack>
                  <TextField
                    size="small"
                    value={mentionedCommentFilter}
                    onChange={(event) => setMentionedCommentFilter(event.target.value)}
                    placeholder="Filter by author, content, node, or time"
                    fullWidth
                  />
                </Stack>

                {profileLoading && !selectedMentionedComments ? (
                  <Box display="flex" justifyContent="center" py={3}>
                    <CircularProgress size={24} />
                  </Box>
                ) : filteredMentionedComments.length > 0 ? (
                      <List dense disablePadding>
                        {filteredMentionedComments.map((comment, index) => (
                          <React.Fragment key={comment.id}>
                            {index > 0 && <Divider />}
                            <ListItem
                              alignItems="flex-start"
                              sx={{ py: 1.25 }}
                              secondaryAction={renderCommentQuickActions(comment, {
                                previewLabel: `Preview comment ${comment.id}`,
                                discussLabel: `Discuss comment ${comment.id}`,
                                favoriteFallbackLabel: comment.nodeName || comment.id,
                                onPreview: () => handleMentionedCommentPreview(comment),
                                onDiscuss: () => handleMentionedCommentDiscuss(comment),
                                draftText: comment.author ? `@${comment.author} ` : null,
                              })}
                            >
                              <ListItemButton
                                onClick={() => handleMentionedCommentPreview(comment)}
                                sx={{ borderRadius: 1, my: 0.5, alignItems: 'flex-start' }}
                              >
                                <ListItemText
                                  primary={
                                    <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
                                      <Typography variant="subtitle2">{comment.author}</Typography>
                                      {comment.nodeName && (
                                        <Chip size="small" variant="outlined" color="primary" label={comment.nodeName} />
                                      )}
                                      <Typography variant="caption" color="text.secondary">
                                        {formatDateTime(comment.created)}
                                      </Typography>
                                    </Stack>
                                  }
                                  secondary={
                                    <Stack spacing={0.75} sx={{ mt: 0.75 }}>
                                      <Typography variant="caption" color="text.secondary">
                                        {getNodeKindLabel(comment.nodeType)} · {getNodeActionHint(comment.nodeType)}
                                        {comment.nodeId ? ` · ${comment.nodeId}` : ''}
                                      </Typography>
                                      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                                        {comment.content}
                                      </Typography>
                                      <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                        {(comment.mentionedUsers || []).map((mentionedUser) => (
                                          <Chip
                                            key={`${comment.id}-${mentionedUser}`}
                                            size="small"
                                            variant="outlined"
                                            label={`@${mentionedUser}`}
                                          />
                                        ))}
                                      </Stack>
                                    </Stack>
                                  }
                                />
                                <ChevronRight color="disabled" />
                              </ListItemButton>
                          </ListItem>
                        </React.Fragment>
                      ))}
                  </List>
                ) : mentionedCommentHasFilter ? (
                  <Alert severity="info">No mentioned comments match the current filter.</Alert>
                ) : (
                  <Alert severity="info">No comments currently mention this user.</Alert>
                )}
              </Paper>
            </Stack>
          </Grid>
        </Grid>

        <Dialog open={profileEditorOpen} onClose={() => setProfileEditorOpen(false)} fullWidth maxWidth="sm">
          <DialogTitle>Edit Profile</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <TextField
                label="Display name"
                value={profileDraft.displayName || ''}
                onChange={(event) => setProfileDraft((current) => ({ ...current, displayName: event.target.value }))}
                fullWidth
              />
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="First name"
                    value={profileDraft.firstName || ''}
                    onChange={(event) => setProfileDraft((current) => ({ ...current, firstName: event.target.value }))}
                    fullWidth
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Last name"
                    value={profileDraft.lastName || ''}
                    onChange={(event) => setProfileDraft((current) => ({ ...current, lastName: event.target.value }))}
                    fullWidth
                  />
                </Grid>
              </Grid>
              <TextField
                label="Phone"
                value={profileDraft.phone || ''}
                onChange={(event) => setProfileDraft((current) => ({ ...current, phone: event.target.value }))}
                fullWidth
              />
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Department"
                    value={profileDraft.department || ''}
                    onChange={(event) => setProfileDraft((current) => ({ ...current, department: event.target.value }))}
                    fullWidth
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Job title"
                    value={profileDraft.jobTitle || ''}
                    onChange={(event) => setProfileDraft((current) => ({ ...current, jobTitle: event.target.value }))}
                    fullWidth
                  />
                </Grid>
              </Grid>
              <TextField
                label="Avatar URL"
                value={profileDraft.avatarUrl || ''}
                onChange={(event) => setProfileDraft((current) => ({ ...current, avatarUrl: event.target.value }))}
                fullWidth
              />
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Locale"
                    value={profileDraft.locale || ''}
                    onChange={(event) => setProfileDraft((current) => ({ ...current, locale: event.target.value }))}
                    fullWidth
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Timezone"
                    value={profileDraft.timezone || ''}
                    onChange={(event) => setProfileDraft((current) => ({ ...current, timezone: event.target.value }))}
                    fullWidth
                  />
                </Grid>
              </Grid>
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setProfileEditorOpen(false)}>Cancel</Button>
            <Button onClick={() => void handleSaveProfile()} variant="contained" disabled={profileSaveLoading}>
              Save profile
            </Button>
          </DialogActions>
        </Dialog>

        <Dialog open={preferencesEditorOpen} onClose={() => setPreferencesEditorOpen(false)} fullWidth maxWidth="md">
          <DialogTitle>Import / Replace Preferences JSON</DialogTitle>
          <DialogContent dividers>
            <TextField
              value={preferencesDraftText}
              onChange={(event) => setPreferencesDraftText(event.target.value)}
              fullWidth
              multiline
              minRows={14}
              maxRows={24}
              placeholder='{\n  "theme": "dark"\n}'
              InputProps={{
                sx: {
                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, monospace',
                },
              }}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setPreferencesEditorOpen(false)}>Cancel</Button>
            <Button onClick={() => void handleSavePreferences()} variant="contained" disabled={profileSaveLoading}>
              Import preferences
            </Button>
          </DialogActions>
        </Dialog>

        <Dialog
          open={preferenceEntryEditorOpen}
          onClose={() => setPreferenceEntryEditorOpen(false)}
          fullWidth
          maxWidth="sm"
        >
          <DialogTitle>{preferenceEntryKey ? 'Edit Preference Entry' : 'Set Preference Entry'}</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <TextField
                label="Preference key"
                value={preferenceEntryKey}
                onChange={(event) => setPreferenceEntryKey(event.target.value)}
                fullWidth
                placeholder="org.athena.ui.compactMode"
              />
              <TextField
                label="Preference value"
                value={preferenceEntryValueText}
                onChange={(event) => setPreferenceEntryValueText(event.target.value)}
                fullWidth
                multiline
                minRows={6}
                placeholder={`Use JSON for objects/arrays/booleans, or plain text for strings.
Examples:
true
{"theme":"dark"}`}
                InputProps={{
                  sx: {
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, monospace',
                  },
                }}
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setPreferenceEntryEditorOpen(false)}>Cancel</Button>
            <Button
              onClick={() => void handleSavePreferenceEntry()}
              variant="contained"
              disabled={Boolean(preferenceActionLoadingKey && preferenceActionLoadingKey === preferenceEntryKey.trim())}
            >
              {preferenceActionLoadingKey && preferenceActionLoadingKey === preferenceEntryKey.trim() ? 'Saving...' : 'Save preference'}
            </Button>
          </DialogActions>
        </Dialog>

        <Dialog open={favoriteEditorOpen} onClose={closeFavoriteEditor} fullWidth maxWidth="sm">
          <DialogTitle>Add Favorite</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <Typography variant="body2" color="text.secondary">
                Search by node name, path, or ID, then choose a result to prefill the favorite target.
              </Typography>
              <Autocomplete
                options={favoriteNodeSearch.results}
                value={favoriteDraftNode}
                inputValue={favoriteNodeSearchQuery}
                onInputChange={(_, value, reason) => {
                  setFavoriteNodeSearchQuery(value);
                  if (reason === 'input' || reason === 'clear') {
                    setFavoriteDraftNode(null);
                    setFavoriteDraftNodeId('');
                  }
                }}
                onChange={(_, value) => handleFavoriteNodeSelect(value)}
                loading={favoriteNodeSearch.loading}
                filterOptions={(options) => options}
                isOptionEqualToValue={(option, value) => option.id === value.id}
                getOptionLabel={(option) => `${option.name} (${option.path})`}
                noOptionsText={
                  favoriteNodeSearchQuery.trim().length < 2
                    ? 'Type at least 2 characters to search'
                    : 'No matching nodes found'
                }
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Search nodes"
                    placeholder="Search documents or folders..."
                    helperText={
                      favoriteNodeSearch.error
                        ? favoriteNodeSearch.error
                        : 'Use the search results below to pick a node.'
                    }
                    InputProps={{
                      ...params.InputProps,
                      endAdornment: (
                        <>
                          {favoriteNodeSearch.loading ? <CircularProgress color="inherit" size={18} /> : null}
                          {params.InputProps.endAdornment}
                        </>
                      ),
                    }}
                    fullWidth
                  />
                )}
                renderOption={(props, option) => (
                  <Box component="li" {...props} alignItems="flex-start">
                    <Stack spacing={0.25} sx={{ width: '100%' }}>
                      <Typography variant="subtitle2">{option.name}</Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ wordBreak: 'break-all' }}>
                        {option.path}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {option.nodeType} · {option.id}
                      </Typography>
                    </Stack>
                  </Box>
                )}
              />
              <TextField
                label="Node ID"
                value={favoriteDraftNodeId}
                onChange={(event) => {
                  const nextValue = event.target.value;
                  setFavoriteDraftNodeId(nextValue);
                  if (favoriteDraftNode?.id !== nextValue.trim()) {
                    setFavoriteDraftNode(null);
                  }
                }}
                fullWidth
                helperText="If you already know the UUID, paste it here instead."
              />
              {favoriteDraftNode && (
                <Alert severity="success" icon={<CheckCircle fontSize="inherit" />}>
                  Selected: {favoriteDraftNode.name} · {favoriteDraftNode.path}
                </Alert>
              )}
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={closeFavoriteEditor}>Cancel</Button>
            <Button onClick={() => void handleCreateFavorite()} variant="contained" disabled={favoriteActionLoading}>
              {favoriteActionLoading ? 'Adding...' : 'Add favorite'}
            </Button>
          </DialogActions>
        </Dialog>

        <Dialog open={favoriteSiteEditorOpen} onClose={closeFavoriteSiteEditor} fullWidth maxWidth="sm">
          <DialogTitle>Add Favorite Workspace</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <Typography variant="body2" color="text.secondary">
                Search folders by name or path, then choose the workspace folder to favorite.
              </Typography>
              <Autocomplete
                options={favoriteSiteSearch.results}
                value={favoriteSiteDraftNode}
                inputValue={favoriteSiteSearchQuery}
                onInputChange={(_, value, reason) => {
                  setFavoriteSiteSearchQuery(value);
                  if (reason === 'input' || reason === 'clear') {
                    setFavoriteSiteDraftNode(null);
                    setFavoriteSiteDraftNodeId('');
                  }
                }}
                onChange={(_, value) => handleFavoriteSiteNodeSelect(value)}
                loading={favoriteSiteSearch.loading}
                filterOptions={(options) => options}
                isOptionEqualToValue={(option, value) => option.id === value.id}
                getOptionLabel={(option) => `${option.name} (${option.path})`}
                noOptionsText={
                  favoriteSiteSearchQuery.trim().length < 2
                    ? 'Type at least 2 characters to search'
                    : 'No matching folders found'
                }
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Search folders"
                    placeholder="Search workspaces..."
                    helperText={
                      favoriteSiteSearch.error
                        ? favoriteSiteSearch.error
                        : 'Only folder results are shown for favorite workspaces.'
                    }
                    InputProps={{
                      ...params.InputProps,
                      endAdornment: (
                        <>
                          {favoriteSiteSearch.loading ? <CircularProgress color="inherit" size={18} /> : null}
                          {params.InputProps.endAdornment}
                        </>
                      ),
                    }}
                    fullWidth
                  />
                )}
                renderOption={(props, option) => (
                  <Box component="li" {...props} alignItems="flex-start">
                    <Stack spacing={0.25} sx={{ width: '100%' }}>
                      <Typography variant="subtitle2">{option.name}</Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ wordBreak: 'break-all' }}>
                        {option.path}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {option.nodeType} · {option.id}
                      </Typography>
                    </Stack>
                  </Box>
                )}
              />
              <TextField
                label="Workspace folder node ID"
                value={favoriteSiteDraftNodeId}
                onChange={(event) => {
                  const nextValue = event.target.value;
                  setFavoriteSiteDraftNodeId(nextValue);
                  if (favoriteSiteDraftNode?.id !== nextValue.trim()) {
                    setFavoriteSiteDraftNode(null);
                  }
                }}
                fullWidth
                helperText="Paste a folder UUID directly if you already know it."
              />
              {favoriteSiteDraftNode && (
                <Alert severity="success" icon={<CheckCircle fontSize="inherit" />}>
                  Selected workspace: {favoriteSiteDraftNode.name} · {favoriteSiteDraftNode.path}
                </Alert>
              )}
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={closeFavoriteSiteEditor}>Cancel</Button>
            <Button onClick={() => void handleCreateFavoriteSite()} variant="contained" disabled={favoriteSiteActionLoading}>
              {favoriteSiteActionLoading ? 'Adding...' : 'Add workspace'}
            </Button>
          </DialogActions>
        </Dialog>

        <Dialog
          open={siteMembershipRequestEditorOpen}
          onClose={closeSiteMembershipRequestEditor}
          fullWidth
          maxWidth="sm"
        >
          <DialogTitle>
            {siteMembershipRequestEditingSiteId ? 'Edit Site Membership Request' : 'New Site Membership Request'}
          </DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <TextField
                label="Site ID"
                value={siteMembershipRequestDraft.siteId}
                onChange={(event) => setSiteMembershipRequestDraft((current) => ({ ...current, siteId: event.target.value }))}
                fullWidth
                disabled={Boolean(siteMembershipRequestEditingSiteId)}
                helperText={siteMembershipRequestEditingSiteId ? 'Site ID cannot be changed after creation.' : 'Enter the site short name or workspace id.'}
              />
              <TextField
                label="Site title"
                value={siteMembershipRequestDraft.siteTitle}
                onChange={(event) => setSiteMembershipRequestDraft((current) => ({ ...current, siteTitle: event.target.value }))}
                fullWidth
              />
              <TextField
                label="Role"
                value={siteMembershipRequestDraft.role}
                onChange={(event) => setSiteMembershipRequestDraft((current) => ({ ...current, role: event.target.value }))}
                fullWidth
              />
              <TextField
                label="Message"
                value={siteMembershipRequestDraft.message}
                onChange={(event) => setSiteMembershipRequestDraft((current) => ({ ...current, message: event.target.value }))}
                fullWidth
                multiline
                minRows={4}
                placeholder="Tell the site owners why you need access."
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={closeSiteMembershipRequestEditor}>Cancel</Button>
            <Button
              onClick={() => void handleSaveSiteMembershipRequest()}
              variant="contained"
              disabled={siteMembershipRequestSaving}
            >
              {siteMembershipRequestSaving
                ? 'Saving...'
                : siteMembershipRequestEditingSiteId
                  ? 'Save request'
                  : 'Create request'}
            </Button>
          </DialogActions>
        </Dialog>

        <Suspense fallback={null}>
          {previewNode && (
            <DocumentPreview
              open={Boolean(previewNode)}
              onClose={closeDocumentPreview}
              node={previewNode}
              initialCommentsOpen={previewCommentsOpen}
              initialCommentDraftText={previewCommentDraftText}
              initialCommentId={previewCommentId}
            />
          )}
        </Suspense>
      </Box>
    </Box>
  );
};

export default PeopleDirectoryPage;
