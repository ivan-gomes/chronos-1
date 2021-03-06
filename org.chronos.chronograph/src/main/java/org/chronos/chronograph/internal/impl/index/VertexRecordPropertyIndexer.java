package org.chronos.chronograph.internal.impl.index;

import java.util.Optional;
import java.util.Set;

import org.chronos.chronodb.api.indexing.StringIndexer;
import org.chronos.chronograph.api.structure.record.IPropertyRecord;
import org.chronos.chronograph.api.structure.record.IVertexRecord;
import org.chronos.chronograph.internal.impl.structure.record.VertexRecord;
import org.chronos.common.annotation.PersistentClass;

/**
 * An indexer working on {@link VertexRecord}s.
 *
 * @deprecated Superseded by {@link VertexRecordPropertyIndexer2}.
 *
 * @author martin.haeusler@uibk.ac.at -- Initial Contribution and API
 */
@Deprecated
@PersistentClass("kryo")
public class VertexRecordPropertyIndexer extends AbstractRecordPropertyIndexer implements StringIndexer {

	protected VertexRecordPropertyIndexer() {
		// default constructor for serializer
	}

	public VertexRecordPropertyIndexer(final String propertyName) {
		super(propertyName);
	}

	@Override
	public boolean canIndex(final Object object) {
		return object instanceof IVertexRecord;
	}

	@Override
	public Set<String> getIndexValues(final Object object) {
		IVertexRecord vertexRecord = (IVertexRecord) object;
		Optional<? extends IPropertyRecord> maybePropertyRecord = vertexRecord.getProperties().stream()
				.filter(pRecord -> pRecord.getKey().equals(this.propertyName)).findAny();
		return this.getIndexValue(maybePropertyRecord);
	}

}
