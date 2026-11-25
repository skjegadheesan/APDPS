# APDPS - Agricultural Product Direct Purchase System

A Java Swing desktop application that connects farmers directly with buyers. Farmers list their products,
buyers browse and order them, and an administrator manages the marketplace.

## Features

**Buyer**
- Browse products as a grid of cards: photo, name, code, category, farmer, price, rating and stock
- Search by name or code, filter by category
- Cart, checkout, order history, order cancellation and invoices
- Product reviews, return requests and a farmer credit ranking

**Farmer**
- Register, add and manage products and stock, see orders for their products
- Receive notices when the administrator removes a product

**Administrator**
- Manage categories, products, users, orders and return requests
- Upload a photo for each product (stored in the database)
- Make products available / unavailable, delete products or users with a reason

## Technology
- Java 11 or later, Swing (Nimbus look and feel)
- JDBC with Oracle Database
- Package: `apdps`, main class: `apdps.APDPSApp`

## Getting started
1. Install a JDK (11+) and have an Oracle Database you can connect to.
2. Create the database schema the application expects. The schema scripts are not part of this repository.
3. Add the Oracle JDBC driver (`ojdbc8.jar` or `ojdbc11.jar`) to the project libraries.
4. Set the connection in `src/apdps/DBConnection.java`, or use the environment variables
   `APDPS_DB_URL`, `APDPS_DB_USER` and `APDPS_DB_PASSWORD`.
5. Run `apdps.APDPSApp`.

## Project layout
```
src/apdps/    application source (pages, dashboards, data access, theme)
```
The colours and fonts are all in `Theme.java`.
