package com.notify.agent;

import com.notify.agent.Buffer.Record;
import com.notify.agent.Buffer.RecordType;
import com.notify.agent.client.models.ClassModel;
import com.notify.agent.client.models.EventCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Buffer}.
 *
 * No mocks needed — Buffer is a pure in-memory data structure.
 *
 * Test groups:
 *  1. Constructor defaults  — invalid batch size / timeout fall back to sane values.
 *  2. add() / typed helpers — addEventCapture, addVocabulary, addRule enqueue correctly.
 *  3. drainTo()             — partial drain, full drain, max-capped drain.
 *  4. take()                — blocking take, type/payload integrity.
 *  5. size() / isEmpty()    — reflect queue state accurately.
 *  6. Flush predicates      — shouldFlushBySize, shouldFlushByTimeout, markFlushed.
 *  7. Record inner class    — type and payload accessors.
 *  8. Concurrency           — multiple producers don't drop or corrupt records.
 */
class BufferTest {

    // =========================================================================
    // 1. Constructor defaults
    // =========================================================================

    @Nested
    @DisplayName("1. Constructor defaults")
    class ConstructorDefaults {

        @Test
        @DisplayName("Zero batchSize is replaced by default 100")
        void zeroBatchSize_usesDefault() {
            Buffer b = new Buffer(0, 1_000);
            assertThat(b.getBatchSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("Negative batchSize is replaced by default 100")
        void negativeBatchSize_usesDefault() {
            Buffer b = new Buffer(-5, 1_000);
            assertThat(b.getBatchSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("Positive batchSize is kept as-is")
        void positiveBatchSize_isPreserved() {
            Buffer b = new Buffer(42, 1_000);
            assertThat(b.getBatchSize()).isEqualTo(42);
        }

        @Test
        @DisplayName("Zero flushTimeout is replaced by default 5000 ms")
        void zeroFlushTimeout_usesDefault() {
            Buffer b = new Buffer(10, 0);
            assertThat(b.getFlushTimeoutMs()).isEqualTo(5_000);
        }

        @Test
        @DisplayName("Negative flushTimeout is replaced by default 5000 ms")
        void negativeFlushTimeout_usesDefault() {
            Buffer b = new Buffer(10, -100);
            assertThat(b.getFlushTimeoutMs()).isEqualTo(5_000);
        }

        @Test
        @DisplayName("Positive flushTimeout is kept as-is")
        void positiveFlushTimeout_isPreserved() {
            Buffer b = new Buffer(10, 2_000);
            assertThat(b.getFlushTimeoutMs()).isEqualTo(2_000);
        }

        @Test
        @DisplayName("New buffer is empty")
        void newBuffer_isEmpty() {
            Buffer b = new Buffer(10, 1_000);
            assertThat(b.isEmpty()).isTrue();
            assertThat(b.size()).isZero();
        }
    }

    // =========================================================================
    // 2. add() and typed helper methods
    // =========================================================================

    @Nested
    @DisplayName("2. add() and typed helpers")
    class AddAndHelpers {

        @Test
        @DisplayName("add() enqueues a record with the correct type and payload")
        void add_enqueuesRecord() {
            Buffer b = new Buffer(10, 1_000);
            String payload = "test-payload";

            b.add(RecordType.EVENT_CAPTURE, payload);

            assertThat(b.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("addEventCapture() enqueues an EVENT_CAPTURE record")
        void addEventCapture_enqueuedWithCorrectType() {
            Buffer b = new Buffer(10, 1_000);
            EventCapture dto = new EventCapture();

            b.addEventCapture(dto);

            List<Record> out = new ArrayList<>();
            b.drainTo(out, 1);
            assertThat(out).hasSize(1);
            assertThat(out.get(0).getType()).isEqualTo(RecordType.EVENT_CAPTURE);
            assertThat(out.get(0).getPayload()).isSameAs(dto);
        }

        @Test
        @DisplayName("addVocabulary() enqueues a VOCABULARY record with a defensive copy of the list")
        void addVocabulary_enqueuedWithCorrectType() {
            Buffer b = new Buffer(10, 1_000);
            ClassModel model = new ClassModel();
            List<ClassModel> models = new ArrayList<>(List.of(model));

            b.addVocabulary(models);

            // Mutate original list — buffer should NOT be affected
            models.add(new ClassModel());

            List<Record> out = new ArrayList<>();
            b.drainTo(out, 1);
            assertThat(out.get(0).getType()).isEqualTo(RecordType.VOCABULARY);

            @SuppressWarnings("unchecked")
            List<ClassModel> stored = (List<ClassModel>) out.get(0).getPayload();
            assertThat(stored).hasSize(1); // still the original snapshot
        }

        @Test
        @DisplayName("addRule() enqueues a RULE record with all four fields")
        void addRule_enqueuesMapWithAllFields() {
            Buffer b = new Buffer(10, 1_000);

            b.addRule("OrderPlaced", "HighValue", "Order > 1000", Map.of("threshold", 1000));

            List<Record> out = new ArrayList<>();
            b.drainTo(out, 1);
            assertThat(out.get(0).getType()).isEqualTo(RecordType.RULE);

            @SuppressWarnings("unchecked")
            Map<String, Object> ruleMap = (Map<String, Object>) out.get(0).getPayload();
            assertThat(ruleMap)
                    .containsEntry("eventName", "OrderPlaced")
                    .containsEntry("ruleName", "HighValue")
                    .containsEntry("ruleDescription", "Order > 1000")
                    .containsKey("payload");
        }

        @Test
        @DisplayName("addRule() with null payload omits the 'payload' key")
        void addRule_nullPayload_omitsPayloadKey() {
            Buffer b = new Buffer(10, 1_000);

            b.addRule("ev", "rule", "desc", null);

            List<Record> out = new ArrayList<>();
            b.drainTo(out, 1);
            @SuppressWarnings("unchecked")
            Map<String, Object> ruleMap = (Map<String, Object>) out.get(0).getPayload();
            assertThat(ruleMap).doesNotContainKey("payload");
        }

        @Test
        @DisplayName("Multiple records of different types can be enqueued together")
        void multipleTypes_enqueuedInOrder() {
            Buffer b = new Buffer(10, 1_000);

            b.addEventCapture(new EventCapture());
            b.addVocabulary(List.of(new ClassModel()));
            b.addRule("ev", "r", "d", null);

            assertThat(b.size()).isEqualTo(3);

            List<Record> out = new ArrayList<>();
            b.drainTo(out, 10);
            assertThat(out)
                    .extracting(Record::getType)
                    .containsExactly(RecordType.EVENT_CAPTURE, RecordType.VOCABULARY, RecordType.RULE);
        }
    }

    // =========================================================================
    // 3. drainTo()
    // =========================================================================

    @Nested
    @DisplayName("3. drainTo()")
    class DrainTo {

        @Test
        @DisplayName("drainTo() with max > queue size drains all available records")
        void drainTo_drainsFull() {
            Buffer b = new Buffer(10, 1_000);
            b.addEventCapture(new EventCapture());
            b.addEventCapture(new EventCapture());
            b.addEventCapture(new EventCapture());

            List<Record> out = new ArrayList<>();
            int drained = b.drainTo(out, 100);

            assertThat(drained).isEqualTo(3);
            assertThat(out).hasSize(3);
            assertThat(b.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("drainTo() respects max and leaves remaining records in queue")
        void drainTo_respectsMax() {
            Buffer b = new Buffer(10, 1_000);
            for (int i = 0; i < 5; i++) b.addEventCapture(new EventCapture());

            List<Record> out = new ArrayList<>();
            int drained = b.drainTo(out, 3);

            assertThat(drained).isEqualTo(3);
            assertThat(b.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("drainTo() on empty buffer returns 0 and leaves list untouched")
        void drainTo_emptyBuffer_returnsZero() {
            Buffer b = new Buffer(10, 1_000);
            List<Record> out = new ArrayList<>();

            int drained = b.drainTo(out, 10);

            assertThat(drained).isZero();
            assertThat(out).isEmpty();
        }

        @Test
        @DisplayName("drainTo() with max=0 uses batchSize as the ceiling")
        void drainTo_zeroMax_usesBatchSize() {
            Buffer b = new Buffer(3, 1_000); // batchSize = 3
            for (int i = 0; i < 5; i++) b.addEventCapture(new EventCapture());

            List<Record> out = new ArrayList<>();
            int drained = b.drainTo(out, 0); // 0 → falls back to batchSize (3)

            assertThat(drained).isEqualTo(3);
            assertThat(b.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("Drained records preserve their type and payload identity")
        void drainTo_preservesPayloadIdentity() {
            Buffer b = new Buffer(10, 1_000);
            EventCapture dto = new EventCapture();
            dto.setCorrelationId("uid-42");
            b.addEventCapture(dto);

            List<Record> out = new ArrayList<>();
            b.drainTo(out, 1);

            EventCapture recovered = (EventCapture) out.get(0).getPayload();
            assertThat(recovered.getCorrelationId()).isEqualTo("uid-42");
        }
    }

    // =========================================================================
    // 4. take()
    // =========================================================================

    @Nested
    @DisplayName("4. take()")
    class TakeTests {

        @Test
        @DisplayName("take() returns the head record immediately when queue is non-empty")
        void take_returnsHead() throws InterruptedException {
            Buffer b = new Buffer(10, 1_000);
            EventCapture dto = new EventCapture();
            dto.setCorrelationId("take-test");
            b.addEventCapture(dto);

            Record record = b.take();

            assertThat(record.getType()).isEqualTo(RecordType.EVENT_CAPTURE);
            assertThat(((EventCapture) record.getPayload()).getCorrelationId()).isEqualTo("take-test");
        }

        @Test
        @DisplayName("take() blocks until a record is available, then returns it")
        void take_blocksUntilRecordAvailable() throws InterruptedException {
            Buffer b = new Buffer(10, 1_000);
            EventCapture dto = new EventCapture();

            // Schedule a producer to enqueue after a short delay
            Thread producer = new Thread(() -> {
                try {
                    Thread.sleep(80);
                    b.addEventCapture(dto);
                } catch (InterruptedException ignored) {}
            });
            producer.start();

            long start = System.currentTimeMillis();
            Record record = b.take(); // should block for ~80 ms
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isGreaterThanOrEqualTo(50L);
            assertThat(record.getType()).isEqualTo(RecordType.EVENT_CAPTURE);
            producer.join(500);
        }

        @Test
        @DisplayName("take() preserves FIFO order across multiple records")
        void take_preservesFifoOrder() throws InterruptedException {
            Buffer b = new Buffer(10, 1_000);
            b.addRule("e1", "r1", "d1", null);
            b.addRule("e2", "r2", "d2", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) b.take().getPayload();
            @SuppressWarnings("unchecked")
            Map<String, Object> second = (Map<String, Object>) b.take().getPayload();

            assertThat(first.get("eventName")).isEqualTo("e1");
            assertThat(second.get("eventName")).isEqualTo("e2");
        }
    }

    // =========================================================================
    // 5. size() / isEmpty()
    // =========================================================================

    @Nested
    @DisplayName("5. size() and isEmpty()")
    class SizeAndEmpty {

        @Test
        @DisplayName("size() increments with each enqueue")
        void size_incrementsOnAdd() {
            Buffer b = new Buffer(10, 1_000);
            assertThat(b.size()).isZero();
            b.addEventCapture(new EventCapture());
            assertThat(b.size()).isEqualTo(1);
            b.addEventCapture(new EventCapture());
            assertThat(b.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("size() decrements after drainTo()")
        void size_decrementsAfterDrain() {
            Buffer b = new Buffer(10, 1_000);
            b.addEventCapture(new EventCapture());
            b.addEventCapture(new EventCapture());
            List<Record> out = new ArrayList<>();
            b.drainTo(out, 1);
            assertThat(b.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("isEmpty() returns true only when queue has no records")
        void isEmpty_trueOnlyWhenEmpty() {
            Buffer b = new Buffer(10, 1_000);
            assertThat(b.isEmpty()).isTrue();
            b.addEventCapture(new EventCapture());
            assertThat(b.isEmpty()).isFalse();
            List<Record> out = new ArrayList<>();
            b.drainTo(out, 10);
            assertThat(b.isEmpty()).isTrue();
        }
    }

    // =========================================================================
    // 6. Flush predicates
    // =========================================================================

    @Nested
    @DisplayName("6. Flush predicates")
    class FlushPredicates {

        @Test
        @DisplayName("shouldFlushBySize() returns false when queue has fewer records than batchSize")
        void shouldFlushBySize_falseWhenBelowBatch() {
            Buffer b = new Buffer(5, 1_000);
            b.addEventCapture(new EventCapture());
            assertThat(b.shouldFlushBySize()).isFalse();
        }

        @Test
        @DisplayName("shouldFlushBySize() returns true when queue size reaches batchSize")
        void shouldFlushBySize_trueWhenAtBatch() {
            Buffer b = new Buffer(3, 1_000);
            b.addEventCapture(new EventCapture());
            b.addEventCapture(new EventCapture());
            b.addEventCapture(new EventCapture()); // size == batchSize
            assertThat(b.shouldFlushBySize()).isTrue();
        }

        @Test
        @DisplayName("shouldFlushBySize() returns true when queue size exceeds batchSize")
        void shouldFlushBySize_trueWhenAboveBatch() {
            Buffer b = new Buffer(2, 1_000);
            b.addEventCapture(new EventCapture());
            b.addEventCapture(new EventCapture());
            b.addEventCapture(new EventCapture()); // size > batchSize
            assertThat(b.shouldFlushBySize()).isTrue();
        }

        @Test
        @DisplayName("shouldFlushByTimeout() returns false immediately after construction")
        void shouldFlushByTimeout_falseJustAfterConstruction() {
            // Use a very large timeout so it can't expire within the test
            Buffer b = new Buffer(10, 60_000);
            assertThat(b.shouldFlushByTimeout()).isFalse();
        }

        @Test
        @DisplayName("shouldFlushByTimeout() returns true after the timeout has elapsed")
        void shouldFlushByTimeout_trueAfterTimeoutElapsed() throws InterruptedException {
            Buffer b = new Buffer(10, 50); // 50 ms timeout
            Thread.sleep(80);              // wait beyond the timeout
            assertThat(b.shouldFlushByTimeout()).isTrue();
        }

        @Test
        @DisplayName("markFlushed() resets the timeout window — subsequent check returns false")
        void markFlushed_resetsTimeout() throws InterruptedException {
            Buffer b = new Buffer(10, 50);
            Thread.sleep(80);              // let timeout expire
            assertThat(b.shouldFlushByTimeout()).isTrue();

            b.markFlushed();               // reset
            assertThat(b.shouldFlushByTimeout()).isFalse();
        }
    }

    // =========================================================================
    // 7. Record inner class
    // =========================================================================

    @Nested
    @DisplayName("7. Record inner class")
    class RecordTests {

        @Test
        @DisplayName("Record.getType() returns the type passed at construction")
        void record_getType() {
            Record r = new Record(RecordType.RULE, "payload");
            assertThat(r.getType()).isEqualTo(RecordType.RULE);
        }

        @Test
        @DisplayName("Record.getPayload() returns the payload passed at construction")
        void record_getPayload() {
            Object payload = new EventCapture();
            Record r = new Record(RecordType.EVENT_CAPTURE, payload);
            assertThat(r.getPayload()).isSameAs(payload);
        }

        @Test
        @DisplayName("Record accepts null payload without throwing")
        void record_nullPayload_doesNotThrow() {
            assertThatCode(() -> new Record(RecordType.VOCABULARY, null))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // 8. Concurrency
    // =========================================================================

    @Nested
    @DisplayName("8. Concurrency")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent producers do not drop records")
        void concurrentProducers_noRecordsLost() throws InterruptedException {
            int threads  = 8;
            int perThread = 50;
            Buffer b = new Buffer(500, 60_000);

            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done  = new CountDownLatch(threads);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            b.addEventCapture(new EventCapture());
                        }
                    } catch (InterruptedException ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown(); // release all producers at once
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(b.size()).isEqualTo(threads * perThread);
            pool.shutdown();
        }

        @Test
        @DisplayName("Concurrent producer + consumer: all records consumed exactly once")
        void concurrentProducerAndConsumer_exactlyOnceDelivery() throws InterruptedException {
            int totalRecords = 100;
            Buffer b = new Buffer(200, 60_000);
            AtomicInteger consumed = new AtomicInteger(0);

            // Producer thread
            Thread producer = new Thread(() -> {
                for (int i = 0; i < totalRecords; i++) {
                    b.addEventCapture(new EventCapture());
                }
            });

            // Consumer thread — drains in batches
            Thread consumer = new Thread(() -> {
                List<Record> batch = new ArrayList<>();
                while (consumed.get() < totalRecords) {
                    batch.clear();
                    int drained = b.drainTo(batch, 10);
                    consumed.addAndGet(drained);
                    if (drained == 0) Thread.yield();
                }
            });

            producer.start();
            consumer.start();
            producer.join(3_000);
            consumer.join(3_000);

            assertThat(consumed.get()).isEqualTo(totalRecords);
            assertThat(b.isEmpty()).isTrue();
        }
    }

    // =========================================================================
    // 9. Null-safety guards
    // =========================================================================

    @Nested
    @DisplayName("9. Null-safety guards")
    class NullSafety {

        // --- add() ---

        @Test
        @DisplayName("add() with null RecordType throws NullPointerException")
        void add_nullType_throws() {
            Buffer b = new Buffer(10, 1_000);
            assertThatNullPointerException()
                    .isThrownBy(() -> b.add(null, "payload"))
                    .withMessageContaining("RecordType");
        }

        @Test
        @DisplayName("add() with null payload is still enqueued (payload is optional)")
        void add_nullPayload_isEnqueued() {
            Buffer b = new Buffer(10, 1_000);
            b.add(RecordType.EVENT_CAPTURE, null);
            assertThat(b.size()).isEqualTo(1);
        }

        // --- addEventCapture() ---

        @Test
        @DisplayName("addEventCapture(null) is a no-op — queue stays empty")
        void addEventCapture_null_isNoOp() {
            Buffer b = new Buffer(10, 1_000);
            b.addEventCapture(null);
            assertThat(b.isEmpty()).isTrue();
        }

        // --- addVocabulary() ---

        @Test
        @DisplayName("addVocabulary(null) is a no-op — queue stays empty")
        void addVocabulary_null_isNoOp() {
            Buffer b = new Buffer(10, 1_000);
            b.addVocabulary(null);
            assertThat(b.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("addVocabulary(emptyList) is a no-op — queue stays empty")
        void addVocabulary_emptyList_isNoOp() {
            Buffer b = new Buffer(10, 1_000);
            b.addVocabulary(List.of());
            assertThat(b.isEmpty()).isTrue();
        }

        // --- addRule() ---

        @Test
        @DisplayName("addRule with both null eventName and null ruleName is a no-op")
        void addRule_bothNullNames_isNoOp() {
            Buffer b = new Buffer(10, 1_000);
            b.addRule(null, null, "desc", null);
            assertThat(b.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("addRule with both blank eventName and blank ruleName is a no-op")
        void addRule_bothBlankNames_isNoOp() {
            Buffer b = new Buffer(10, 1_000);
            b.addRule("   ", "  ", "desc", null);
            assertThat(b.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("addRule with valid ruleName but null eventName is enqueued")
        void addRule_nullEventName_isEnqueued() {
            Buffer b = new Buffer(10, 1_000);
            b.addRule(null, "MyRule", "desc", null);
            assertThat(b.size()).isEqualTo(1);

            List<Record> out = new ArrayList<>();
            b.drainTo(out, 1);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) out.get(0).getPayload();
            assertThat(map.get("eventName")).isEqualTo("");    // normalised to empty
            assertThat(map.get("ruleName")).isEqualTo("MyRule");
        }

        @Test
        @DisplayName("addRule with valid eventName but null ruleName is enqueued")
        void addRule_nullRuleName_isEnqueued() {
            Buffer b = new Buffer(10, 1_000);
            b.addRule("OrderPlaced", null, "desc", null);
            assertThat(b.size()).isEqualTo(1);

            List<Record> out = new ArrayList<>();
            b.drainTo(out, 1);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) out.get(0).getPayload();
            assertThat(map.get("eventName")).isEqualTo("OrderPlaced");
            assertThat(map.get("ruleName")).isEqualTo("");     // normalised to empty
        }

        @Test
        @DisplayName("addRule with null ruleDescription normalises it to empty string")
        void addRule_nullDescription_normalisedToEmpty() {
            Buffer b = new Buffer(10, 1_000);
            b.addRule("ev", "rule", null, null);

            List<Record> out = new ArrayList<>();
            b.drainTo(out, 1);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) out.get(0).getPayload();
            assertThat(map.get("ruleDescription")).isEqualTo("");
        }

        // --- drainTo() ---

        @Test
        @DisplayName("drainTo(null, n) returns 0 and does not remove anything from the queue")
        void drainTo_nullList_returnsZeroAndLeavesQueueIntact() {
            Buffer b = new Buffer(10, 1_000);
            b.addEventCapture(new EventCapture());
            b.addEventCapture(new EventCapture());

            int drained = b.drainTo(null, 10);

            assertThat(drained).isZero();
            assertThat(b.size()).isEqualTo(2);
        }

        // --- Record constructor ---

        @Test
        @DisplayName("Record constructor with null type throws NullPointerException")
        void record_nullType_throws() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Record(null, "payload"))
                    .withMessageContaining("RecordType");
        }
    }
}
