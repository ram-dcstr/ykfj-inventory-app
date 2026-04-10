# Pricing and Discounts

## Pricing

### Weighted Items (dynamic price)
Selling price is NEVER stored in the products table for weighted items. Always calculated at display time:

```
displayPrice = product.weight_grams × metalRate.price_per_gram
```

When metal rate changes → all weighted products using that rate auto-update instantly.

### Fixed Items (static price)
Selling price stored directly in `products.selling_price`. Only changes when manually edited.

### Sold Price (snapshot)
When selling ANY item (weighted or fixed), the `sold_record` captures:
- `sold_price` = calculated selling price AT TIME OF SALE
- `capital_price` = product capital AT TIME OF SALE

This preserves historical accuracy even if rates change later.

### Currency
All monetary values formatted as Philippine Peso: ₱3,200.00
Using `CurrencyFormatter.kt` utility with `Locale("en", "PH")`.

## Discounts

Admin and Manager can apply discounts when selling. Staff cannot give discounts.

- **Fixed discount:** Flat amount off per unit (e.g., ₱200 off)
- **Percentage discount:** Percentage off per unit (e.g., 5%)
- **Max discount cap:** Cannot exceed 20% of the product profit (selling_price - capital_price)
- Discount details saved in sold_record: `discount_amount`, `discount_type` (NONE/FIXED/PERCENTAGE)
- `sold_price` in the record is the **final price after discount**
