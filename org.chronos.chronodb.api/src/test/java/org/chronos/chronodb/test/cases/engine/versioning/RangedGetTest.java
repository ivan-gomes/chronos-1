package org.chronos.chronodb.test.cases.engine.versioning;

import org.chronos.chronodb.api.ChronoDB;
import org.chronos.chronodb.api.ChronoDBTransaction;
import org.chronos.chronodb.api.key.QualifiedKey;
import org.chronos.chronodb.internal.api.GetResult;
import org.chronos.chronodb.internal.api.Period;
import org.chronos.chronodb.internal.api.TemporalKeyValueStore;
import org.chronos.chronodb.test.base.AllChronoDBBackendsTest;
import org.chronos.common.test.junit.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

@Category(IntegrationTest.class)
public class RangedGetTest extends AllChronoDBBackendsTest {

    @Test
    public void rangedGetBehavesCorrectlyInMainCase() {
        ChronoDB db = this.getChronoDB();
        ChronoDBTransaction tx = db.tx();
        tx.put("Hello", "World");
        tx.put("Name", "Martin");
        tx.put("Number", 123);
        tx.commit();

        long afterFirstCommit = tx.getTimestamp();
        this.sleep(10);

        long betweenCommits = System.currentTimeMillis();
        this.sleep(10);

        tx.put("Hello", "Foo");
        tx.put("Name", "John");
        tx.put("Number", 456);
        tx.commit();

        long afterSecondCommit = tx.getTimestamp();
        this.sleep(10);

        tx.put("Hello", "Bar");
        tx.put("Name", "Jack");
        tx.put("Number", 789);
        tx.commit();

        TemporalKeyValueStore tkvs = this.getMasterTkvs(db);

        ChronoDBTransaction tx2 = db.tx(betweenCommits);

        // test the ranges in the cases where there is a lower bound and an upper bound

        GetResult<Object> result1 = tkvs.performRangedGet(tx2, QualifiedKey.createInDefaultKeyspace("Hello"));
        assertNotNull(result1);
        assertEquals("World", result1.getValue());
        assertEquals(afterFirstCommit, result1.getPeriod().getLowerBound());
        assertEquals(afterSecondCommit, result1.getPeriod().getUpperBound());

        GetResult<Object> result2 = tkvs.performRangedGet(tx2, QualifiedKey.createInDefaultKeyspace("Name"));
        assertNotNull(result2);
        assertEquals("Martin", result2.getValue());
        assertEquals(afterFirstCommit, result2.getPeriod().getLowerBound());
        assertEquals(afterSecondCommit, result2.getPeriod().getUpperBound());

        GetResult<Object> result3 = tkvs.performRangedGet(tx2, QualifiedKey.createInDefaultKeyspace("Number"));
        assertNotNull(result3);
        assertEquals(123, result3.getValue());
        assertEquals(afterFirstCommit, result3.getPeriod().getLowerBound());
        assertEquals(afterSecondCommit, result3.getPeriod().getUpperBound());
    }

    @Test
    public void rangedGetBehavesCorrectlyInTemporalCornerCases() {
        ChronoDB db = this.getChronoDB();
        ChronoDBTransaction tx = db.tx();
        tx.put("Hello", "World");
        tx.put("Name", "Martin");
        tx.put("Number", 123);
        tx.commit();

        long afterFirstCommit = tx.getTimestamp();
        this.sleep(10);

        tx.put("Hello", "Foo");
        tx.put("Name", "John");
        tx.put("Number", 456);
        tx.commit();

        this.sleep(10);

        tx.put("Hello", "Bar");
        tx.put("Name", "Jack");
        tx.put("Number", 789);
        tx.commit();
        long afterThirdCommit = tx.getTimestamp();

        TemporalKeyValueStore tkvs = this.getMasterTkvs(db);

        // test the ranges BEFORE the first commit
        ChronoDBTransaction tx2 = db.tx(0);

        GetResult<Object> result1 = tkvs.performRangedGet(tx2, QualifiedKey.createInDefaultKeyspace("Hello"));
        assertNotNull(result1);
        assertNull(result1.getValue());
        assertEquals(0, result1.getPeriod().getLowerBound());
        assertEquals(afterFirstCommit, result1.getPeriod().getUpperBound());

        GetResult<Object> result2 = tkvs.performRangedGet(tx2, QualifiedKey.createInDefaultKeyspace("Name"));
        assertNotNull(result2);
        assertNull(result2.getValue());
        assertEquals(0, result2.getPeriod().getLowerBound());
        assertEquals(afterFirstCommit, result2.getPeriod().getUpperBound());

        GetResult<Object> result3 = tkvs.performRangedGet(tx2, QualifiedKey.createInDefaultKeyspace("Number"));
        assertNotNull(result3);
        assertNull(result3.getValue());
        assertEquals(0, result3.getPeriod().getLowerBound());
        assertEquals(afterFirstCommit, result3.getPeriod().getUpperBound());

        // test the ranges AFTER the third commit
        ChronoDBTransaction tx3 = db.tx(afterThirdCommit);

        GetResult<Object> result4 = tkvs.performRangedGet(tx3, QualifiedKey.createInDefaultKeyspace("Hello"));
        assertNotNull(result4);
        assertEquals("Bar", result4.getValue());
        assertEquals(afterThirdCommit, result4.getPeriod().getLowerBound());
        assertEquals(Long.MAX_VALUE, result4.getPeriod().getUpperBound());

        GetResult<Object> result5 = tkvs.performRangedGet(tx3, QualifiedKey.createInDefaultKeyspace("Name"));
        assertNotNull(result5);
        assertEquals("Jack", result5.getValue());
        assertEquals(afterThirdCommit, result5.getPeriod().getLowerBound());
        assertEquals(Long.MAX_VALUE, result5.getPeriod().getUpperBound());

        GetResult<Object> result6 = tkvs.performRangedGet(tx3, QualifiedKey.createInDefaultKeyspace("Number"));
        assertNotNull(result6);
        assertEquals(789, result6.getValue());
        assertEquals(afterThirdCommit, result6.getPeriod().getLowerBound());
        assertEquals(Long.MAX_VALUE, result6.getPeriod().getUpperBound());
    }

    @Test
    public void rangedGetBehavesCorrectlyOnNonExistingKeys() {
        ChronoDB db = this.getChronoDB();
        ChronoDBTransaction tx = db.tx();
        tx.put("Hello", "World"); // make sure that the keyspace exists
        tx.commit();

        TemporalKeyValueStore tkvs = this.getMasterTkvs(db);

        GetResult<Object> result = tkvs.performRangedGet(tx, QualifiedKey.createInDefaultKeyspace("Fake"));
        assertNotNull(result);
        assertNull(result.getValue());
        assertEquals(Period.eternal(), result.getPeriod());
    }

    @Test
    public void rangedGetBehavesCorrectlyOnNonExistingKeyspaces() {
        ChronoDB db = this.getChronoDB();
        ChronoDBTransaction tx = db.tx();

        TemporalKeyValueStore tkvs = this.getMasterTkvs(db);

        GetResult<Object> result = tkvs.performRangedGet(tx, QualifiedKey.createInDefaultKeyspace("Fake"));
        assertNotNull(result);
        assertNull(result.getValue());
        assertEquals(Period.eternal(), result.getPeriod());
    }

    @Test
    public void rangedGetBehavesCorrectlyInCombinationWithDeletion() {
        ChronoDB db = this.getChronoDB();
        ChronoDBTransaction tx = db.tx();
        tx.put("Hello", "World");
        tx.put("Name", "Martin");
        tx.put("Number", 123);
        tx.commit();

        long afterFirstCommit = tx.getTimestamp();
        this.sleep(10);

        long betweenCommits = System.currentTimeMillis();
        this.sleep(10);

        tx.remove("Hello");
        tx.remove("Name");
        tx.put("Number", 456);
        tx.commit();

        long afterSecondCommit = tx.getTimestamp();

        TemporalKeyValueStore tkvs = this.getMasterTkvs(db);

        // test the ranges in the cases where there is a lower bound and an upper bound
        ChronoDBTransaction tx2 = db.tx(betweenCommits);

        GetResult<Object> result1 = tkvs.performRangedGet(tx2, QualifiedKey.createInDefaultKeyspace("Hello"));
        assertNotNull(result1);
        assertEquals("World", result1.getValue());
        assertEquals(afterFirstCommit, result1.getPeriod().getLowerBound());
        assertEquals(afterSecondCommit, result1.getPeriod().getUpperBound());

        GetResult<Object> result2 = tkvs.performRangedGet(tx2, QualifiedKey.createInDefaultKeyspace("Name"));
        assertNotNull(result2);
        assertEquals("Martin", result2.getValue());
        assertEquals(afterFirstCommit, result2.getPeriod().getLowerBound());
        assertEquals(afterSecondCommit, result2.getPeriod().getUpperBound());

        GetResult<Object> result3 = tkvs.performRangedGet(tx2, QualifiedKey.createInDefaultKeyspace("Number"));
        assertNotNull(result3);
        assertEquals(123, result3.getValue());
        assertEquals(afterFirstCommit, result3.getPeriod().getLowerBound());
        assertEquals(afterSecondCommit, result3.getPeriod().getUpperBound());

        // test the ranges in the cases where there is no upper bound
        ChronoDBTransaction tx3 = db.tx(afterSecondCommit);

        GetResult<Object> result4 = tkvs.performRangedGet(tx3, QualifiedKey.createInDefaultKeyspace("Hello"));
        assertNotNull(result4);
        assertNull(result4.getValue());
        assertEquals(afterSecondCommit, result4.getPeriod().getLowerBound());
        assertEquals(Long.MAX_VALUE, result4.getPeriod().getUpperBound());

        GetResult<Object> result5 = tkvs.performRangedGet(tx3, QualifiedKey.createInDefaultKeyspace("Name"));
        assertNotNull(result5);
        assertNull(result5.getValue());
        assertEquals(afterSecondCommit, result5.getPeriod().getLowerBound());
        assertEquals(Long.MAX_VALUE, result5.getPeriod().getUpperBound());

        GetResult<Object> result6 = tkvs.performRangedGet(tx3, QualifiedKey.createInDefaultKeyspace("Number"));
        assertNotNull(result6);
        assertEquals(456, result6.getValue());
        assertEquals(afterSecondCommit, result6.getPeriod().getLowerBound());
        assertEquals(Long.MAX_VALUE, result6.getPeriod().getUpperBound());

    }
}
