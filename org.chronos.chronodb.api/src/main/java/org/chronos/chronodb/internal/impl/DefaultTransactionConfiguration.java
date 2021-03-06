package org.chronos.chronodb.internal.impl;

import org.chronos.chronodb.api.ChronoDBConstants;
import org.chronos.chronodb.api.DuplicateVersionEliminationMode;
import org.chronos.chronodb.api.conflict.ConflictResolutionStrategy;
import org.chronos.chronodb.internal.api.MutableTransactionConfiguration;
import org.chronos.chronodb.internal.api.TransactionConfigurationInternal;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.*;

public class DefaultTransactionConfiguration implements MutableTransactionConfiguration {

    private Long timestamp;
    private String branch;

    private boolean threadSafe;
    private ConflictResolutionStrategy conflictResolutionStrategy;
    private DuplicateVersionEliminationMode duplicateVersionEliminationMode;
    private boolean readOnly;
    private boolean allowedDuringDateback;

    private boolean frozen;

    public DefaultTransactionConfiguration() {
        this.branch = ChronoDBConstants.MASTER_BRANCH_IDENTIFIER;
        this.threadSafe = false;
        this.conflictResolutionStrategy = ConflictResolutionStrategy.DO_NOT_MERGE;
        this.duplicateVersionEliminationMode = DuplicateVersionEliminationMode.ON_COMMIT;
        this.readOnly = false;
        this.allowedDuringDateback = false;
        this.frozen = false;
    }

    public DefaultTransactionConfiguration(TransactionConfigurationInternal other, Consumer<DefaultTransactionConfiguration> modifications) {
        this.timestamp = other.getTimestamp();
        this.branch = other.getBranch();
        this.threadSafe = other.isThreadSafe();
        this.conflictResolutionStrategy = other.getConflictResolutionStrategy();
        this.duplicateVersionEliminationMode = other.getDuplicateVersionEliminationMode();
        this.readOnly = other.isReadOnly();
        this.allowedDuringDateback = other.isAllowedDuringDateback();
        modifications.accept(this);
        this.freeze();
    }

    @Override
    public void setBranch(final String branchName) {
        this.assertNotFrozen();
        checkNotNull(branchName, "Precondition violation - argument 'branchName' must not be NULL!");
        this.branch = branchName;
    }

    @Override
    public String getBranch() {
        return this.branch;
    }

    @Override
    public void setTimestamp(final long timestamp) {
        this.assertNotFrozen();
        checkArgument(timestamp >= 0, "Precondition violation - argument 'timestamp' must not be negative!");
        this.timestamp = timestamp;
    }

    @Override
    public Long getTimestamp() {
        return this.timestamp;
    }

    @Override
    public void setTimestampToNow() {
        this.assertNotFrozen();
        this.timestamp = null;
    }

    @Override
    public boolean isThreadSafe() {
        return this.threadSafe;
    }

    @Override
    public void setThreadSafe(final boolean threadSafe) {
        this.assertNotFrozen();
        this.threadSafe = threadSafe;
    }

    @Override
    public ConflictResolutionStrategy getConflictResolutionStrategy() {
        return this.conflictResolutionStrategy;
    }

    @Override
    public void setConflictResolutionStrategy(final ConflictResolutionStrategy strategy) {
        this.assertNotFrozen();
        checkNotNull(strategy, "Precondition violation - argument 'strategy' must not be NULL!");
        this.conflictResolutionStrategy = strategy;
    }

    @Override
    public DuplicateVersionEliminationMode getDuplicateVersionEliminationMode() {
        return this.duplicateVersionEliminationMode;
    }

    @Override
    public void setDuplicateVersionEliminationMode(final DuplicateVersionEliminationMode mode) {
        this.assertNotFrozen();
        checkNotNull(mode, "Precondition violation - argument 'mode' must not be NULL!");
        this.duplicateVersionEliminationMode = mode;
    }

    @Override
    public boolean isReadOnly() {
        return this.readOnly;
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        this.assertNotFrozen();
        this.readOnly = readOnly;
    }

    @Override
    public boolean isAllowedDuringDateback() {
        return this.allowedDuringDateback;
    }

    @Override
    public void setAllowedDuringDateback(final boolean allowedDuringDateback) {
        this.assertNotFrozen();
        this.allowedDuringDateback = allowedDuringDateback;
    }

    @Override
    public void freeze() {
        this.frozen = true;
    }

    // =====================================================================================================================
    // INTERNAL HELPER METHODS
    // =====================================================================================================================

    private void assertNotFrozen() {
        if (this.frozen) {
            throw new IllegalStateException("Cannot modify Transaction Coniguration - it has already been frozen!");
        }
    }

}
