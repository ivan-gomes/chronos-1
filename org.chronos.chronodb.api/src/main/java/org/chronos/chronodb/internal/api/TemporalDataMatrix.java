package org.chronos.chronodb.internal.api;

import org.chronos.chronodb.api.Order;
import org.chronos.chronodb.api.key.TemporalKey;
import org.chronos.chronodb.internal.api.stream.CloseableIterator;
import org.chronos.chronodb.internal.impl.temporal.UnqualifiedTemporalEntry;
import org.chronos.chronodb.internal.impl.temporal.UnqualifiedTemporalKey;
import org.chronos.chronodb.internal.util.KeySetModifications;

import java.util.*;

/**
 * A {@link TemporalDataMatrix} is a structured container for temporal key-value pairs.
 *
 * <p>
 * Such a matrix is defined over a set of timestamps <i>T</i>, in combination with a set of keys <i>K</i>. A temporal
 * data matrix <i>D</i> is then defined as <i>D = T x K</i>. The matrix can be visualized as follows:
 *
 * <pre>
 *    K
 *
 *    ^
 *    |
 *    |
 *    +----+----+----+----+----+
 *  c |    | V2 |    |    |    |
 *    +----+----+----+----+----+
 *  b |    |    | V4 |    |  X |
 *    +----+----+----+----+----+
 *  a | V1 |    | V3 |    |    |
 *    +----+----+----+----+----+---> T
 *       1    2    3    4    5
 * </pre>
 * <p>
 * In the example above, the following inserts have been applied:
 * <ul>
 * <li>a->V1 at T = 1
 * <li>c->v2 at T = 2
 * <li>a->v3, b-> v4 at T = 3
 * <li>b->deleted at T = 5
 * </ul>
 * <p>
 * Each entry in such a matrix D has a certain validity range on the T axis. For example, the entry "a->V1" is valid in
 * range [1;3[, while "b->V4" is valid in [3;5[. In general, each entry is valid from (including) the timestamp at which
 * it was inserted, up to the timestamp where the next insert occurs (exclusive).
 *
 * @author martin.haeusler@uibk.ac.at -- Initial Contribution and API
 */
public interface TemporalDataMatrix {

    // =================================================================================================================
    // METADATA
    // =================================================================================================================

    /**
     * Returns the name of the keyspace that is represented by this matrix.
     *
     * @return The keyspace. Never <code>null</code>.
     */
    public String getKeyspace();

    /**
     * Returns the timestamp at which this matrix was created.
     *
     * @return The creation timestamp. Never negative.
     */
    public long getCreationTimestamp();

    // =================================================================================================================
    // QUERIES
    // =================================================================================================================

    /**
     * Returns the value for the given key at the given timestamp together with the time range in which it is valid.
     *
     * @param timestamp The timestamp at which to get the value for the given key. Must not be negative.
     * @param key       The key to get the value for. Must not be <code>null</code>.
     * @return A ranged result. The result object itself is guaranteed to be non-<code>null</code>. Contains the
     * {@linkplain GetResult#getValue() value} for the given key at the given timestamp, or contains a
     * <code>null</code>-{@linkplain GetResult#getValue() value} if the key does not exist in this matrix at the
     * given timestamp. The {@linkplain GetResult#getPeriod() range} of the returned object represents the
     * period in which the given key is bound to the returned value.
     */
    public GetResult<byte[]> get(final long timestamp, final String key);

    /**
     * Returns the history of the given key, i.e. all timestamps at which the given key changed its value due to a
     * commit.
     *
     * @param key     The key to get the commit timestamps for. Must not be <code>null</code>.
     * @param lowerBound The (inclusive) lower bound of the timestamp range to search in. Must not be negative. Must be less than or equal to <code>upperBound</code>.
     * @param upperBound The (inclusive) upper bound of the timestamp range to search in. Must not be negative. Must be greater than or equal to the <code>lowerBound</code>.
     * @param order The desired ordering of the result iterator. Must not be <code>null</code>.
     * @return An iterator over all commit timestamps on the given key, up to the given maximum timestamp. May be empty,
     * but never <code>null</code>.
     */
    public Iterator<Long> history(final String key, long lowerBound, long upperBound, Order order);



    /**
     * Returns an iterator over all entries in this matrix.
     *
     * @param minTimestamp The minimum timestamp to include. Only entries with timestamps greater than or equal to this timestamp
     *                  will be considered. Must not be negative. Must be less than or equal to <code>maxTimestamp</code>.
     * @param maxTimestamp The maximum timestamp to include. Only entries with timestamps less than or equal to this timestamp
     *                  will be considered. Must not be negative. Must be greater than or equal to <code>minTimestamp</code>.
     * @return An iterator over all entries up to the given timestamp. May be empty, but never <code>null</code>.
     */
    public CloseableIterator<UnqualifiedTemporalEntry> allEntriesIterator(long minTimestamp, long maxTimestamp);

    /**
     * Returns the timestamp at which the last (latest) commit has happened on the given key.
     *
     * @param key The key in question. Must not be <code>null</code>.
     * @return The latest commit timestamp, or a negative value if there has never been a commit to this key in this
     * matrix.
     */
    public default long lastCommitTimestamp(String key){
        return this.lastCommitTimestamp(key, System.currentTimeMillis());
    }

    /**
     * Returns the last (highest) modification timestamp of the given <code>key</code> which is less than or equal to the given <code>upperBound</code>.
     *
     * @param key The key to get the last modification timestamp for. Must not be <code>null</code>.
     * @param upperBound The maximum change timestamp to consider (inclusive).
     * @return The highest modification timestamp on the given key which is less than or equal to the given upper bound, or -1 to indicate that the key has not existed up to this point.
     */
    public long lastCommitTimestamp(String key, long upperBound);

    /**
     * Returns the {@link TemporalKey temporal keys} that were modified in the given time range.
     *
     * @param timestampLowerBound The lower bound on the timestamp to search for (inclusive). Must not be negative. Must be less than or
     *                            equal to the upper bound.
     * @param timestampUpperBound The upper bound on the timestamp to search for (inclusive). Must not be negative. Must be larger than
     *                            or equal to the lower bound.
     * @return An iterator over the temporal keys that were modified in the given time range. May be empty, but never
     * <code>null</code>.
     * @see #getCommitTimestampsBetween(long, long)
     */
    public Iterator<TemporalKey> getModificationsBetween(long timestampLowerBound, long timestampUpperBound);

    /**
     * Returns the timestamps at which actual commits have taken place in the given time range.
     *
     * @param timestampLowerBound The lower bound on the timestamp to search for (inclusive). Must not be negative. Must be less than or
     *                            equal to the upper bound.
     * @param timestampUpperBound The upper bound on the timestamp to search for (inclusive). Must not be negative. Must be larger than
     *                            or equal to the lower bound.
     * @return An iterator over the commit timestamps in the given time range. May be empty, but never <code>null</code>
     * .
     * @see #getModificationsBetween(long, long)
     */
    public Iterator<Long> getCommitTimestampsBetween(long timestampLowerBound, long timestampUpperBound);

    /**
     * Returns an iterator over the keys which changed their value at the given commit timestamp.
     *
     * @param commitTimestamp The commit timestamp to analyze. Must exactly match a commit timestamp in the history, otherwise the
     *                        resulting iterator will be empty. Must not be negative.
     * @return An iterator over the keys which changed in this matrix at the commit with the given timestamp. Never
     * <code>null</code>. Will be empty if there have been no changes in the given keyspace at the given commit,
     * if no commit has taken place at the specified timestamp, or a keyspace with the given name did not exist
     * at that timestamp.
     */
    public Iterator<String> getChangedKeysAtCommit(long commitTimestamp);

    /**
     * Returns the modifications to the keyset performed in this matrix, up to the given timestamp.
     *
     * @param timestamp The timestamp for which to retrieve the modifications. Must not be negative. Must be smaller than the
     *                  timestamp of the latest change.
     * @return the keyset modifications. May be empty, but never <code>null</code>.
     */
    public KeySetModifications keySetModifications(long timestamp);

    /**
     * Returns the total number of entries in this matrix.
     *
     * <p>
     * For chunked backends, this will return the size of the head chunk.
     * </p>
     *
     * @return The total number of entries. Never negative.
     */
    public long size();

    // =================================================================================================================
    // CONTENT MODIFICATION
    // =================================================================================================================

    /**
     * Adds the given contents to this matrix, at the given timestamp.
     *
     * @param timestamp The timestamp at which to add the given contents to this matrix. Must not be negative.
     * @param contents  The key-value pairs to add, as a map. Must not be <code>null</code>. If the map is empty, this method
     *                  is a no-op and returns immediately.
     */
    public void put(final long timestamp, final Map<String, byte[]> contents);

    /**
     * Inserts the given set of entries into this matrix.
     *
     * @param entries The entries to insert. Must not be <code>null</code>. If the set is empty, this method is a no-op and
     *                returns immediately.
     * @param force   Force the insertion of the entries, bypassing safety checks. Use <code>true</code> to force the
     *                insertion.
     */
    public void insertEntries(Set<UnqualifiedTemporalEntry> entries, boolean force);

    /**
     * Performs a rollback of the contents of this matrix to the given timestamp.
     *
     * <p>
     * Any entries which have been inserted after the given timestamp will be removed during the process.
     *
     * @param timestamp The timestamp to roll back to. Must not be negative.
     */
    public void rollback(long timestamp);

    // =================================================================================================================
    // DATEBACK OPERATIONS
    // =================================================================================================================

    /**
     * Purges the entry at the given key and timestamp from this matrix.
     *
     * @param key       The key of the entry to purge. Must not be <code>null</code>.
     * @param timestamp The timestamp of the entry to purge (exact match). Must not be negative.
     * @return <code>true</code> if the entry was purged successfully, or <code>false</code> if there was no entry to
     * purge at the given coordinates.
     */
    public default boolean purgeEntry(String key, long timestamp) {
        return this.purgeEntries(Collections.singleton(UnqualifiedTemporalKey.create(key, timestamp))) > 0;
    }

    /**
     * Purges the entries at the given coordinates from this matrix.
     *
     * @param keys The keys to purge. Must not be <code>null</code>, may be empty.
     * @return The number of successfully purged entries. Keys which did not exist do not count towards this result.
     */
    public int purgeEntries(Set<UnqualifiedTemporalKey> keys);

    /**
     * Purges the given key from the matrix on all timestamps.
     *
     * @param key The key to purge. Must not be <code>null</code>.
     * @return The timestamps at which the key was removed. May be empty, but never <code>null</code>.
     */
    public Collection<Long> purgeKey(String key);

    /**
     * Purges all entries in the matrix that reside in the given time range, eliminating them completely from the history.
     *
     * @param purgeRangeStart The lower bound of the range to purge entries from (inclusive). Must not be negative, must not be
     *                        larger than <code>purgeRangeEnd</code>.
     * @param purgeRangeEnd   The upper bound of the range to purge entries from (inclusive). Must not be negative, must be greater
     *                        than or equal to <code>purgeRangeStart</code>.
     * @return The set of coordinates which have been affected by the operation. Never <code>null</code>, may be empty.
     */
    public Set<UnqualifiedTemporalKey> purgeAllEntriesInTimeRange(long purgeRangeStart, long purgeRangeEnd);

    /**
     * Ensures that the creation timestamp of this matrix is greater than or equal to the given value.
     *
     * <p>
     * If the creation timestamp of this matrix happens to be greater than the given timestamp, it will be adjusted to match it.
     * </p>
     *
     * @param creationTimestamp The creation timestamp to match. Must not be negative.
     */
    public void ensureCreationTimestampIsGreaterThanOrEqualTo(long creationTimestamp);
}
