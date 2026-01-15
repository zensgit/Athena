# Mail Automation P2 OAuth Verification

## Automated Tests
- `cd ecm-core && mvn test`
  - Result: pass
- `cd ecm-frontend && npm run lint`
  - Result: pass
- `cd ecm-frontend && CI=true npm test`
  - Result: pass

## Manual Checks (Not Run)
- Create an OAuth2 account and verify token refresh via provider endpoint.
- Confirm IMAP XOAUTH2 login against Gmail/Outlook.
