# ExpensesTracker — Feature Enhancement Ideas

## Current Stack (for reference)
- **Backend:** Spring Boot 3.3.5, Java 21, PostgreSQL 16, JPA/Hibernate, JWT Auth, Flyway, Apache PDFBox, OpenCSV
- **Frontend:** Angular 18, TypeScript, Chart.js, RxJS
- **AI:** Anthropic Claude API (claude-3-5-haiku) for categorization and analytics
- **Infrastructure:** Docker Compose, Nginx, Spring Actuator

---

## High Impact / Immediately Useful

### 1. Email Notifications
Spring Mail is already wired in — just needs activation.
- Budget overspend alerts (e.g., "You've used 80% of your Food budget")
- Upload completion notification
- Monthly summary email report

### 2. Financial Goals / Savings Targets
- Create goals (e.g., "Save ₹50,000 for vacation by December")
- Track progress automatically from income-expense net
- Visual progress bar on the dashboard

### 3. Recurring Transaction Templates
- Tag detected recurring bills as "confirmed recurring"
- Auto-create expected transactions each month
- Alert if a recurring payment is missed

### 4. User Profile & Preferences Page
- Update name, email, password
- Set default currency
- Toggle notification preferences
- No UI or backend endpoint exists for this yet

---

## Real-World Engagement Features

### 5. Mobile Responsive / PWA
- Convert the Angular app into a Progressive Web App
- Works offline, installable on phone home screen
- Push notifications for budget alerts

### 6. Receipt / Document Attachments
- Attach a photo or PDF receipt to any transaction
- Useful for tax time or reimbursements

### 7. Bulk Transaction Operations
- Select multiple transactions → bulk recategorize or delete
- Very useful after a large CSV import with miscategorized rows

### 8. Custom Category Management UI
- Backend already supports user-defined categories
- No UI exists to create/edit/delete them — quick win

---

## Analytics Upgrades

### 9. Net Worth Tracker
- Add asset accounts (savings, investments) alongside expenses
- Display a net worth timeline chart

### 10. Tax Report Export
- Group deductible categories (medical, education, insurance)
- Export as PDF/Excel formatted for tax filing

### 11. Peer Comparison (Anonymous Benchmarks)
- "You spend 30% more on dining compared to similar users"
- Requires anonymized aggregate data across users

### 12. Spending Forecasts
- Use 3–6 month trends to project next month's spend per category
- Warn when on track to exceed budget before month ends

---

## Security & Enterprise Readiness

### 13. Two-Factor Authentication (2FA)
- TOTP (Google Authenticator) or email OTP
- Important for an app that holds real financial data

### 14. Audit Log
- Record who changed what and when (category edits, deletes, budget changes)
- Useful for accountability and debugging

### 15. Rate Limiting
- Brute-force protection on `/auth/login`
- Abuse protection on the file upload endpoint

---

## Quick Wins (Low effort, high value)

| Feature | Effort | Impact |
|---|---|---|
| Dark mode toggle | Low | High |
| Export to Excel / PDF | Low | High |
| Category creation UI | Low | High |
| Saved filter presets on transactions page | Low | Medium |
| Search history / recent filters | Low | Medium |

---

## Known Gaps in Existing Code

### Backend
- No multi-currency conversion in analytics (transactions store currency but all math assumes INR)
- No admin endpoints or role-based access beyond `ROLE_USER`
- No full-text search index (relies on SQL `LIKE` queries)
- Database connection pool capped at 10; no Redis caching layer
- Only one API version; no versioning strategy

### Frontend
- No end-to-end or unit test files present
- No i18n / multi-language support
- No keyboard shortcuts
- Date range picker is basic HTML month input; no advanced picker

### Infrastructure
- No centralized logging (ELK) or APM
- No documented backup/restore strategy
- No CDN or asset optimization beyond Angular build

---

## Recommended Starting Point

1. **Email Notifications + Financial Goals** — uses existing infrastructure, immediately visible to users
2. **Mobile PWA** — makes the app usable daily on a phone
3. **Custom Category UI + Bulk Operations** — low effort, removes daily friction
