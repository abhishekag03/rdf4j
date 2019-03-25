/*******************************************************************************
 * Copyright (c) 2018 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.sail.shacl;

import org.apache.commons.io.IOUtils;
import org.eclipse.rdf4j.IsolationLevels;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.vocabulary.RDF4J;
import org.eclipse.rdf4j.model.vocabulary.SHACL;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.NotifyingSail;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.helpers.NotifyingSailWrapper;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.shacl.AST.NodeShape;
import org.eclipse.rdf4j.sail.shacl.config.ShaclSailConfig;
import org.eclipse.rdf4j.sail.shacl.planNodes.LoggingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A {@link Sail} implementation that adds support for the Shapes Constraint Language (SHACL).
 * <p>
 * The ShaclSail looks for SHACL shape data in a special named graph {@link RDF4J#SHACL_SHAPE_GRAPH}.
 * <h4>Working example</h4>
 * <p>
 * 
 * <pre>
 * import ch.qos.logback.classic.Level;
 * import ch.qos.logback.classic.Logger;
 * import org.eclipse.rdf4j.model.Model;
 * import org.eclipse.rdf4j.model.vocabulary.RDF4J;
 * import org.eclipse.rdf4j.repository.RepositoryException;
 * import org.eclipse.rdf4j.repository.sail.SailRepository;
 * import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
 * import org.eclipse.rdf4j.rio.RDFFormat;
 * import org.eclipse.rdf4j.rio.Rio;
 * import org.eclipse.rdf4j.sail.memory.MemoryStore;
 * import org.eclipse.rdf4j.sail.shacl.ShaclSail;
 * import org.eclipse.rdf4j.sail.shacl.ShaclSailValidationException;
 * import org.eclipse.rdf4j.sail.shacl.results.ValidationReport;
 * import org.slf4j.LoggerFactory;
 *
 * import java.io.IOException;
 * import java.io.StringReader;
 *
 * public class ShaclSampleCode {
 *
 * 	public static void main(String[] args) throws IOException {
 *
 * 		ShaclSail shaclSail = new ShaclSail(new MemoryStore());
 *
 * 		// Logger root = (Logger) LoggerFactory.getLogger(ShaclSail.class.getName());
 * 		// root.setLevel(Level.INFO);
 *
 * 		// shaclSail.setLogValidationPlans(true);
 * 		// shaclSail.setGlobalLogValidationExecution(true);
 * 		// shaclSail.setLogValidationViolations(true);
 *
 * 		SailRepository sailRepository = new SailRepository(shaclSail);
 * 		sailRepository.init();
 *
 * 		try (SailRepositoryConnection connection = sailRepository.getConnection()) {
 *
 * 			connection.begin();
 *
 * 			StringReader shaclRules = new StringReader(String.join(&quot;\n&quot;, &quot;&quot;,
 * 					&quot;@prefix ex: &lt;http://example.com/ns#&gt; .&quot;, &quot;@prefix sh: &lt;http://www.w3.org/ns/shacl#&gt; .&quot;,
 * 					&quot;@prefix xsd: &lt;http://www.w3.org/2001/XMLSchema#&gt; .&quot;,
 * 					&quot;@prefix foaf: &lt;http://xmlns.com/foaf/0.1/&gt;.&quot;,
 *
 * 					&quot;ex:PersonShape&quot;, &quot;	a sh:NodeShape  ;&quot;, &quot;	sh:targetClass foaf:Person ;&quot;,
 * 					&quot;	sh:property ex:PersonShapeProperty .&quot;,
 *
 * 					&quot;ex:PersonShapeProperty &quot;, &quot;	sh:path foaf:age ;&quot;, &quot;	sh:datatype xsd:int ;&quot;,
 * 					&quot;	sh:maxCount 1 ;&quot;, &quot;	sh:minCount 1 .&quot;));
 *
 * 			connection.add(shaclRules, &quot;&quot;, RDFFormat.TURTLE, RDF4J.SHACL_SHAPE_GRAPH);
 * 			connection.commit();
 *
 * 			connection.begin();
 *
 * 			StringReader invalidSampleData = new StringReader(String.join(&quot;\n&quot;, &quot;&quot;,
 * 					&quot;@prefix ex: &lt;http://example.com/ns#&gt; .&quot;, &quot;@prefix foaf: &lt;http://xmlns.com/foaf/0.1/&gt;.&quot;,
 * 					&quot;@prefix xsd: &lt;http://www.w3.org/2001/XMLSchema#&gt; .&quot;,
 *
 * 					&quot;ex:peter a foaf:Person ;&quot;, &quot;	foaf:age 20, \&quot;30\&quot;^^xsd:int  .&quot;
 *
 * 			));
 *
 * 			connection.add(invalidSampleData, &quot;&quot;, RDFFormat.TURTLE);
 * 			try {
 * 				connection.commit();
 * 			} catch (RepositoryException exception) {
 * 				Throwable cause = exception.getCause();
 * 				if (cause instanceof ShaclSailValidationException) {
 * 					ValidationReport validationReport = ((ShaclSailValidationException) cause).getValidationReport();
 * 					Model validationReportModel = ((ShaclSailValidationException) cause).validationReportAsModel();
 * 					// use validationReport or validationReportModel to understand validation violations
 *
 * 					Rio.write(validationReportModel, System.out, RDFFormat.TURTLE);
 * 				}
 * 				throw exception;
 * 			}
 * 		}
 * 	}
 * }
 * </pre>
 *
 * @author Heshan Jayasinghe
 * @author Håvard Ottestad
 * @see <a href="https://www.w3.org/TR/shacl/">SHACL W3C Recommendation</a>
 */
public class ShaclSail extends NotifyingSailWrapper {

	private static final Logger logger = LoggerFactory.getLogger(ShaclSail.class);

	private List<NodeShape> nodeShapes = Collections.emptyList();

	private static String SH_OR_UPDATE_QUERY;
	private static String IMPLICIT_TARGET_CLASS_NODE_SHAPE;
	private static String IMPLICIT_TARGET_CLASS_PROPERTY_SHAPE;

	/**
	 * an initialized {@link Repository} for storing/retrieving Shapes data
	 */
	private SailRepository shapesRepo;

	private boolean parallelValidation = ShaclSailConfig.PARALLEL_VALIDATION_DEFAULT;
	private boolean undefinedTargetValidatesAllSubjects = ShaclSailConfig.UNDEFINED_TARGET_VALIDATES_ALL_SUBJECTS_DEFAULT;
	private boolean logValidationPlans = ShaclSailConfig.LOG_VALIDATION_PLANS_DEFAULT;
	private boolean logValidationViolations = ShaclSailConfig.LOG_VALIDATION_VIOLATIONS_DEFAULT;
	private boolean ignoreNoShapesLoadedException = ShaclSailConfig.IGNORE_NO_SHAPES_LOADED_EXCEPTION_DEFAULT;
	private boolean validationEnabled = ShaclSailConfig.VALIDATION_ENABLED_DEFAULT;
	private boolean cacheSelectNodes = ShaclSailConfig.CACHE_SELECT_NODES_DEFAULT;
	private boolean rdfsSubClassReasoning = ShaclSailConfig.RDFS_SUB_CLASS_REASONING_DEFAULT;

	private boolean initializing = false;

	static {
		try {
			SH_OR_UPDATE_QUERY = resourceAsString("shacl-sparql-inference/sh_or.rq");
			IMPLICIT_TARGET_CLASS_NODE_SHAPE = resourceAsString(
					"shacl-sparql-inference/implicitTargetClassNodeShape.rq");
			IMPLICIT_TARGET_CLASS_PROPERTY_SHAPE = resourceAsString(
					"shacl-sparql-inference/implicitTargetClassPropertyShape.rq");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

	}

	private static String resourceAsString(String s) throws IOException {
		return IOUtils.toString(ShaclSail.class.getClassLoader().getResourceAsStream(s), "UTF-8");
	}

	public ShaclSail(NotifyingSail baseSail) {
		super(baseSail);

	}

	public ShaclSail() {
		super();
	}

	/**
	 * Lists the predicates that have been implemented in the ShaclSail. All of these, and all combinations,
	 * <i>should</i> work, please report any bugs. For sh:path, only single predicate paths are supported.
	 *
	 * @return List of IRIs (SHACL predicates)
	 */
	public static List<IRI> getSupportedShaclPredicates() {
		return Arrays.asList(
				SHACL.TARGET_CLASS,
				SHACL.PATH,
				SHACL.PROPERTY,
				SHACL.OR,
				SHACL.AND,
				SHACL.MIN_COUNT,
				SHACL.MAX_COUNT,
				SHACL.MIN_LENGTH,
				SHACL.MAX_LENGTH,
				SHACL.PATTERN,
				SHACL.FLAGS,
				SHACL.NODE_KIND_PROP,
				SHACL.LANGUAGE_IN,
				SHACL.DATATYPE,
				SHACL.MIN_EXCLUSIVE,
				SHACL.MIN_INCLUSIVE,
				SHACL.MAX_EXCLUSIVE,
				SHACL.MAX_INCLUSIVE,
				SHACL.CLASS,
				SHACL.TARGET_NODE,
				SHACL.DEACTIVATED,
				SHACL.TARGET_SUBJECTS_OF,
				SHACL.IN,
				SHACL.UNIQUE_LANG,
				SHACL.TARGET_OBJECTS_OF);
	}

	@Override
	public void initialize() throws SailException {
		initializing = true;
		super.initialize();

		if (getDataDir() != null) {
			if (parallelValidation) {
				logger.info("Automatically disabled parallel SHACL validation because persistent base sail "
						+ "was detected! Re-enable by calling setParallelValidation(true) after calling init() / initialize().");
			}
			setParallelValidation(false);
		}

		if (shapesRepo != null) {
			shapesRepo.shutDown();
			shapesRepo = null;
		}

		if (super.getBaseSail().getDataDir() != null) {
			String path = super.getBaseSail().getDataDir().getPath();
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			path = path + "-shapes-graph/";

			shapesRepo = new SailRepository(new MemoryStore(new File(path)));
		} else {
			shapesRepo = new SailRepository(new MemoryStore());
		}

		shapesRepo.init();

		try (SailRepositoryConnection shapesRepoConnection = shapesRepo.getConnection()) {
			shapesRepoConnection.begin(IsolationLevels.NONE);
			refreshShapes(shapesRepoConnection);
			shapesRepoConnection.commit();
		}
		initializing = false;

	}

	synchronized List<NodeShape> refreshShapes(SailRepositoryConnection shapesRepoConnection) throws SailException {
		if (!initializing) {
			try (SailRepositoryConnection beforeCommitConnection = shapesRepo.getConnection()) {
				boolean empty = !beforeCommitConnection.hasStatement(null, null, null, false);
				if (!empty) {
					// Our inferencer both adds and removes statements.
					// To support updates I recommend having two graphs, one raw one with the unmodified data.
					// Then copy all that data into a new graph, run inferencing on that graph and use it to generate
					// the java objects
					throw new IllegalStateException(
							"ShaclSail does not support modifying shapes that are already loaded or loading more shapes");
				}
			}
		}

		runInferencingSparqlQueries(shapesRepoConnection);
		nodeShapes = NodeShape.Factory.getShapes(shapesRepoConnection, this);
		return nodeShapes;
	}

	@Override
	public void shutDown() throws SailException {
		if (shapesRepo != null) {
			shapesRepo.shutDown();
			shapesRepo = null;
		}
		super.shutDown();
	}

	@Override
	public NotifyingSailConnection getConnection() throws SailException {
		return new ShaclSailConnection(this, super.getConnection(), super.getConnection(), shapesRepo.getConnection());
	}

	/**
	 * Disable the SHACL validation on commit()
	 */
	public void disableValidation() {
		this.validationEnabled = false;
	}

	/**
	 * Enabled the SHACL validation on commit()
	 */
	public void enableValidation() {
		this.validationEnabled = true;
	}

	/**
	 * Check if SHACL validation on commit() is enabled.
	 *
	 * @return <code>true</code> if validation is enabled, <code>false</code> otherwise.
	 */
	public boolean isValidationEnabled() {
		return validationEnabled;
	}

	/**
	 * Check if logging of validation plans is enabled.
	 *
	 * @return <code>true</code> if validation plan logging is enabled, <code>false</code> otherwise.
	 */
	public boolean isLogValidationPlans() {
		return this.logValidationPlans;
	}

	public boolean isIgnoreNoShapesLoadedException() {
		return this.ignoreNoShapesLoadedException;
	}

	/**
	 * Check if shapes have been loaded into the shapes graph before other data is added
	 *
	 * @param ignoreNoShapesLoadedException
	 */
	public void setIgnoreNoShapesLoadedException(boolean ignoreNoShapesLoadedException) {
		this.ignoreNoShapesLoadedException = ignoreNoShapesLoadedException;
	}

	/**
	 * Log (INFO) the executed validation plans as GraphViz DOT Recommended to disable parallel validation with
	 * setParallelValidation(false)
	 *
	 * @param logValidationPlans
	 */
	public void setLogValidationPlans(boolean logValidationPlans) {
		this.logValidationPlans = logValidationPlans;
	}

	List<NodeShape> getNodeShapes() {
		return nodeShapes;
	}

	private void runInferencingSparqlQueries(SailRepositoryConnection shaclSailConnection) {

		long prevSize;
		long currentSize = shaclSailConnection.size();
		do {
			prevSize = currentSize;
			shaclSailConnection.prepareUpdate(IMPLICIT_TARGET_CLASS_PROPERTY_SHAPE).execute();
			shaclSailConnection.prepareUpdate(IMPLICIT_TARGET_CLASS_NODE_SHAPE).execute();
			shaclSailConnection.prepareUpdate(SH_OR_UPDATE_QUERY).execute();
			currentSize = shaclSailConnection.size();
		} while (prevSize != currentSize);

	}

	/**
	 * Log (INFO) every execution step of the SHACL validation. This is fairly costly and should not be used in
	 * production. Recommended to disable parallel validation with setParallelValidation(false)
	 *
	 * @param loggingEnabled
	 */
	public void setGlobalLogValidationExecution(boolean loggingEnabled) {
		LoggingNode.loggingEnabled = loggingEnabled;
	}

	/**
	 * Check if logging of every execution steps is enabled.
	 *
	 * @return <code>true</code> if enabled, <code>false</code> otherwise.
	 * @see #setGlobalLogValidationExecution(boolean)
	 */
	public boolean isGlobalLogValidationExecution() {
		return LoggingNode.loggingEnabled;
	}

	/**
	 * Check if logging a list of violations and the triples that caused the violations is enabled. It is recommended to
	 * disable parallel validation with {@link #setParallelValidation(boolean)}
	 *
	 * @see #setLogValidationViolations(boolean)
	 */
	public boolean isLogValidationViolations() {
		return this.logValidationViolations;
	}

	/**
	 * Log (INFO) a list of violations and the triples that caused the violations (BETA). Recommended to disable
	 * parallel validation with setParallelValidation(false)
	 *
	 * @param logValidationViolations
	 */
	public void setLogValidationViolations(boolean logValidationViolations) {
		this.logValidationViolations = logValidationViolations;
	}

	/**
	 * If no target is defined for a NodeShape, that NodeShape will be ignored. Calling this method with "true" will
	 * make such NodeShapes wildcard shapes and validate all subjects. Equivalent to setting sh:targetClass to owl:Thing
	 * or rdfs:Resource in an environment with a reasoner.
	 *
	 * @param undefinedTargetValidatesAllSubjects default false
	 */
	public void setUndefinedTargetValidatesAllSubjects(boolean undefinedTargetValidatesAllSubjects) {
		this.undefinedTargetValidatesAllSubjects = undefinedTargetValidatesAllSubjects;
	}

	/**
	 * Check if {@link NodeShape}s without a defined target are considered wildcards.
	 *
	 * @return <code>true</code> if enabled, <code>false</code> otherwise
	 * @see #setUndefinedTargetValidatesAllSubjects(boolean)
	 */
	public boolean isUndefinedTargetValidatesAllSubjects() {
		return this.undefinedTargetValidatesAllSubjects;
	}

	/**
	 * Check if SHACL validation is run in parellel.
	 *
	 * @return <code>true</code> if enabled, <code>false</code> otherwise.
	 */
	public boolean isParallelValidation() {
		return this.parallelValidation;
	}

	/**
	 * EXPERIMENTAL! Run SHACL validation in parallel. Default: false
	 * <p>
	 * May cause deadlock, especially when using NativeStore.
	 *
	 * @param parallelValidation default false
	 */
	public void setParallelValidation(boolean parallelValidation) {
		if (parallelValidation) {
			logger.warn("Parallel SHACL validation enabled. This is an experimental feature and may cause deadlocks!");
		}
		this.parallelValidation = parallelValidation;
	}

	/**
	 * Check if selected nodes caches is enabled.
	 *
	 * @return <code>true</code> if enabled, <code>false</code> otherwise.
	 * @see #setCacheSelectNodes(boolean)
	 */
	public boolean isCacheSelectNodes() {
		return this.cacheSelectNodes;
	}

	/**
	 * The ShaclSail retries a lot of its relevant data through running SPARQL Select queries against the underlying
	 * sail and against the changes in the transaction. This is usually good for performance, but while validating large
	 * amounts of data disabling this cache will use less memory. Default: true
	 *
	 * @param cacheSelectNodes default true
	 */
	public void setCacheSelectNodes(boolean cacheSelectNodes) {
		this.cacheSelectNodes = cacheSelectNodes;
	}

	public boolean isRdfsSubClassReasoning() {
		return rdfsSubClassReasoning;
	}

	public void setRdfsSubClassReasoning(boolean rdfsSubClassReasoning) {
		this.rdfsSubClassReasoning = rdfsSubClassReasoning;
	}
}
