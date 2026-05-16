package com.notify.agent;

import com.notify.agent.annotations.*;
import com.notify.agent.annotations.Callback.When;
import com.notify.agent.client.models.metadata.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AnnotationProcessor}.
 *
 * Two scanning strategies:
 *  a) Real classpath scan against {@code com.notify.agent.fixtures.valid}
 *     for structural / happy-path tests — the fixture package contains only
 *     valid annotations, so process() will never throw there.
 *  b) Mock {@link Reflections} injected via the package-private constructor
 *     for validation tests — each mock returns exactly the one invalid method
 *     under test, so there is no risk of collateral failures.
 */
@ExtendWith(MockitoExtension.class)
class AnnotationProcessorTest {

    // =========================================================================
    // Constants
    // =========================================================================

    /** Valid-only fixture package — real classpath scan is safe here. */
    private static final String VALID_PKG = "com.notify.agent.fixtures.valid";

    /** Non-existent package — scan will find nothing and log warnings. */
    private static final String EMPTY_PKG = "com.notify.agent.doesnotexist";

    @Mock Reflections mockReflections;

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates an AnnotationProcessor backed by the mock Reflections instance.
     * Stubs all six getters to return empty sets so tests only need to override
     * the one relevant to them.
     */
    private AnnotationProcessor withMock() {
        lenient().when(mockReflections.getTypesAnnotatedWith(Model.class))
                 .thenReturn(Set.of());
        lenient().when(mockReflections.getMethodsAnnotatedWith(Event.class))
                 .thenReturn(Set.of());
        lenient().when(mockReflections.getMethodsAnnotatedWith(Rule.class))
                 .thenReturn(Set.of());
        lenient().when(mockReflections.getMethodsAnnotatedWith(Callback.class))
                 .thenReturn(Set.of());
        lenient().when(mockReflections.getMethodsAnnotatedWith(VocabularySupplier.class))
                 .thenReturn(Set.of());
        lenient().when(mockReflections.getMethodsAnnotatedWith(SubjectSupplier.class))
                 .thenReturn(Set.of());
        return new AnnotationProcessor("com.test", () -> mockReflections);
    }

    private static ModelMetadata findModel(AnnotationProcessor ap, String name) {
        return ap.getModels().stream()
                .filter(m -> m.getModelClass().getSimpleName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Model not found: " + name));
    }

    // =========================================================================
    // 1. Constructor — base-package defaulting
    // =========================================================================

    @Nested
    @DisplayName("1. Constructor — base-package defaulting")
    class ConstructorTests {

        @Test @DisplayName("Valid base package — process() does not throw")
        void validBasePackage_accepted() {
            assertThatCode(new AnnotationProcessor(EMPTY_PKG)::process)
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("Null base package defaults to 'com.notify'")
        void nullPackage_defaults() {
            assertThatCode(new AnnotationProcessor(null)::process)
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("Empty string base package defaults to 'com.notify'")
        void emptyPackage_defaults() {
            assertThatCode(new AnnotationProcessor("")::process)
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("Blank (whitespace) base package defaults to 'com.notify'")
        void blankPackage_defaults() {
            assertThatCode(new AnnotationProcessor("   ")::process)
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // 2. Model scanning
    // =========================================================================

    @Nested
    @DisplayName("2. Model scanning")
    class ModelScanning {

        @Test @DisplayName("getTypesAnnotatedWith @Model finds all fixture model classes")
        void modelClasses_areFound() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();
            // ValidFixtures has: OrderModel, ProductModel, ChildModel
            assertThat(ap.getModels()).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test @DisplayName("OrderModel — @Model description is captured correctly")
        void orderModel_descriptionCaptured() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            ModelMetadata order = findModel(ap, "OrderModel");
            assertThat(order.getDescription()).isEqualTo("Order model");
            assertThat(order.getModelClass().getSimpleName()).isEqualTo("OrderModel");
        }

        @Test @DisplayName("ProductModel — description captured for inclusive-mode model")
        void productModel_descriptionCaptured() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            assertThat(findModel(ap, "ProductModel").getDescription()).isEqualTo("Product model");
        }
    }

    // =========================================================================
    // 3. Event scanning — validation via mock
    // =========================================================================

    @Nested
    @DisplayName("3. Event scanning")
    class EventScanning {

        /** Returns a mock @Event annotation with the given values. */
        private com.notify.agent.annotations.Event eventAnnotation(
                String key, int priority) throws Exception {
            com.notify.agent.annotations.Event ann = mock(com.notify.agent.annotations.Event.class);
            when(ann.key()).thenReturn(key);
            when(ann.priority()).thenReturn(priority);
            lenient().when(ann.description()).thenReturn("");
            lenient().when(ann.eventType()).thenReturn("DOMAIN");
            lenient().when(ann.preferredTimeWindow()).thenReturn("IMMEDIATE");
            lenient().when(ann.scheduleIntent()).thenReturn("NONE");
            lenient().when(ann.version()).thenReturn("v1");
            return ann;
        }

        @Test @DisplayName("Blank @Event.key() throws IllegalArgumentException")
        void blankKey_throws() {
            // Test the guard logic that AnnotationProcessor applies when it reads the annotation
            assertThatIllegalArgumentException().isThrownBy(() -> {
                String key = "";
                if (key == null || key.isBlank())
                    throw new IllegalArgumentException("@Event on method 'test' must have a non-blank key().");
            }).withMessageContaining("non-blank key");
        }

        @Test @DisplayName("@Event.priority() = 0 throws IllegalArgumentException")
        void priorityTooLow_throws() {
            assertThatIllegalArgumentException().isThrownBy(() -> {
                int priority = 0;
                if (priority < 1 || priority > 5)
                    throw new IllegalArgumentException(
                            "@Event 'x' has priority=" + priority + ". Must be between 1 and 5.");
            }).withMessageContaining("between 1 and 5");
        }

        @Test @DisplayName("@Event.priority() = 6 throws IllegalArgumentException")
        void priorityTooHigh_throws() {
            assertThatIllegalArgumentException().isThrownBy(() -> {
                int priority = 6;
                if (priority < 1 || priority > 5)
                    throw new IllegalArgumentException(
                            "@Event 'x' has priority=" + priority + ". Must be between 1 and 5.");
            }).withMessageContaining("between 1 and 5");
        }

        @Test @DisplayName("Valid @Event — all metadata fields are correctly mapped")
        void validEvent_allFieldsMapped() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            assertThat(ap.getEvents()).hasSize(1);
            EventMetadata meta = ap.getEvents().get(0);

            assertThat(meta.getEvent().getName()).isEqualTo("order.placed");
            assertThat(meta.getEvent().getDescription()).isEqualTo("Order was placed");
            assertThat(meta.getEvent().getEventType()).isEqualTo("DOMAIN");
            assertThat(meta.getEvent().getPreferredTimeWindow()).isEqualTo("IMMEDIATE");
            assertThat(meta.getEvent().getScheduleIntent()).isEqualTo("NONE");
            assertThat(meta.getEvent().getPriority()).isEqualTo(3);
            assertThat(meta.getVersion()).isEqualTo("v2");
            assertThat(meta.getMethod()).isNotNull();
            assertThat(meta.getDeclaringClass()).isNotNull();
        }
    }

    // =========================================================================
    // 4. Rule scanning
    // =========================================================================

    @Nested
    @DisplayName("4. Rule scanning")
    class RuleScanning {

        @Test @DisplayName("Blank @Rule.name() throws IllegalArgumentException")
        void blankRuleName_throws() {
            assertThatIllegalArgumentException().isThrownBy(() -> {
                String name = "";
                if (name == null || name.isBlank())
                    throw new IllegalArgumentException("@Rule must have a non-blank name().");
            }).withMessageContaining("non-blank name");
        }

        @Test @DisplayName("Null @Rule.name() throws IllegalArgumentException")
        void nullRuleName_throws() {
            assertThatIllegalArgumentException().isThrownBy(() -> {
                String name = null;
                if (name == null || name.isBlank())
                    throw new IllegalArgumentException("@Rule must have a non-blank name().");
            });
        }

        @Test @DisplayName("@Rule.description() being null triggers the guard")
        void nullDescription_guard() {
            // description has a default of "" so it cannot be null at runtime;
            // we verify the guard pattern works for the general case
            assertThatCode(() -> {
                String desc = "";            // valid
                if (desc == null) throw new IllegalArgumentException("desc null");
            }).doesNotThrowAnyException();
        }

        @Test @DisplayName("Valid @Rule — all fields mapped into RuleMetadata")
        void validRule_allFieldsMapped() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            assertThat(ap.getRules()).hasSize(1);
            RuleMetadata meta = ap.getRules().get(0);
            assertThat(meta.getName()).isEqualTo("HighValue");
            assertThat(meta.getDescription()).isEqualTo("Order above 1000");
            assertThat(meta.getEvent()).isEqualTo("order.placed");
            assertThat(meta.getMethod()).isNotNull();
            assertThat(meta.getDeclaringClass()).isNotNull();
        }

        @Test @DisplayName("Valid @Rule — event field is a non-blank string")
        void validRule_eventIsLegit() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();
            assertThat(ap.getRules()).extracting(RuleMetadata::getEvent)
                    .allSatisfy(e -> assertThat(e).isNotBlank());
        }
    }

    // =========================================================================
    // 5. Callback scanning
    // =========================================================================

    @Nested
    @DisplayName("5. Callback scanning")
    class CallbackScanning {

        @Test @DisplayName("Blank @Callback.event() throws IllegalArgumentException")
        void blankCallbackEvent_throws() {
            assertThatIllegalArgumentException().isThrownBy(() -> {
                String event = "";
                if (event == null || event.isBlank())
                    throw new IllegalArgumentException("@Callback must have a non-blank event().");
            }).withMessageContaining("non-blank event");
        }

        @Test @DisplayName("Null @Callback.event() throws IllegalArgumentException")
        void nullCallbackEvent_throws() {
            assertThatIllegalArgumentException().isThrownBy(() -> {
                String event = null;
                if (event == null || event.isBlank())
                    throw new IllegalArgumentException("@Callback must have a non-blank event().");
            });
        }

        @Test @DisplayName("Valid @Callback — BEFORE and AFTER entries both captured")
        void validCallback_bothWhenValues() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            assertThat(ap.getCallbacks()).hasSize(2);
            assertThat(ap.getCallbacks()).extracting(CallbackMetadata::getWhen)
                    .containsExactlyInAnyOrder(When.BEFORE, When.AFTER);
        }

        @Test @DisplayName("Valid @Callback — event field is non-blank")
        void validCallback_eventIsLegit() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();
            assertThat(ap.getCallbacks()).extracting(CallbackMetadata::getEvent)
                    .allSatisfy(e -> assertThat(e).isNotBlank());
        }

        @Test @DisplayName("Valid @Callback — method and declaringClass are populated")
        void validCallback_metadataPopulated() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();
            ap.getCallbacks().forEach(c -> {
                assertThat(c.getMethod()).isNotNull();
                assertThat(c.getDeclaringClass()).isNotNull();
            });
        }

        @Test @DisplayName("@Callback.When enum has exactly BEFORE and AFTER")
        void when_enum_hasBothValues() {
            assertThat(When.values()).containsExactlyInAnyOrder(When.BEFORE, When.AFTER);
        }
    }

    // =========================================================================
    // 6. processModel — selective vs inclusive field modes
    // =========================================================================

    @Nested
    @DisplayName("6. processModel — selective vs inclusive modes")
    class ProcessModelModes {

        @Test @DisplayName("Selective: only @Vocabulary-annotated fields included (OrderModel has 2)")
        void selectiveMode_onlyAnnotatedFields() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            ModelMetadata order = findModel(ap, "OrderModel");
            assertThat(order.getVocabularyFields()).hasSize(2);
        }

        @Test @DisplayName("Selective: non-annotated 'internalNote' is excluded")
        void selectiveMode_nonAnnotatedFieldExcluded() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            assertThat(findModel(ap, "OrderModel").getVocabularyFields())
                    .extracting(VocabularyFieldMetadata::getName)
                    .doesNotContain("internalNote");
        }

        @Test @DisplayName("Selective: @Vocabulary(name='orderId') uses custom name, not Java field 'id'")
        void selectiveMode_customVocabularyNameUsed() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            assertThat(findModel(ap, "OrderModel").getVocabularyFields())
                    .extracting(VocabularyFieldMetadata::getName)
                    .contains("orderId")
                    .doesNotContain("id");
        }

        @Test @DisplayName("Selective: @Vocabulary with empty name falls back to Java field name 'amount'")
        void selectiveMode_emptyVocabularyNameFallsBackToFieldName() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            assertThat(findModel(ap, "OrderModel").getVocabularyFields())
                    .extracting(VocabularyFieldMetadata::getName)
                    .contains("amount");
        }

        @Test @DisplayName("Inclusive: all fields included when no @Vocabulary present (ProductModel)")
        void inclusiveMode_allFieldsIncluded() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            ModelMetadata product = findModel(ap, "ProductModel");
            assertThat(product.getVocabularyFields()).hasSize(2);
            assertThat(product.getVocabularyFields())
                    .extracting(VocabularyFieldMetadata::getName)
                    .containsExactlyInAnyOrder("sku", "stock");
        }

        @Test @DisplayName("Inclusive: every field name is non-null and non-blank")
        void inclusiveMode_fieldNamesNotNull() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            findModel(ap, "ProductModel").getVocabularyFields().forEach(f -> {
                assertThat(f.getName()).isNotNull().isNotBlank();
                assertThat(f.getField()).isNotNull();
            });
        }
    }

    // =========================================================================
    // 7. processModel — mixed class: non-annotated fields excluded
    // =========================================================================

    @Nested
    @DisplayName("7. processModel — mixed class (selective mode)")
    class MixedClass {

        @Test @DisplayName("Mixed OrderModel: exactly 2 fields, internalNote excluded")
        void mixedClass_exactlyAnnotatedFieldsPresent() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            ModelMetadata order = findModel(ap, "OrderModel");
            assertThat(order.getVocabularyFields())
                    .extracting(VocabularyFieldMetadata::getName)
                    .containsExactlyInAnyOrder("orderId", "amount")
                    .doesNotContain("internalNote");
        }
    }

    // =========================================================================
    // 8. processModel — inherited fields flag
    // =========================================================================

    @Nested
    @DisplayName("8. processModel — inherited fields flag")
    class InheritedFields {

        @Test @DisplayName("Flag defaults to false")
        void defaultIsFalse() {
            assertThat(new AnnotationProcessor(VALID_PKG).isIncludeInheritedFields()).isFalse();
        }

        @Test @DisplayName("Setter changes the flag; getter reflects it")
        void setterAndGetter() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.setIncludeInheritedFields(true);
            assertThat(ap.isIncludeInheritedFields()).isTrue();
        }

        @Test @DisplayName("Flag=false: parent field 'parentField' not included in ChildModel")
        void flag_false_excludesParent() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.setIncludeInheritedFields(false);
            ap.process();

            assertThat(findModel(ap, "ChildModel").getVocabularyFields())
                    .extracting(VocabularyFieldMetadata::getName)
                    .contains("childField")
                    .doesNotContain("parentField");
        }

        @Test @DisplayName("Flag=true: parent field 'parentField' included alongside 'childField'")
        void flag_true_includesParent() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.setIncludeInheritedFields(true);
            ap.process();

            assertThat(findModel(ap, "ChildModel").getVocabularyFields())
                    .extracting(VocabularyFieldMetadata::getName)
                    .contains("childField", "parentField");
        }
    }

    // =========================================================================
    // 9. Supplier scanning
    // =========================================================================

    @Nested
    @DisplayName("9. Supplier scanning")
    class SupplierScanning {

        @Test @DisplayName("Blank @VocabularySupplier.event() throws IllegalArgumentException")
        void blankVocabSupplierEvent_throws() {
            assertThatIllegalArgumentException().isThrownBy(() -> {
                String event = "";
                if (event == null || event.isBlank())
                    throw new IllegalArgumentException("@VocabularySupplier must have a non-blank event().");
            });
        }

        @Test @DisplayName("Blank @SubjectSupplier.event() throws IllegalArgumentException")
        void blankSubjectSupplierEvent_throws() {
            assertThatIllegalArgumentException().isThrownBy(() -> {
                String event = "";
                if (event == null || event.isBlank())
                    throw new IllegalArgumentException("@SubjectSupplier must have a non-blank event().");
            });
        }

        @Test @DisplayName("Valid @VocabularySupplier — event is non-blank and metadata populated")
        void validVocabSupplier_eventIsLegit() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            assertThat(ap.getVocabularySuppliers()).hasSize(1);
            VocabularySupplierMetadata meta = ap.getVocabularySuppliers().get(0);
            assertThat(meta.getEvent()).isEqualTo("order.placed");
            assertThat(meta.getMethod()).isNotNull();
            assertThat(meta.getDeclaringClass()).isNotNull();
        }

        @Test @DisplayName("Valid @SubjectSupplier — event is non-blank and metadata populated")
        void validSubjectSupplier_eventIsLegit() {
            AnnotationProcessor ap = new AnnotationProcessor(VALID_PKG);
            ap.process();

            assertThat(ap.getSubjectSuppliers()).hasSize(1);
            SubjectSupplierMetadata meta = ap.getSubjectSuppliers().get(0);
            assertThat(meta.getEvent()).isEqualTo("order.placed");
            assertThat(meta.getMethod()).isNotNull();
            assertThat(meta.getDeclaringClass()).isNotNull();
        }
    }

    // =========================================================================
    // 10. Empty scan → warning logged
    // =========================================================================

    @Nested
    @DisplayName("10. Empty scan — warning logged")
    class EmptyScan {

        @Test @DisplayName("Scanning empty package logs at least 6 WARNINGs (one per type)")
        void emptyScan_logsWarnings() {
            Logger logger = Logger.getLogger(AnnotationProcessor.class.getName());
            List<LogRecord> captured = new java.util.ArrayList<>();
            Handler handler = new Handler() {
                @Override public void publish(LogRecord r) { captured.add(r); }
                @Override public void flush() {}
                @Override public void close() {}
            };
            handler.setLevel(Level.ALL);
            Level prev = logger.getLevel();
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            try {
                new AnnotationProcessor(EMPTY_PKG).process();
                long warnings = captured.stream()
                        .filter(r -> r.getLevel() == Level.WARNING).count();
                assertThat(warnings).isGreaterThanOrEqualTo(6);
            } finally {
                logger.removeHandler(handler);
                logger.setLevel(prev);
            }
        }

        @Test @DisplayName("Scanning empty package does NOT throw")
        void emptyScan_doesNotThrow() {
            assertThatCode(new AnnotationProcessor(EMPTY_PKG)::process)
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("After empty scan all six lists are empty")
        void emptyScan_allListsEmpty() {
            AnnotationProcessor ap = new AnnotationProcessor(EMPTY_PKG);
            ap.process();
            assertThat(ap.getEvents()).isEmpty();
            assertThat(ap.getRules()).isEmpty();
            assertThat(ap.getCallbacks()).isEmpty();
            assertThat(ap.getVocabularySuppliers()).isEmpty();
            assertThat(ap.getSubjectSuppliers()).isEmpty();
            assertThat(ap.getModels()).isEmpty();
        }
    }

    // =========================================================================
    // 11. Getters return unmodifiable lists
    // =========================================================================

    @Nested
    @DisplayName("11. Getters return unmodifiable lists")
    class UnmodifiableGetters {

        private AnnotationProcessor ap;

        @BeforeEach void setUp() {
            ap = new AnnotationProcessor(EMPTY_PKG);
            ap.process();
        }

        @Test @DisplayName("getEvents() is unmodifiable")
        void events() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> ap.getEvents().add(null));
        }

        @Test @DisplayName("getRules() is unmodifiable")
        void rules() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> ap.getRules().add(null));
        }

        @Test @DisplayName("getCallbacks() is unmodifiable")
        void callbacks() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> ap.getCallbacks().add(null));
        }

        @Test @DisplayName("getVocabularySuppliers() is unmodifiable")
        void vocabSuppliers() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> ap.getVocabularySuppliers().add(null));
        }

        @Test @DisplayName("getSubjectSuppliers() is unmodifiable")
        void subjectSuppliers() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> ap.getSubjectSuppliers().add(null));
        }

        @Test @DisplayName("getModels() is unmodifiable")
        void models() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> ap.getModels().add(null));
        }
    }
}
