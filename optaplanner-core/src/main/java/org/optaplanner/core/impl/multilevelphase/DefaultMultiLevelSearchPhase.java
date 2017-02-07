/*
 * Created 2016 chris
 * christoph.hess@live.de
 */

package org.optaplanner.core.impl.multilevelphase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.config.heuristic.policy.HeuristicConfigPolicy;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.config.solver.recaller.BestSolutionRecallerConfig;
import org.optaplanner.core.impl.constructionheuristic.ConstructionHeuristicPhase;
import org.optaplanner.core.impl.partitionedsearch.PartitionSolver;
import org.optaplanner.core.impl.phase.AbstractPhase;
import org.optaplanner.core.impl.phase.Phase;
import org.optaplanner.core.impl.phase.event.PhaseLifecycleListener;
import org.optaplanner.core.impl.solver.ChildThreadType;
import org.optaplanner.core.impl.solver.recaller.BestSolutionRecaller;
import org.optaplanner.core.impl.solver.scope.DefaultSolverScope;
import org.optaplanner.core.impl.solver.termination.ChildThreadPlumbingTermination;
import org.optaplanner.core.impl.solver.termination.OrCompositeTermination;
import org.optaplanner.core.impl.solver.termination.Termination;

public class DefaultMultiLevelSearchPhase<Solution_, V extends LevelObject<Solution_>> extends AbstractPhase<Solution_>
		implements MultiLevelSearchPhase<Solution_>, PhaseLifecycleListener<Solution_> {

	/*
	 * What do I need? Two Modes: with ch and without
	 * 
	 * without ch: => the problem is constructed at an other location _ opt, -
	 * ch - \ _ \_/ \_/
	 * 
	 * with ch \ \ -_...\_/ \-_/
	 * 
	 * Interface has to provide - coarse - split - merge
	 */

//	protected ConstructionHeuristicPhase<Solution_> constructionHeuristic;
	boolean shouldFragmentsBeCoarsed = false;
	protected MultiLevelProvider<Solution_, V> multilevelProvider;
	protected PhaseLifecycleListener<Solution_> sorting; // ??
	protected Integer runnablePartThreadLimit;
	/**
	 * Recoarse and reopt in a difference in solution quality at least {@value}
	 */
	protected double recoarse;

	protected List<PhaseConfig> phaseConfigList;
	protected HeuristicConfigPolicy configPolicy;
	protected ThreadPoolExecutor threadPoolExector;

	private volatile List<Phase<Solution_>> phases;
	
//	public ConstructionHeuristicPhase<Solution_> getConstructionHeuristic() {
//		return constructionHeuristic;
//	}
//
//	public void setConstructionHeuristic(ConstructionHeuristicPhase<Solution_> constructionHeuristic) {
//		this.constructionHeuristic = constructionHeuristic;
//	}

	public List<PhaseConfig> getPhaseConfigList() {
		return phaseConfigList;
	}

	public void setPhaseConfigList(List<PhaseConfig> phaseConfigList) {
		this.phaseConfigList = phaseConfigList;
	}

	public void setRunnablePartThreadLimit(Integer runnablePartThreadLimit) {
		this.runnablePartThreadLimit = runnablePartThreadLimit;
	}

	public ThreadPoolExecutor getThreadPoolExector() {
		return threadPoolExector;
	}

	public void setThreadPoolExector(ThreadPoolExecutor threadPoolExector) {
		this.threadPoolExector = threadPoolExector;
	}

	public void setConfigPolicy(HeuristicConfigPolicy configPolicy) {
		this.configPolicy = configPolicy;
	}

	public HeuristicConfigPolicy getConfigPolicy() {
		return configPolicy;
	}

	public DefaultMultiLevelSearchPhase(int phaseIndex, String logIndentation,
			BestSolutionRecaller<Solution_> bestSolutionRecaller, Termination termination) {
		super(phaseIndex, logIndentation, bestSolutionRecaller, termination);
	}

	public MultiLevelProvider<Solution_, V> getMultilevelProvider() {
		return multilevelProvider;
	}

	public void setMultilevelProvider(MultiLevelProvider<Solution_, V> multilevelProvider) {
		this.multilevelProvider = multilevelProvider;
	}

	@Override
	public void solve(DefaultSolverScope<Solution_> currentScope) {		
				
		if (multilevelProvider == null)
			throw new IllegalStateException("No MultiLevelProvider");

		
		if (phases == null) {
			buildPhases();
		}
		
		LevelObject<Solution_> levelObj = multilevelProvider.createLevelObject(currentScope);

		// coarse
		final ChildThreadPlumbingTermination childThreadPlumbingTermination = new ChildThreadPlumbingTermination();
		final Solution_ coarseSolution;
		
		if (levelObj.getCoarseSolution() == null) {
			System.out.println("Coarsening done!");
			return;
		} else {
			Solver<Solution_> solver = buildCoarseSolver(childThreadPlumbingTermination, null,currentScope);		
			coarseSolution = solver.solve(levelObj.getCoarseSolution());			
		}
		
		// do fragments if there are no .. no can be executed

		List<Solution_> fragments;
		try {
			List<Callable<Solution_>> tasks = new ArrayList<>();
			for (Solution_ s : levelObj.getFragments()) {
				
				tasks.add(new Callable<Solution_>() {

					@Override
					public Solution_ call() throws Exception {
						multilevelProvider.adjustFragments(levelObj, coarseSolution, s);
						// adjust phases !
												
						// construction heuristic!!
						
						Solver<Solution_> solver;	
						if (shouldFragmentsBeCoarsed) {
							solver = buildCoarseSolver(childThreadPlumbingTermination, null,currentScope);
						} else {
							solver = buildPartSolver(childThreadPlumbingTermination, null, currentScope, phases);
						}
						
						Solution_ fragmentSolution =  solver.solve(levelObj.getCoarseSolution());
						return fragmentSolution;
					}
				});
			}
			tasks.clear();
			// invokeAll fragments for us until all submitted tasks in the call
			// complete

			System.out.println("tasksize=" + tasks.size() + " @ " + coarseSolution);
			List<Future<Solution_>> result = threadPoolExector.invokeAll(tasks);
			System.out.println(threadPoolExector.isTerminated());
			fragments = new ArrayList<>(result.size());
			for (Future<Solution_> future : result) {
				if (future.isCancelled()) {
					throw new IllegalStateException("A fragments has been cancelled!");
				}
				try {
					fragments.add(future.get());
				} catch (ExecutionException e) {
					e.printStackTrace();
					throw new IllegalStateException(e.getCause());
				}
			}
		} catch (InterruptedException e) {
			throw new IllegalStateException("Process is interrupted!");
		}

		// now do a merge/extend solition with the precomputed
		/**
		 * Problem: - no possibility to consider only the cut => there is maybe
		 * performance loss
		 */

		// coarse solution and fragments

		Solution_ mergedSolution = multilevelProvider.extendSolution(levelObj, coarseSolution, fragments,
				currentScope.getWorkingSolution());
 
		// how to set merged?
		if (currentScope.isBestSolutionInitialized()) {
			System.err.println("oO");
		} else {
			
			System.out.println("got initialized solution");
			System.out.println(phases);
		}

		solvePhases(currentScope);

	}
	
	private void buildPhases() {
		Termination termination = new OrCompositeTermination();

		BestSolutionRecaller<Solution_> bestSolutionRecaller = new BestSolutionRecallerConfig()
				.buildBestSolutionRecaller(configPolicy.getEnvironmentMode());
		
		phases = new ArrayList<>();
		for (int index = 0; index < phaseConfigList.size(); ++index) {
			PhaseConfig phaseConfig = phaseConfigList.get(index);
			Phase<Solution_> phase = phaseConfig.buildPhase(index, configPolicy, bestSolutionRecaller, termination);
			// TODO ist nicht ganz korrekt... aber läuft vorerst!
			phase.setSolverPhaseLifecycleSupport(phaseLifecycleSupport);
 			phases.add(phase);
			System.out.println(index + " " + phase);
		}
	}

	
	private void solvePhases(DefaultSolverScope<Solution_> solverScope) {
		for (int i = 0; i < phases.size(); ++i) {
			Phase<Solution_> phase = phases.get(i);			
			phase.solve(solverScope);
		}
	}

	@Override
	public String getPhaseTypeString() {
		return "Multi-Level Search";
	}

	public PartitionSolver<Solution_> buildPartSolver(
            ChildThreadPlumbingTermination childThreadPlumbingTermination, Semaphore runnablePartThreadSemaphore,
            DefaultSolverScope<Solution_> solverScope, List<Phase<Solution_>> phases) {
        Termination partTermination = new OrCompositeTermination(childThreadPlumbingTermination,
                termination.createChildThreadTermination(solverScope, ChildThreadType.PART_THREAD));
        BestSolutionRecaller<Solution_> bestSolutionRecaller = new BestSolutionRecallerConfig()
                .buildBestSolutionRecaller(configPolicy.getEnvironmentMode());
        
        // TODO create PartitionSolverScope alternative to deal with 3 layer terminations
        DefaultSolverScope<Solution_> partSolverScope = solverScope.createChildThreadSolverScope(ChildThreadType.PART_THREAD);
        partSolverScope.setRunnableThreadSemaphore(runnablePartThreadSemaphore);
        return new PartitionSolver<>(partTermination, bestSolutionRecaller, phases, partSolverScope);
    }
	
	public PartitionSolver<Solution_> buildCoarseSolver(
            ChildThreadPlumbingTermination childThreadPlumbingTermination, Semaphore runnablePartThreadSemaphore,
            DefaultSolverScope<Solution_> solverScope) {
        Termination partTermination = new OrCompositeTermination(childThreadPlumbingTermination,
                termination.createChildThreadTermination(solverScope, ChildThreadType.PART_THREAD));
        BestSolutionRecaller<Solution_> bestSolutionRecaller = new BestSolutionRecallerConfig()
                .buildBestSolutionRecaller(configPolicy.getEnvironmentMode());
        List<Phase<Solution_>> phaseList = new ArrayList<>();
        phaseList.add(this);        
        // TODO create PartitionSolverScope alternative to deal with 3 layer terminations
        DefaultSolverScope<Solution_> partSolverScope = solverScope.createChildThreadSolverScope(ChildThreadType.PART_THREAD);
        partSolverScope.setRunnableThreadSemaphore(runnablePartThreadSemaphore);
        return new PartitionSolver<>(partTermination, bestSolutionRecaller, phaseList, partSolverScope);
    }
	
}
