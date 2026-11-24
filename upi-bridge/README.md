# Dhanlekh UPI Bridge

A small Android app for the shop phone. It watches UPI and bank notifications,
and when money comes in it sends the text to the shop's Dhanlekh ERP server, which
pulls out the amount and the reference number and drops the payment into the bill
that is open at the counter.

- No account, no subscription, no data leaves the shop: the phone talks only to the
  shop computer's own address.
- Money going out is ignored. The same message is never sent twice.
- It also appears in the Share sheet, so a payment SMS can be sent by hand.

Set up: install the APK, paste the address shown in the ERP under
Settings → Data & Backup → UPI auto-receive → "Set up the shop phone",
then allow notification access.
