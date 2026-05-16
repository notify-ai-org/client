/**
 * Valid-only fixtures — every annotation here is correctly configured.
 * Tests that verify happy-path behaviour scan this package.
 */
package com.notify.agent.fixtures.valid;

import com.notify.agent.annotations.*;
import com.notify.agent.annotations.Callback.When;

// ---------------------------------------------------------------------------
// Model fixtures
// ---------------------------------------------------------------------------

/** Selective-mode model: some fields annotated, one not. */
@Model(description = "Order model")
class OrderModel {
    @Vocabulary(name = "orderId", description = "Unique order identifier")
    public String id;

    @Vocabulary            // empty name → falls back to field name "amount"
    public double amount;

    public String internalNote; // NOT annotated → excluded in selective mode
}

/** Inclusive-mode model: no @Vocabulary annotations at all. */
@Model(description = "Product model")
class ProductModel {
    public String sku;
    public int stock;
}

/** Child model for the inherited-fields flag test. */
@Model(description = "Child model")
class ChildModel extends ParentModel {
    public String childField;
}

class ParentModel {
    public String parentField;
}

// ---------------------------------------------------------------------------
// Event fixtures
// ---------------------------------------------------------------------------

class EventFixtures {
    @Event(key = "order.placed", description = "Order was placed",
           eventType = "DOMAIN", preferredTimeWindow = "IMMEDIATE",
           scheduleIntent = "NONE", priority = 3, version = "v2")
    public void onOrderPlaced() {}
}

// ---------------------------------------------------------------------------
// Rule fixtures
// ---------------------------------------------------------------------------

class RuleFixtures {
    @Rule(name = "HighValue", description = "Order above 1000", event = "order.placed")
    public void highValueRule() {}
}

// ---------------------------------------------------------------------------
// Callback fixtures
// ---------------------------------------------------------------------------

class CallbackFixtures {
    @Callback(event = "order.placed", when = When.BEFORE)
    public void beforeOrderPlaced() {}

    @Callback(event = "order.placed", when = When.AFTER)
    public void afterOrderPlaced() {}
}

// ---------------------------------------------------------------------------
// Supplier fixtures
// ---------------------------------------------------------------------------

class SupplierFixtures {
    @VocabularySupplier(event = "order.placed", description = "Order vocabulary")
    public Object vocabSupplier() { return null; }

    @SubjectSupplier(event = "order.placed", description = "Order subjects")
    public Object subjectSupplier() { return null; }
}
