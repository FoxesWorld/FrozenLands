package org.takesome.frozenlands.engine.operations;

import org.junit.jupiter.api.Test;
import org.takesome.frozenlands.engine.events.EngineEventTopics;
import org.takesome.frozenlands.engine.runtime.GameRuntimeStartupController;
import org.takesome.frozenlands.engine.testing.TestEngineContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EngineOperationCoordinatorTest {
    @Test
    void exactlyOneConcurrentRequestAcquiresTheOperationLease() throws Exception {
        TestEngineContext context = new TestEngineContext();
        EngineOperationCoordinator coordinator = new EngineOperationCoordinator(context);
        List<String> topics = Collections.synchronizedList(new ArrayList<>());
        context.modules().getEventBus().subscribe(
                "engine.operation.*",
                event -> topics.add(String.valueOf(event.get("topic"))),
                false
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<EngineOperationCoordinator.BeginResult> first = executor.submit(() -> {
                ready.countDown();
                release.await();
                return coordinator.begin("world.generate", Map.of("seed", 11), "test-a");
            });
            Future<EngineOperationCoordinator.BeginResult> second = executor.submit(() -> {
                ready.countDown();
                release.await();
                return coordinator.begin("world.generate", Map.of("seed", 12), "test-b");
            });

            ready.await();
            release.countDown();
            List<EngineOperationCoordinator.BeginResult> results = List.of(first.get(), second.get());

            assertEquals(1, results.stream().filter(EngineOperationCoordinator.BeginResult::accepted).count());
            assertEquals(1, results.stream().filter(EngineOperationCoordinator.BeginResult::duplicate).count());

            EngineOperationCoordinator.BeginResult accepted = results.stream()
                    .filter(EngineOperationCoordinator.BeginResult::accepted)
                    .findFirst()
                    .orElseThrow();
            EngineOperationCoordinator.BeginResult duplicate = results.stream()
                    .filter(EngineOperationCoordinator.BeginResult::duplicate)
                    .findFirst()
                    .orElseThrow();

            assertNotEquals(accepted.requestId(), duplicate.requestId());
            assertEquals(accepted.operation().operationId(), duplicate.operation().operationId());
            assertEquals(duplicate.requestId(), duplicate.toMap().get("requestId"));
            assertEquals(accepted.operation().requestId(), duplicate.toMap().get("operationRequestId"));
            assertTrue(coordinator.isActive("world.generate"));

            assertTrue(coordinator.progress(
                    accepted.operation().operationId(),
                    "Generating chunks",
                    0.5f,
                    Map.of("chunks", 4)
            ));
            assertTrue(coordinator.complete(
                    accepted.operation().operationId(),
                    Map.of("chunks", 8)
            ));
            assertFalse(coordinator.isActive("world.generate"));
        } finally {
            executor.shutdownNow();
        }

        assertTrue(topics.contains(EngineEventTopics.ENGINE_OPERATION_REQUESTED));
        assertTrue(topics.contains(EngineEventTopics.ENGINE_OPERATION_STARTED));
        assertTrue(topics.contains(EngineEventTopics.ENGINE_OPERATION_REJECTED));
        assertTrue(topics.contains(EngineEventTopics.ENGINE_OPERATION_PROGRESS));
        assertTrue(topics.contains(EngineEventTopics.ENGINE_OPERATION_COMPLETED));
    }

    @Test
    void gameStartRemainsLockedUntilPlayerSpawned() {
        TestEngineContext context = new TestEngineContext();
        EngineOperationCoordinator coordinator = new EngineOperationCoordinator(context);
        GameRuntimeStartupController controller = new GameRuntimeStartupController(context, coordinator);

        Map<String, Object> first = controller.start(Map.of(
                "source", "test-ui",
                "operation", "newGame"
        ));
        assertEquals(true, first.get("accepted"));
        assertTrue(coordinator.isActive(GameRuntimeStartupController.START_OPERATION));

        Map<String, Object> duplicate = controller.start(Map.of(
                "source", "test-ui",
                "operation", "newGame"
        ));
        assertEquals(false, duplicate.get("accepted"));
        assertEquals(true, duplicate.get("duplicate"));
        assertEquals(true, duplicate.get("busy"));

        controller.update();
        assertEquals(true, controller.status().get("runtimeReady"));
        assertTrue(coordinator.isActive(GameRuntimeStartupController.START_OPERATION));

        Map<String, Object> whileWaitingForPlayer = controller.start(Map.of("source", "direct-call"));
        assertEquals(false, whileWaitingForPlayer.get("accepted"));
        assertEquals(true, whileWaitingForPlayer.get("duplicate"));

        context.modules().publishEvent(EngineEventTopics.PLAYER_SPAWNED, Map.of(
                "playerRef", 7,
                "x", 0f,
                "y", 12f,
                "z", 0f
        ));
        assertFalse(coordinator.isActive(GameRuntimeStartupController.START_OPERATION));

        Map<String, Object> idempotent = controller.start(Map.of("source", "direct-call"));
        assertEquals(true, idempotent.get("alreadyStarted"));
        assertEquals(false, idempotent.get("duplicate"));
        controller.close();
    }
}
