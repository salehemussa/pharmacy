# UI acceptance report

1. **Date of UI review:** 30 September 2026
2. **Frontend technology:** Angular 19.2, PrimeNG 19.1.4, PrimeIcons 8, Tailwind CSS 4. The app was reviewed on the dev server at http://localhost:8088.
3. **Screens reviewed:** Login, Dashboard, Medicines, Medicine details (edit dialog), Inventory (current stock, batches, movements), Shelves/racks, Purchases (list, draft, new purchase), Suppliers, Prescriptions, Dispensing, POS, Customers/patients, Returns, Sales and receipt, Reports, Users, Roles, Settings, Activity, Profile.
4. **Responsive sizes tested:** 1440×900, 1280×800, 1024×768, and 768×1024. Login, Dashboard, Medicines, Inventory, and POS were captured at more than one size. At 768 the sidebar is closed until the menu button is used.
5. **Core workflows reviewed:** Sign-in to the dashboard; medicines list to the edit dialog; purchase draft with Confirm receipt visible; stock, batches, expiry, and shelf locations; a reviewed prescription with Dispense visible; POS search to a cart line, amount due, and Complete sale; an existing sale to its receipt; the returns list and new-return form; a sales report filtered from 1 September 2026 to 30 September 2026. Save, confirm, dispense, and complete-sale were not submitted, so sample stock was not changed.
6. **Visual issues discovered:** Login copy sat at the bottom of the green panel. At 768 the sidebar covered the page. Dashboard sales and purchase columns were cut off at desktop and tablet. Settings labels overlapped. The new-purchase medicine field clipped its placeholder. Dispensing used a native batch select. A prescription showed a raw status code. Keyboard focus on inputs had no visible ring.
7. **Visual issues fixed:** Login content is centered. The narrow sidebar stays off-canvas until opened, then dims the page. Dashboard tables use equal columns, drop the 680px minimum width, and stack below 1280px. Settings labels are readable words that wrap. The purchase line uses the column header instead of a clipped placeholder. Batch choice uses the shared select. Prescription status is a readable label. Inputs and buttons show a 2px green focus ring. These were checked again in the browser.
8. **Remaining known issues:** Report headers use the payload field names in the shared uppercase header style, so a column can read SALECOUNT. Empty date fields show the browser hint mm/dd/yyyy. Quantity boxes in sale, purchase, and dispensing lines are compact number fields with the shared table style. A loading skeleton is present on the dashboard and medicines, not on every list. The roles page is a long permission matrix; it loaded without page overflow.
9. **Build/test result:** `npx ng build --configuration production` completed with exit code 0 (`main-PD2JEWBD.js`, `styles-Y4OBNKOW.css`). `npx ng test --watch=false --browsers=ChromeHeadless` did not start: TS18003, no `src/**/*.spec.ts` files. During the signed-in walk the browser console list was empty and no application API call returned 400 or higher. Pages were opened for every main route used above; logo and favicon assets loaded with the pages.
10. **Final acceptance status:** **UI ACCEPTED**

An administrator session was used only to open reports, users, roles, settings, activity, and medicine details. The original administrator password hash was restored after those screenshots.

Screenshots are in `docs/ui-evidence/`. Measurements for overflow, route, and title are in `docs/ui-evidence/checks.json`. No page in that file had horizontal overflow.

| Area           | Status | Evidence |
| -------------- | ------ | -------- |
| Login          | PASS   | Split sign-in at 1440, 1280, and 1024; stacked at 768. Logo, labels, and Sign in are visible. No horizontal overflow. `login-desktop.png`, `login-laptop.png`, `login-tablet.png`, `login-portrait.png`. |
| Dashboard      | PASS   | KPI cards, alerts with text, recent sales, recent purchases, and stock signals are fully visible at 1440, 1280, and 1024. At 768 the sidebar is closed and the sections stack. `dashboard-desktop.png`, `dashboard-laptop.png`, `dashboard-tablet.png`, `dashboard-portrait.png`. |
| Medicines      | PASS   | Search, filters, table, status text, and pager are readable. Pharmacist view: `medicines-desktop.png` and portrait/tablet/laptop shots. Administrator view includes Add medicine. Edit medicine dialog shows labeled sections and Save: `medicine-details-desktop.png`. |
| Inventory      | PASS   | Summary counts, current stock, batches with expiry and location, and movements. Shelves and racks list is readable. `inventory-desktop.png`, `inventory-batches-desktop.png`, `inventory-movements-desktop.png`, `locations-desktop.png`. Portrait and tablet stock shots have no page overflow. |
| Purchases      | PASS   | List shows draft and confirmed states in words. Draft detail shows supplier, lines, locations, and Confirm receipt. New purchase shows the same steps. `purchases-desktop.png`, `purchase-detail-desktop.png`, `purchase-new-desktop.png`, `suppliers-desktop.png`. |
| Prescriptions  | PASS   | List shows Pending, Reviewed, and Fully dispensed, plus New prescription. `prescriptions-desktop.png`. |
| Dispensing     | PASS   | RX-20260930-0002 shows the step trail, availability text, batch select, and Dispense. `dispensing-desktop.png`. |
| POS            | PASS   | Two columns, empty state, then a Paracetamol line with amount due $0.12, cash payment, and Complete sale. Portrait stacks the search and cart. `pos-desktop.png`, `pos-cart-desktop.png`, `pos-portrait.png`. Receipt dialog: `receipt-desktop.png`. |
| Reports        | PASS   | Sales by period from 09/01/2026 to 09/30/2026 returned two rows after Run. `reports-results-desktop.png`. |
| Users          | PASS   | Four accounts, role text, Active badges, search, and Create user. `users-desktop.png`. |
| Responsive UI  | PASS   | No document overflow at the four sizes in `checks.json`. At 768 the menu opens over a dimmed page (`dashboard-portrait-menu.png`) and is closed on the other portrait shots. |
| Accessibility  | PASS   | Login fields are labeled. After Tab, the username field outline measured solid 2px `rgb(14, 122, 98)`. Status pills include words, not color alone. Buttons seen in the walk have text labels. |
| Console Errors | PASS   | Console and failed-API lists were empty for the signed-in capture in `checks.json`. |
| Build          | PASS   | Production build exit code 0. Unit tests could not run because the frontend has no spec files. |
