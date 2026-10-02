# Design decisions

- Goods are received by confirming a purchase. Opening stock is an authorized adjustment in, with a reason. There is no separate goods-receipt document.
- Medicine prices are defaults for a new purchase line. The point of sale and dispensing use the batch selling price.
- Sale payments must equal the amount due. Cash tendered and change are stored. There is no cash-drawer module.
- A purchase may record a past expiry date. That stock is on hand and is not sellable.
- Pending prescriptions and draft purchases can be edited. Confirmed, reviewed, and completed documents are cancelled or reversed. They are not deleted.
- Password reset does not send email. An administrator receives a one-time temporary password, the user must change it, and existing refresh tokens are revoked.
- The access token is kept in memory. The refresh token is returned in the login response and stored in session storage for this browser tab. It is rotated on refresh.
- Reports use `pharmacy.timezone`. Stored timestamps are UTC.
- A location is Store, then Shelf, then Rack, then Position. A medicine may sit at any active level. A location cannot be deactivated while it has an active child, an assigned medicine, or a batch with quantity on hand.
- Batch numbers are stored uppercase. A medicine and batch number are unique together.
- Expired stock can be sold or dispensed only when `inventory.allow_authorized_expired_use` is true, the user has the expired-stock permission, and the user supplies a reason. By default only the administrator has that permission.
- The administrator role's permissions cannot be edited, and the last active administrator cannot be removed.
- Cashiers do not receive the full stock-history permission. They see availability through the point of sale.
- Stock lists that filter by low, out, expired, or near-expiry status are filtered in the service and then paged. Page size is capped at 100.
- Sale line totals include the line discount and a share of the sale discount. The last line absorbs rounding. A return refund uses that line total.
- Alerts are calculated when the dashboard is opened. They are not stored as a separate inbox.
- Display settings (name, currency, timezone, expiry window, expired-use flag, and maximum discount) are readable by any signed-in user so the counter and header can format amounts. The full settings list remains restricted to settings administrators.
- Spring Security's generated in-memory user is disabled. Authentication is the pharmacy user directory only.
