package org.chronos.chronograph.test.cases.transaction;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.chronos.chronodb.inmemory.InMemoryChronoDB;
import org.chronos.chronodb.internal.api.ChronoDBConfiguration;
import org.chronos.chronodb.test.base.AllBackendsTest.DontRunWithBackend;
import org.chronos.chronodb.test.base.InstantiateChronosWith;
import org.chronos.chronograph.api.structure.ChronoGraph;
import org.chronos.chronograph.test.base.AllChronoGraphBackendsTest;
import org.chronos.common.test.junit.categories.PerformanceTest;
import org.chronos.common.test.utils.TestUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

@Category(PerformanceTest.class)
@DontRunWithBackend({InMemoryChronoDB.BACKEND_NAME})
public class IncrementalGraphCommitPerformanceTest extends AllChronoGraphBackendsTest {

    // copied from TuplUtils to avoid the project dependency
    private static final int BATCH_INSERT_THRESHOLD = 25_000;

    @Test
    public void massiveIncrementalCommitsProduceConsistentStoreWithBatchInsert() {
        this.runMassiveIncrementalCommitTest(true);
    }

    @Test
    @InstantiateChronosWith(property = ChronoDBConfiguration.CACHING_ENABLED, value = "true")
    @InstantiateChronosWith(property = ChronoDBConfiguration.CACHE_MAX_SIZE, value = "10000")
    public void massiveIncrementalCommitsProduceConsistentStoreWithBatchInsertAndCache() {
        this.runMassiveIncrementalCommitTest(true);
    }

    @Test
    public void massiveIncrementalCommitsProduceConsistentStoreWithRegularInsert() {
        this.runMassiveIncrementalCommitTest(false);
    }

    @Test
    @InstantiateChronosWith(property = ChronoDBConfiguration.CACHING_ENABLED, value = "true")
    @InstantiateChronosWith(property = ChronoDBConfiguration.CACHE_MAX_SIZE, value = "10000")
    public void massiveIncrementalCommitsProduceConsistentStoreWithRegularInsertAndCache() {
        this.runMassiveIncrementalCommitTest(false);
    }

    private void runMassiveIncrementalCommitTest(final boolean useBatch) {
        ChronoGraph graph = this.getGraph();

        // we want at least three batches
        final int keyCount = BATCH_INSERT_THRESHOLD * 4;

        final int additionalEdgeCount = keyCount * 3;

        List<String> keysList = Lists.newArrayList();
        for (int i = 0; i < keyCount; i++) {
            keysList.add(UUID.randomUUID().toString());
        }
        keysList = Collections.unmodifiableList(keysList);

        final int maxBatchSize;
        if (useBatch) {
            // we force batch inserts by choosing a size larger than the batch threshold
            maxBatchSize = BATCH_INSERT_THRESHOLD + 1;
        } else {
            // we force normal inserts by choosing a size less than the batch threshold
            maxBatchSize = BATCH_INSERT_THRESHOLD - 1;
        }

        graph.tx().open();

        int index = 0;
        int batchSize = 0;
        int batchCount = 0;
        List<String> addedVertexIds = Lists.newArrayList();
        while (index < keyCount) {
            String uuid = keysList.get(index);
            Vertex newVertex = graph.addVertex(T.id, uuid);
            if (addedVertexIds.size() > 1) {
                // connect to a random vertex (other than self; we add this vertex later)
                String randomVertexId = TestUtils.getRandomEntryOf(addedVertexIds);
                Vertex randomVertex = Iterators.getOnlyElement(graph.vertices(randomVertexId));
                newVertex.addEdge("connected", randomVertex);
            }
            addedVertexIds.add(uuid);
            index++;
            batchSize++;
            if (batchSize >= maxBatchSize) {
                for (int i = 0; i < index; i++) {
                    String test = keysList.get(i);
                    try {
                        Vertex vertex = Iterators.getOnlyElement(graph.vertices(test));
                        assertNotNull(vertex);
                        assertEquals(test, vertex.id());
                        if (i >= 2) {
                            assertEquals(1, Iterators.size(vertex.edges(Direction.OUT)));
                        }
                    } catch (AssertionError e) {
                        System.out.println("Error occurred on Test\t\tBatch: " + batchCount + "\t\ti: " + i
                            + "\t\tmaxIndex: " + index);
                        throw e;
                    }
                }
                graph.tx().commitIncremental();
                batchSize = 0;
                batchCount++;
                for (int i = 0; i < index; i++) {
                    String test = keysList.get(i);
                    try {
                        Vertex vertex = Iterators.getOnlyElement(graph.vertices(test));
                        assertNotNull(vertex);
                        assertEquals(test, vertex.id());
                        if (i >= 2) {
                            assertEquals(1, Iterators.size(vertex.edges(Direction.OUT)));
                        }
                    } catch (AssertionError e) {
                        System.out.println("Error occurred on Test\t\tBatch: " + batchCount + "\t\ti: " + i
                            + "\t\tmaxIndex: " + index);
                        throw e;
                    }
                }
            }
        }
        // now, do some linking
        batchSize = 0;
        batchCount = 0;
        for (int i = 0; i < additionalEdgeCount; i++) {
            String vId1 = TestUtils.getRandomEntryOf(keysList);
            String vId2 = TestUtils.getRandomEntryOf(keysList);
            Vertex v1 = Iterators.getOnlyElement(graph.vertices(vId1));
            Vertex v2 = Iterators.getOnlyElement(graph.vertices(vId2));
            v1.addEdge("additional", v2);
            batchSize++;
            if (batchSize >= maxBatchSize) {
                batchSize = 0;
                batchCount++;
                graph.tx().commitIncremental();
            }
        }

        graph.tx().commit();
        graph.tx().open();
        // check that all elements are present in the old transaction
        int i = 0;
        for (String uuid : keysList) {
            Vertex vertex = Iterators.getOnlyElement(graph.vertices(uuid));
            assertNotNull(vertex);
            assertEquals(uuid, vertex.id());
            if (i >= 2) {
                assertEquals(1, Iterators.size(vertex.edges(Direction.OUT, "connected")));
            }
            i++;
        }
        // check that all edges are there
        assertEquals(additionalEdgeCount,
            Iterators.size(graph.traversal().E().filter(t -> t.get().label().equals("additional"))));
        graph.tx().rollback();
    }

}
