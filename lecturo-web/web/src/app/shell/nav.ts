export interface NavItem {
  /** i18n key for the label. */
  key: string;
  path: string;
  icon: string;
}

export const NAV_ITEMS: NavItem[] = [
  { key: 'nav.library', path: '/library', icon: 'menu_book' },
  { key: 'nav.chats', path: '/chats', icon: 'chat_bubble' },
  { key: 'nav.settings', path: '/settings', icon: 'settings' },
];
