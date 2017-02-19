

package org.optaplanner.core.config.multiLevel;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import org.optaplanner.core.config.heuristic.policy.HeuristicConfigPolicy;
import org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.optaplanner.core.config.util.ConfigUtils;
import org.optaplanner.core.impl.multilevelphase.DefaultMultiLevelSearchPhase;
import org.optaplanner.core.impl.multilevelphase.MultiLevelProvider;
import org.optaplanner.core.impl.multilevelphase.MultiLevelSearchPhase;
import org.optaplanner.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.core.impl.solver.ChildThreadType;
import org.optaplanner.core.impl.solver.recaller.BestSolutionRecaller;
import org.optaplanner.core.impl.solver.termination.Termination;
import org.optaplanner.core.impl.solver.thread.DefaultSolverThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

@XStreamAlias("multiLevelSearch")
public class MultiLevelPhaseConfig extends PhaseConfig<MultiLevelPhaseConfig> {

    public static final String ACTIVE_THREAD_COUNT_AUTO = "AUTO";
    public static final String ACTIVE_THREAD_COUNT_UNLIMITED = "UNLIMITED";

    private static final Logger logger = LoggerFactory.getLogger(MultiLevelPhaseConfig.class);

    // Warning: all fields are null (and not defaulted) because they can be inherited
    // and also because the input config file should match the output config file

    private Class<MultiLevelProvider> multiLevelProviderClass = null;
    
    protected MultiLevelProvider multiLevelProvider = null;
    
    protected Class<? extends ThreadFactory> threadFactoryClass = null;
    protected String runnablePartThreadLimit = null;

    protected boolean  multithreaded = true;
    
    @XStreamImplicit()
    protected List<PhaseConfig> phaseConfigList = null;

    // ************************************************************************
    // Constructors and simple getters/setters
    // ************************************************************************

    public Class<MultiLevelProvider> getMultiLevelProviderClass() {
        return multiLevelProviderClass;
    }

    public void setMultiLevelProviderClass(Class<MultiLevelProvider> solutionPartitionerClass) {
        this.multiLevelProviderClass = solutionPartitionerClass;
    }

    public Class<? extends ThreadFactory> getThreadFactoryClass() {
        return threadFactoryClass;
    }

    public void setThreadFactoryClass(Class<? extends ThreadFactory> threadFactoryClass) {
        this.threadFactoryClass = threadFactoryClass;
    }

    /**
     * Similar to a thread pool size, but instead of limiting the number of {@link Thread}s,
     * it limits the number of {@link Thread.State#RUNNABLE} {@link Thread}s to avoid consuming all CPU resources
     * (which would starve UI, Servlets and REST threads).
     * <p/>
     * The number of {@link Thread}s is always equal to the number of partitions returned by {@link SolutionPartitioner#splitWorkingSolution(ScoreDirector)},
     * because otherwise some partitions would never run (especially with {@link Solver#terminateEarly() asynchronous termination}).
     * If this limit (or {@link Runtime#availableProcessors()) is lower than the number of partitions,
     * this results in a slower score calculation speed per partition {@link Solver}.
     * <p/>
     * Defaults to {@value #ACTIVE_THREAD_COUNT_AUTO} which consumes the majority
     * but not all of the CPU cores on multi-core machines, preventing other processes (including your IDE or SSH connection)
     * on the machine from hanging.
     * <p/>
     * Use {@value #ACTIVE_THREAD_COUNT_UNLIMITED} to give it all CPU cores.
     * This is usefull if you're handling the CPU consumption on an OS level.
     * @return null, a number, {@value #ACTIVE_THREAD_COUNT_AUTO}, {@value #ACTIVE_THREAD_COUNT_UNLIMITED}
     * or a JavaScript calculation using {@value ConfigUtils#AVAILABLE_PROCESSOR_COUNT}.
     */
    public String getRunnablePartThreadLimit() {
        return runnablePartThreadLimit;
    }

    public void setRunnablePartThreadLimit(String runnablePartThreadLimit) {
        this.runnablePartThreadLimit = runnablePartThreadLimit;
    }

    public List<PhaseConfig> getPhaseConfigList() {
        return phaseConfigList;
    }

    public void setPhaseConfigList(List<PhaseConfig> phaseConfigList) {
        this.phaseConfigList = phaseConfigList;
    }

    // ************************************************************************
    // Builder methods
    // ************************************************************************

    @Override
    public MultiLevelSearchPhase buildPhase(int phaseIndex, HeuristicConfigPolicy solverConfigPolicy,
            BestSolutionRecaller bestSolutionRecaller, Termination solverTermination) {
    	    	
    	
        HeuristicConfigPolicy phaseConfigPolicy = solverConfigPolicy.createPhaseConfigPolicy();
        DefaultMultiLevelSearchPhase phase = new DefaultMultiLevelSearchPhase(
                phaseIndex, solverConfigPolicy.getLogIndentation(), bestSolutionRecaller,
                buildPhaseTermination(phaseConfigPolicy, solverTermination));
        
        phase.setConfigPolicy(phaseConfigPolicy.createChildThreadConfigPolicy(ChildThreadType.PART_THREAD));
        phase.setMultilevelProvider(buildMultiLevelProvider());
        phase.setThreadPoolExector(buildThreadPoolExecutor());
        phase.setMultithreaded(multithreaded);
        List<PhaseConfig> phaseConfigList_ = phaseConfigList;
        if (ConfigUtils.isEmptyCollection(phaseConfigList_)) {
            phaseConfigList_ = Arrays.asList(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig());
        }
        // I have to build them
        phase.setPhaseConfigList(phaseConfigList_);
        phase.setConfigPolicy(phaseConfigPolicy.createChildThreadConfigPolicy(ChildThreadType.PART_THREAD));
        
        EnvironmentMode environmentMode = phaseConfigPolicy.getEnvironmentMode();
        if (environmentMode.isNonIntrusiveFullAsserted()) {
            phase.setAssertStepScoreFromScratch(true);
        }
        if (environmentMode.isIntrusiveFastAsserted()) {
            phase.setAssertExpectedStepScore(true);
            phase.setAssertShadowVariablesAreNotStaleAfterStep(true);
        }
        return phase;
    }
       
    private MultiLevelProvider buildMultiLevelProvider() {
    	if (multiLevelProvider != null)
    		return multiLevelProvider;
    	
        if (multiLevelProviderClass != null) {
        	multiLevelProvider =ConfigUtils.newInstance(this, "mutliLevelProviderClass", multiLevelProviderClass); 
            return multiLevelProvider;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private ThreadPoolExecutor buildThreadPoolExecutor() {
        ThreadFactory threadFactory;
        if (threadFactoryClass != null) {
            threadFactory = ConfigUtils.newInstance(this, "threadFactoryClass", threadFactoryClass);
        } else {
            threadFactory = new DefaultSolverThreadFactory("PartThread");
        }
        // Based on Executors.newCachedThreadPool(...)
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                threadFactory);
    }

    private Integer resolvedActiveThreadCount() {
        int availableProcessorCount = Runtime.getRuntime().availableProcessors();
        Integer resolvedActiveThreadCount;
        if (runnablePartThreadLimit == null || runnablePartThreadLimit.equals(ACTIVE_THREAD_COUNT_AUTO)) {
            // Leave one for the Operating System and 1 for the solver thread, take the rest
            resolvedActiveThreadCount = availableProcessorCount <= 2 ? 1 : availableProcessorCount - 2;
        } else if (runnablePartThreadLimit.equals(ACTIVE_THREAD_COUNT_UNLIMITED)) {
            resolvedActiveThreadCount = null;
        } else {
            resolvedActiveThreadCount = ConfigUtils.resolveThreadPoolSizeScript(
                    "runnablePartThreadLimit", runnablePartThreadLimit, ACTIVE_THREAD_COUNT_AUTO, ACTIVE_THREAD_COUNT_UNLIMITED);
            if (resolvedActiveThreadCount < 1) {
                throw new IllegalArgumentException("The runnablePartThreadLimit (" + runnablePartThreadLimit
                        + ") resulted in a resolvedActiveThreadCount (" + resolvedActiveThreadCount
                        + ") that is lower than 1.");
            }
            if (resolvedActiveThreadCount > availableProcessorCount) {
                logger.debug("The resolvedActiveThreadCount (" + resolvedActiveThreadCount
                        + ") is higher than the availableProcessorCount (" + availableProcessorCount
                        + "), so the JVM will round-robin the CPU instead.");
            }
        }
        return resolvedActiveThreadCount;
    }

    @Override
    public void inherit(MultiLevelPhaseConfig inheritedConfig) {
        super.inherit(inheritedConfig);
        multiLevelProviderClass = ConfigUtils.inheritOverwritableProperty(multiLevelProviderClass,
                inheritedConfig.getMultiLevelProviderClass());
        threadFactoryClass = ConfigUtils.inheritOverwritableProperty(threadFactoryClass,
                inheritedConfig.getThreadFactoryClass());
        runnablePartThreadLimit = ConfigUtils.inheritOverwritableProperty(runnablePartThreadLimit,
                inheritedConfig.getRunnablePartThreadLimit());
        phaseConfigList = ConfigUtils.inheritMergeableListConfig(
                phaseConfigList, inheritedConfig.getPhaseConfigList());
    }

}