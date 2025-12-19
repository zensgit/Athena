import React, { useEffect, useState } from 'react';
import {
  Box,
  AppBar,
  Toolbar,
  IconButton,
  Typography,
  Drawer,
  Button,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Divider,
  Avatar,
  Tooltip,
} from '@mui/material';
import {
  Menu as MenuIcon,
  CloudUpload,
  CreateNewFolder,
  Search,
  AccountCircle,
  Logout,
  Settings,
  RestoreFromTrash,
  Rule as RuleIcon,
  AssignmentTurnedIn,
  InfoOutlined,
  Business,
  Star,
  LocalOffer,
  Category as CategoryIcon,
  SavedSearch as SavedSearchIcon,
  PushPin,
  PushPinOutlined,
} from '@mui/icons-material';
import { alpha } from '@mui/material/styles';
import { useNavigate, useParams } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from 'store';
import { logout } from 'store/slices/authSlice';
import {
  setUploadDialogOpen,
  setCreateFolderDialogOpen,
  setSearchOpen,
  setSidebarOpen,
  setTagManagerOpen,
  setCategoryManagerOpen,
  setShareLinkManagerOpen,
  setSidebarAutoCollapse,
  toggleSidebar,
} from 'store/slices/uiSlice';
import FolderTree from 'components/browser/FolderTree';
import UploadDialog from 'components/dialogs/UploadDialog';
import CreateFolderDialog from 'components/dialogs/CreateFolderDialog';
import TagManager from 'components/tags/TagManager';
import CategoryManager from 'components/categories/CategoryManager';
import ShareLinkManager from 'components/share/ShareLinkManager';
import MLSuggestionsDialog from 'components/ml/MLSuggestionsDialog';
import authService from 'services/authService';

const DRAWER_WIDTH_STORAGE_KEY = 'athena.ecm.drawerWidth';
const SIDEBAR_AUTO_COLLAPSE_STORAGE_KEY = 'athena.ecm.sidebarAutoCollapse';
const DEFAULT_DRAWER_WIDTH = 280;
const MIN_DRAWER_WIDTH = 220;
const MAX_DRAWER_WIDTH = 520;
const MIN_CONTENT_WIDTH = 360;

interface MainLayoutProps {
  children: React.ReactNode;
}

const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const navigate = useNavigate();
  const { nodeId } = useParams<{ nodeId: string }>();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { sidebarOpen, sidebarAutoCollapse, compactMode, tagManagerOpen, categoryManagerOpen, shareLinkManagerOpen, selectedNodeId } =
    useAppSelector((state) => state.ui);
  const effectiveUser = user ?? authService.getCurrentUser();
  const canWrite = Boolean(
    effectiveUser?.roles?.includes('ROLE_ADMIN') || effectiveUser?.roles?.includes('ROLE_EDITOR')
  );
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [drawerWidth, setDrawerWidth] = useState(() => {
    try {
      const raw = window.localStorage.getItem(DRAWER_WIDTH_STORAGE_KEY);
      const parsed = raw ? Number(raw) : Number.NaN;
      if (Number.isFinite(parsed) && parsed >= MIN_DRAWER_WIDTH && parsed <= MAX_DRAWER_WIDTH) {
        return parsed;
      }
    } catch {
      // ignore
    }
    return DEFAULT_DRAWER_WIDTH;
  });
  const [resizing, setResizing] = useState(false);

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleLogout = async () => {
    await dispatch(logout());
    navigate('/login');
  };

  const handleUpload = () => {
    dispatch(setUploadDialogOpen(true));
  };

  const handleCreateFolder = () => {
    dispatch(setCreateFolderDialogOpen(true));
  };

  const handleSearch = () => {
    dispatch(setSearchOpen(true));
  };

  const handleNodeSelect = (node: any) => {
    if (node.nodeType === 'FOLDER') {
      navigate(`/browse/${node.id}`);
      if (sidebarAutoCollapse) {
        dispatch(setSidebarOpen(false));
      }
    }
  };

  useEffect(() => {
    try {
      window.localStorage.setItem(SIDEBAR_AUTO_COLLAPSE_STORAGE_KEY, String(sidebarAutoCollapse));
    } catch {
      // ignore
    }
  }, [sidebarAutoCollapse]);

  useEffect(() => {
    const clamp = (value: number) => {
      const maxWidth = Math.max(
        MIN_DRAWER_WIDTH,
        Math.min(MAX_DRAWER_WIDTH, window.innerWidth - MIN_CONTENT_WIDTH)
      );
      return Math.max(MIN_DRAWER_WIDTH, Math.min(maxWidth, value));
    };

    const handleResize = () => {
      setDrawerWidth((current) => clamp(current));
    };

    window.addEventListener('resize', handleResize);
    handleResize();

    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  useEffect(() => {
    if (!resizing) {
      return;
    }

    const previousUserSelect = document.body.style.userSelect;
    const previousCursor = document.body.style.cursor;
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'col-resize';

    const clamp = (value: number) => {
      const maxWidth = Math.max(
        MIN_DRAWER_WIDTH,
        Math.min(MAX_DRAWER_WIDTH, window.innerWidth - MIN_CONTENT_WIDTH)
      );
      return Math.max(MIN_DRAWER_WIDTH, Math.min(maxWidth, value));
    };

    const handleMove = (event: MouseEvent) => {
      setDrawerWidth(clamp(event.clientX));
    };
    const handleUp = () => {
      setResizing(false);
    };

    window.addEventListener('mousemove', handleMove);
    window.addEventListener('mouseup', handleUp);

    return () => {
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('mouseup', handleUp);
      document.body.style.userSelect = previousUserSelect;
      document.body.style.cursor = previousCursor;
    };
  }, [resizing]);

  useEffect(() => {
    try {
      window.localStorage.setItem(DRAWER_WIDTH_STORAGE_KEY, String(drawerWidth));
    } catch {
      // ignore
    }
  }, [drawerWidth]);

  return (
    <Box sx={{ display: 'flex', height: '100vh' }}>
      <AppBar
        position="fixed"
        sx={{
          zIndex: (theme) => theme.zIndex.drawer + 1,
        }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={() => dispatch(toggleSidebar())}
            sx={{ mr: 2 }}
          >
            <MenuIcon />
          </IconButton>

          <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1 }}>
            Athena ECM
          </Typography>

          <Tooltip title={canWrite ? '' : 'Requires write permission'}>
            <span>
              <Button
                color="inherit"
                startIcon={<CloudUpload />}
                onClick={handleUpload}
                sx={{ mr: 1 }}
                disabled={!canWrite}
              >
                Upload
              </Button>
            </span>
          </Tooltip>

          <Tooltip title={canWrite ? '' : 'Requires write permission'}>
            <span>
              <Button
                color="inherit"
                startIcon={<CreateNewFolder />}
                onClick={handleCreateFolder}
                sx={{ mr: 1 }}
                disabled={!canWrite}
              >
                New Folder
              </Button>
            </span>
          </Tooltip>

          <IconButton color="inherit" aria-label="Search" onClick={handleSearch} sx={{ mr: 2 }}>
            <Search />
          </IconButton>

          <IconButton
            onClick={handleMenuOpen}
            size="small"
            sx={{ ml: 2 }}
            aria-label="Account menu"
            aria-controls={anchorEl ? 'account-menu' : undefined}
            aria-haspopup="true"
            aria-expanded={anchorEl ? 'true' : undefined}
	          >
	            <Avatar sx={{ width: 32, height: 32 }}>
	              {effectiveUser?.username?.charAt(0).toUpperCase()}
	            </Avatar>
	          </IconButton>

	          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleMenuClose}
            onClick={handleMenuClose}
            transformOrigin={{ horizontal: 'right', vertical: 'top' }}
            anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
	          >
	            <MenuItem disabled>
	              <ListItemIcon>
	                <AccountCircle fontSize="small" />
	              </ListItemIcon>
	              <ListItemText
	                primary={effectiveUser?.username}
	                secondary={effectiveUser?.email}
	              />
	            </MenuItem>
	            <Divider />
            {(effectiveUser?.roles?.includes('ROLE_ADMIN') || effectiveUser?.roles?.includes('ROLE_EDITOR')) && (
	              <MenuItem onClick={() => navigate('/rules')}>
	                <ListItemIcon>
	                  <RuleIcon fontSize="small" />
	                </ListItemIcon>
	                <ListItemText>Rules</ListItemText>
	              </MenuItem>
	            )}
            {(effectiveUser?.roles?.includes('ROLE_ADMIN') || effectiveUser?.roles?.includes('ROLE_EDITOR')) && (
              <MenuItem onClick={() => dispatch(setTagManagerOpen(true))}>
                <ListItemIcon>
                  <LocalOffer fontSize="small" />
                </ListItemIcon>
                <ListItemText>Tags</ListItemText>
              </MenuItem>
            )}
            {(effectiveUser?.roles?.includes('ROLE_ADMIN') || effectiveUser?.roles?.includes('ROLE_EDITOR')) && (
              <MenuItem onClick={() => dispatch(setCategoryManagerOpen(true))}>
                <ListItemIcon>
                  <CategoryIcon fontSize="small" />
                </ListItemIcon>
                <ListItemText>Categories</ListItemText>
              </MenuItem>
            )}
              <MenuItem onClick={() => navigate('/tasks')}>
                <ListItemIcon>
                  <AssignmentTurnedIn fontSize="small" />
                </ListItemIcon>
                <ListItemText>Tasks</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => navigate('/status')}>
                <ListItemIcon>
                  <InfoOutlined fontSize="small" />
                </ListItemIcon>
                <ListItemText>System Status</ListItemText>
              </MenuItem>
              {(effectiveUser?.roles?.includes('ROLE_ADMIN') || effectiveUser?.roles?.includes('ROLE_EDITOR')) && (
                <MenuItem onClick={() => navigate('/correspondents')}>
                  <ListItemIcon>
                    <Business fontSize="small" />
                  </ListItemIcon>
                  <ListItemText>Correspondents</ListItemText>
                </MenuItem>
              )}
	            {effectiveUser?.roles?.includes('ROLE_ADMIN') && (
	              <MenuItem onClick={() => navigate('/admin')}>
	                <ListItemIcon>
	                  <Settings fontSize="small" />
	                </ListItemIcon>
	                <ListItemText>Admin Dashboard</ListItemText>
              </MenuItem>
            )}
            <MenuItem onClick={() => navigate('/trash')}>
              <ListItemIcon>
                <RestoreFromTrash fontSize="small" />
              </ListItemIcon>
              <ListItemText>Trash</ListItemText>
            </MenuItem>
            <MenuItem onClick={() => navigate('/favorites')}>
              <ListItemIcon>
                <Star fontSize="small" />
              </ListItemIcon>
              <ListItemText>Favorites</ListItemText>
            </MenuItem>
            <MenuItem onClick={() => navigate('/saved-searches')}>
              <ListItemIcon>
                <SavedSearchIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>Saved Searches</ListItemText>
            </MenuItem>
            <MenuItem onClick={() => navigate('/settings')}>
              <ListItemIcon>
                <Settings fontSize="small" />
              </ListItemIcon>
              <ListItemText>Settings</ListItemText>
            </MenuItem>
            <MenuItem onClick={handleLogout}>
              <ListItemIcon>
                <Logout fontSize="small" />
              </ListItemIcon>
              <ListItemText>Logout</ListItemText>
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="persistent"
        anchor="left"
        open={sidebarOpen}
        sx={{
          width: sidebarOpen ? drawerWidth : 0,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
          },
        }}
		      >
		        <Toolbar />
		        <Box sx={{ overflow: 'auto', p: compactMode ? 1.5 : 2 }}>
              <Box display="flex" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
                <Typography variant="h6">Folders</Typography>
                <Tooltip
                  title={
                    sidebarAutoCollapse
                      ? 'Auto-hide sidebar after selecting a folder (on)'
                      : 'Auto-hide sidebar after selecting a folder (off)'
                  }
                >
                  <IconButton
                    size="small"
                    aria-label="Toggle auto-hide sidebar"
                    onClick={() => dispatch(setSidebarAutoCollapse(!sidebarAutoCollapse))}
                  >
                    {sidebarAutoCollapse ? <PushPinOutlined fontSize="small" /> : <PushPin fontSize="small" />}
                  </IconButton>
                </Tooltip>
              </Box>
		          <FolderTree
            rootNodeId="root"
            selectedNodeId={nodeId}
            onNodeSelect={handleNodeSelect}
          />
        </Box>
      </Drawer>

      {sidebarOpen && (
        <Box
          role="separator"
          aria-label="Resize sidebar"
          aria-orientation="vertical"
          aria-valuemin={MIN_DRAWER_WIDTH}
          aria-valuemax={MAX_DRAWER_WIDTH}
          aria-valuenow={drawerWidth}
          title="Drag to resize. Double-click to reset."
          tabIndex={0}
          onMouseDown={(event) => {
            if (event.button !== 0) {
              return;
            }
            event.preventDefault();
            setResizing(true);
          }}
          onKeyDown={(event) => {
            const clamp = (value: number) => {
              const maxWidth = Math.max(
                MIN_DRAWER_WIDTH,
                Math.min(MAX_DRAWER_WIDTH, window.innerWidth - MIN_CONTENT_WIDTH)
              );
              return Math.max(MIN_DRAWER_WIDTH, Math.min(maxWidth, value));
            };

            const step = event.shiftKey ? 40 : 20;

            if (event.key === 'ArrowLeft') {
              event.preventDefault();
              setDrawerWidth((current) => clamp(current - step));
              return;
            }
            if (event.key === 'ArrowRight') {
              event.preventDefault();
              setDrawerWidth((current) => clamp(current + step));
              return;
            }
            if (event.key === 'Home') {
              event.preventDefault();
              setDrawerWidth(MIN_DRAWER_WIDTH);
              return;
            }
            if (event.key === 'End') {
              event.preventDefault();
              setDrawerWidth(clamp(MAX_DRAWER_WIDTH));
              return;
            }
            if (event.key === 'Enter') {
              event.preventDefault();
              setDrawerWidth(DEFAULT_DRAWER_WIDTH);
            }
          }}
          onDoubleClick={() => setDrawerWidth(DEFAULT_DRAWER_WIDTH)}
	          sx={{
	            width: 6,
	            flexShrink: 0,
	            cursor: 'col-resize',
	            backgroundColor: (theme) => alpha(theme.palette.divider, 0.35),
	            '&:hover': {
	              backgroundColor: (theme) => alpha(theme.palette.divider, 0.8),
	            },
	            '&:focus-visible': {
	              outline: '2px solid',
	              outlineColor: 'primary.main',
	              outlineOffset: -2,
            },
          }}
        />
      )}

      <Box
	        component="main"
	        sx={{
	          flexGrow: 1,
	          minWidth: 0,
	          p: compactMode ? 2 : 3,
	        }}
	      >
	        <Toolbar />
	        {children}
	      </Box>

      <UploadDialog />
      <CreateFolderDialog />
      <TagManager
        open={tagManagerOpen}
        selectedNodeId={selectedNodeId || undefined}
        onClose={() => dispatch(setTagManagerOpen(false))}
      />
      <CategoryManager
        open={categoryManagerOpen}
        selectedNodeId={selectedNodeId || undefined}
        onClose={() => dispatch(setCategoryManagerOpen(false))}
      />
      <ShareLinkManager
        open={shareLinkManagerOpen}
        selectedNodeId={selectedNodeId || undefined}
        onClose={() => dispatch(setShareLinkManagerOpen(false))}
      />
      <MLSuggestionsDialog />
    </Box>
  );
};

export default MainLayout;
