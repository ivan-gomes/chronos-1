package org.chronos.chronograph.internal.impl.dumpformat;

import static com.google.common.base.Preconditions.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.chronos.chronograph.api.structure.record.IEdgeTargetRecord;
import org.chronos.chronograph.api.structure.record.IVertexPropertyRecord;
import org.chronos.chronograph.api.structure.record.IVertexRecord;
import org.chronos.chronograph.internal.impl.dumpformat.property.AbstractPropertyDump;
import org.chronos.chronograph.internal.impl.dumpformat.vertexproperty.VertexPropertyDump;
import org.chronos.chronograph.internal.impl.structure.record.*;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.chronos.chronograph.internal.impl.structure.record2.EdgeTargetRecord2;
import org.chronos.chronograph.internal.impl.structure.record2.PropertyRecord2;
import org.chronos.chronograph.internal.impl.structure.record2.VertexPropertyRecord2;
import org.chronos.chronograph.internal.impl.structure.record2.VertexRecord2;
import org.chronos.chronograph.internal.impl.structure.record3.SimpleVertexPropertyRecord;
import org.chronos.chronograph.internal.impl.structure.record3.VertexPropertyRecord3;
import org.chronos.chronograph.internal.impl.structure.record3.VertexRecord3;

public class VertexDump {

	// =====================================================================================================================
	// FIELDS
	// =====================================================================================================================

	/** The id of this record. */
	private String recordId;
	/** The label of the vertex stored in this record. */
	private String label;
	/** Mapping of edge labels to incoming edges, i.e. edges which specify this vertex as their in-vertex. */
	private Map<String, Set<EdgeTargetDump>> incomingEdges;
	/** Mapping of edge labels to outgoing edges, i.e. edges which specify this vertex as their out-vertex. */
	private Map<String, Set<EdgeTargetDump>> outgoingEdges;
	/** The set of vertex properties known on this vertex. */
	private Set<VertexPropertyDump> properties;

	// =====================================================================================================================
	// CONSTRUCTORS
	// =====================================================================================================================

	protected VertexDump() {
		// serialization constructor
	}

	public VertexDump(final IVertexRecord record) {
		checkNotNull(record, "Precondition violation - argument 'record' must not be NULL!");
		// load the basic properties
		this.recordId = record.getId();
		this.label = record.getLabel();
		// load incoming edges
		this.incomingEdges = Maps.newHashMap();
		for (Entry<String, Collection<IEdgeTargetRecord>> entry : record.getIncomingEdgesByLabel().asMap().entrySet()) {
			String label = entry.getKey();
			Collection<IEdgeTargetRecord> edgeTargets = entry.getValue();
			Set<EdgeTargetDump> targetSet = edgeTargets.stream().map(etr -> new EdgeTargetDump(etr))
					.collect(Collectors.toSet());
			this.incomingEdges.put(label, targetSet);
		}
		// load outgoing edges
		this.outgoingEdges = Maps.newHashMap();
		for (Entry<String, Collection<IEdgeTargetRecord>> entry : record.getOutgoingEdgesByLabel().asMap().entrySet()) {
			String label = entry.getKey();
			Collection<IEdgeTargetRecord> edgeTargets = entry.getValue();
			Set<EdgeTargetDump> targetSet = edgeTargets.stream().map(etr -> new EdgeTargetDump(etr))
					.collect(Collectors.toSet());
			this.outgoingEdges.put(label, targetSet);
		}
		// load the vertex properties
		Set<VertexPropertyDump> props = record.getProperties().stream()
				.map(vpr -> GraphDumpFormat.convertVertexPropertyRecordToDumpFormat(vpr)).collect(Collectors.toSet());
		this.properties = Sets.newHashSet();
		this.properties.addAll(props);
	}

	// =====================================================================================================================
	// PUBLIC API
	// =====================================================================================================================

	public Map<String, Set<EdgeTargetDump>> getIncomingEdges() {
		return Collections.unmodifiableMap(this.incomingEdges);
	}

	public Map<String, Set<EdgeTargetDump>> getOutgoingEdges() {
		return Collections.unmodifiableMap(this.outgoingEdges);
	}

	public String getLabel() {
		return this.label;
	}

	public String getRecordId() {
		return this.recordId;
	}

	public Set<VertexPropertyDump> getProperties() {
		return Collections.unmodifiableSet(this.properties);
	}

	public VertexRecord3 toRecord() {
		// convert incoming edges
		SetMultimap<String, EdgeTargetRecord2> inE = HashMultimap.create();
		for (Entry<String, Set<EdgeTargetDump>> entry : this.incomingEdges.entrySet()) {
			String label = entry.getKey();
			for (EdgeTargetDump edgeDump : entry.getValue()) {
				EdgeTargetRecord2 edgeRecord = new EdgeTargetRecord2(edgeDump.getEdgeId(),
						edgeDump.getOtherEndVertexId());
				inE.put(label, edgeRecord);
			}
		}
		// convert outgoing edges
		SetMultimap<String, EdgeTargetRecord2> outE = HashMultimap.create();
		for (Entry<String, Set<EdgeTargetDump>> entry : this.outgoingEdges.entrySet()) {
			String label = entry.getKey();
			for (EdgeTargetDump edgeDump : entry.getValue()) {
				EdgeTargetRecord2 edgeRecord = new EdgeTargetRecord2(edgeDump.getEdgeId(),
						edgeDump.getOtherEndVertexId());
				outE.put(label, edgeRecord);
			}
		}
		// convert properties
		Set<IVertexPropertyRecord> props = Sets.newHashSet();
		for (VertexPropertyDump property : this.properties) {
			Map<String, AbstractPropertyDump> metaPropsDump = property.getProperties();
			Map<String, PropertyRecord2> metaProps = Maps.newHashMap();
			for (Entry<String, AbstractPropertyDump> entry : metaPropsDump.entrySet()) {
				String key = entry.getKey();
				AbstractPropertyDump propertyDump = entry.getValue();
				PropertyRecord2 pRecord = new PropertyRecord2(key, propertyDump.getValue());
				metaProps.put(key, pRecord);
			}
			if(metaProps.isEmpty()){
				props.add(new SimpleVertexPropertyRecord(property.getKey(), property.getValue()));
			}else{
				props.add(new VertexPropertyRecord3(property.getKey(), property.getValue(), metaProps));
			}
		}
		return new VertexRecord3(this.recordId, this.label, inE, outE, props);
	}
}
