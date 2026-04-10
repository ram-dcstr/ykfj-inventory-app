# YKFJ Inventory App Documentation

Welcome to the documentation for the YKFJ Inventory App. The documentation is split into domain-specific folders to make finding things easier.

## 📁 System Architecture & Design
- [System Design & Architecture](architecture/System-Design.md) - High-level system diagram, offline sync architecture, and database logic.

## 📁 Business Logic & Rules
Contains the specific rules detailing how commerce features operate in the app:
- [Pricing and Discounts](business/Pricing-and-Discounts.md) - How prices are calculated for weighted vs fixed items.
- [Layaway](business/Layaway.md) - Reservation process, due dates, payments.
- [Paluwagan](business/Paluwagan.md) - The rotating savings pot mechanics.
- [Inventory Rules](business/Inventory-Rules.md) - Managing items, restocking, selling, product ID structures.
- [Customers](business/Customers.md) - Credit score calculations and behaviors.
- [Roles and Permissions](business/Roles-and-Permissions.md) - System users, capability matrices, and audit logging.
- [Archiving and Export](business/Archiving-and-Export.md) - PDF layouts, CSV structures, data retention.

## 📁 Networking & APIs
- [LAN Sync API](api/LAN-Sync-API.md) - Endpoints used for communication between the primary Tablet and secondary Phones over the local network.

## 📁 Database Schema
- [Database Schema](database/Schema.md) - A full breakdown of Room entities and virtual tables.

## 📁 Project Management
- [Implementation Plan](project/Implementation-Plan.md) - Phase-by-phase breakdown of app construction.

## Other References
- [CLAUDE.md](../CLAUDE.md) - Technical stack summary, coding standards, and build instructions.
