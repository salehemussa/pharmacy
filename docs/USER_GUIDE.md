# User guide

Sign in with the username and password issued to you. If the account requires a password change, the system accepts only that change until it is done. Passwords are stored as hashes. An administrator reset produces a temporary password that is shown once and must be changed at the next sign-in.

The menu shows only the tasks your role allows. The five roles are Administrator, Pharmacy Manager, Pharmacist, Cashier, and Storekeeper. An administrator can change what each role may do, except the Administrator role itself, which stays fully granted.

## Users

Administrators open Users to create an account, assign one or more roles, activate or deactivate it, and reset a password. Usernames cannot be changed. The system keeps at least one active administrator. Important user changes are written to Activity.

## Medicines and locations

Open Locations and create the physical hierarchy in this order: Store, Shelf, Rack, Position. A typical path is `STORE-A / A1 / A1-R01 / P01`.

Open Medicines to add a product. Record the name, generic name, form, strength, unit, reorder level, default purchase price, default selling price, and shelf location. The location is shown on the medicine list and on stock. Deactivate a medicine instead of deleting it. Inactive medicines can still be received, but they cannot be sold or dispensed.

Categories are maintained on the Medicines page. Dosage forms and units are maintained in Settings.

## Stock

Stock changes only through a confirmed purchase, a completed sale, dispensing, an approved return, a sale or purchase cancellation, or an authorized adjustment. Balances cannot go below zero.

The Stock page shows current quantities, low stock, out of stock, expired stock, and near-expiry stock. Batches show the batch number, expiry, quantity, price, supplier, and location. Movements show who changed the stock and why.

A storekeeper records damaged or expired write-offs, and opening stock, from the Adjustment tab. Damaged and expired stock can only be written off. An adjustment needs a reason of at least five characters.

## Purchases

Create a purchase as a draft: supplier, date, medicines, quantity, prices, batch number, expiry, and location. Saving a draft does not change stock. Confirm receipt to increase stock and store the batch. A confirmed purchase can be cancelled only while the full received quantity is still on hand. Record how much has been paid on a confirmed purchase. The status becomes unpaid, partial, or paid.

## Prescriptions and dispensing

Create a prescription for a customer with the medicine, dosage, frequency, duration, quantity, and instructions. A pending prescription can be edited. Review locks it. Dispensing is allowed after review. Choose a batch or leave the earliest expiry selected. The system will not dispense more than the remaining quantity or the available stock. Expired stock can be dispensed only when the setting allows it, the user has that permission, and a reason is entered. Each dispensing records the pharmacist, time, batch, and quantity, and reduces stock.

## Sales and payments

At Point of sale, search by name or scan a barcode, add the item, and complete payment. The sale uses the batch selling price. One payment is filled to the amount due. Split the amount by adding another payment. Cash may record the amount tendered and the change. Mobile money and card payments need a reference. Payments must equal the sale total.

A discount, on a line or on the sale, is allowed only for users with that permission and only up to the maximum percent in Settings.

The receipt can be displayed and printed. Cancelling a completed sale restores stock to the original batches unless a return is already pending or approved.

## Customers and returns

Store the customer name and phone, and any other contact details you need. History lists prescriptions, sales, and returns for that person.

A return must refer to the original sale, the lines, the quantities, and a reason. Users who can approve a return restock immediately. Otherwise the return stays pending until a manager approves or rejects it. The refund amount follows the original line total, including any allocated discount.

## Reports and alerts

The dashboard shows today's sales, total sales, sellable stock value, medicine counts, low and expired stock, near-expiry stock, pending prescriptions, recent sales, recent purchases, and alerts. These figures are calculated from the database.

Reports cover sales by day, week, or month, sales by medicine, user, and payment method, stock positions and movements, purchases, prescriptions, dispensing, and a management summary. Use the date filters and download CSV where it is offered. Sales reports omit cancelled sales. Report dates use the pharmacy timezone from Settings.

## Activity

Activity records sign-in, user changes, medicine changes, purchase confirmation, stock adjustments, sale cancellation, returns, prescription changes, and permission changes, including the user, time, record, and important before and after values.
