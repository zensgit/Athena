import React, { useState } from 'react';
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
} from '@mui/material';
import {
  Menu as MenuIcon,
  CloudUpload,
  CreateNewFolder,
  Search,
  AccountCircle,
  Logout,
  Settings,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '@/store';
import { logout } from '@/store/slices/authSlice';
import {
  setUploadDialogOpen,
  setCreateFolderDialogOpen,
  setSearchOpen,
  toggleSidebar,
} from '@/store/slices/uiSlice';
import FolderTree from '@/components/browser/FolderTree';
import UploadDialog from '@/components/dialogs/UploadDialog';
import CreateFolderDialog from '@/components/dialogs/CreateFolderDialog';

const DRAWER_WIDTH = 280;

interface MainLayoutProps {
  children: React.ReactNode;
}

const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { sidebarOpen } = useAppSelector((state) => state.ui);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

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
    }
  };

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

          <Button
            color="inherit"
            startIcon={<CloudUpload />}
            onClick={handleUpload}
            sx={{ mr: 1 }}
          >
            Upload
          </Button>

          <Button
            color="inherit"
            startIcon={<CreateNewFolder />}
            onClick={handleCreateFolder}
            sx={{ mr: 1 }}
          >
            New Folder
          </Button>

          <IconButton color="inherit" onClick={handleSearch} sx={{ mr: 2 }}>
            <Search />
          </IconButton>

          <IconButton
            onClick={handleMenuOpen}
            size="small"
            sx={{ ml: 2 }}
            aria-controls={anchorEl ? 'account-menu' : undefined}
            aria-haspopup="true"
            aria-expanded={anchorEl ? 'true' : undefined}
          >
            <Avatar sx={{ width: 32, height: 32 }}>
              {user?.username?.charAt(0).toUpperCase()}
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
                primary={user?.username}
                secondary={user?.email}
              />
            </MenuItem>
            <Divider />
            {user?.roles?.includes('ROLE_ADMIN') && (
              <MenuItem onClick={() => navigate('/admin')}>
                <ListItemIcon>
                  <Settings fontSize="small" />
                </ListItemIcon>
                <ListItemText>Admin Dashboard</ListItemText>
              </MenuItem>
            )}
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
          width: sidebarOpen ? DRAWER_WIDTH : 0,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: DRAWER_WIDTH,
            boxSizing: 'border-box',
          },
        }}
      >
        <Toolbar />
        <Box sx={{ overflow: 'auto', p: 2 }}>
          <Typography variant="h6" gutterBottom>
            Folders
          </Typography>
          <FolderTree
            rootNodeId="root"
            onNodeSelect={handleNodeSelect}
          />
        </Box>
      </Drawer>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: `calc(100% - ${sidebarOpen ? DRAWER_WIDTH : 0}px)`,
          ml: sidebarOpen ? `${DRAWER_WIDTH}px` : 0,
          transition: (theme) =>
            theme.transitions.create(['margin', 'width'], {
              easing: theme.transitions.easing.sharp,
              duration: theme.transitions.duration.leavingScreen,
            }),
        }}
      >
        <Toolbar />
        {children}
      </Box>

      <UploadDialog />
      <CreateFolderDialog />
    </Box>
  );
};

export default MainLayout;