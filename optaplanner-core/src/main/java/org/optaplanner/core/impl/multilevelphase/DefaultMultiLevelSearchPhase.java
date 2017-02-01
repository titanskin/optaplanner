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
import java.util.concurrent.ThreadPoolExecutor;

import org.optaplanner.core.config.heuristic.policy.HeuristicConfigPolicy;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.config.solver.recaller.BestSolutionRecallerConfig;
import org.optaplanner.core.impl.constructionheuristic.ConstructionHeuristicPhase;
import org.optaplanner.core.impl.phase.AbstractPhase;
import org.optaplanner.core.impl.phase.Phase;
import org.optaplanner.core.impl.phase.event.PhaseLifecycleListener;
import org.optaplanner.core.impl.solver.ChildThreadType;
import org.optaplanner.core.impl.solver.recaller.BestSolutionRecaller;
import org.optaplanner.core.impl.solver.scope.DefaultSolverScope;
import org.optaplanner.core.impl.solver.termination.OrCompositeTermination;
import org.optaplanner.core.impl.solver.termination.Termination;

public class DefaultMultiLevelSearchPhase<Solution_, V extends LevelObject<Solution_>> extends AbstractPhase<Solution_>
		implements MultiLevelSearchPhase<Solution_> {

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

	protected ConstructionHeuristicPhase<Solution_> constructionHeuristic;

	boolean shouldFragmentsBeCoarsed = false;	
	public ConstructionHeuristicPhase<Solution_> getConstructionHeuristic() {
		return constructionHeuristic;
	}

	public void setConstructionHeuristic(ConstructionHeuristicPhase<Solution_> constructionHeuristic) {
		this.constructionHeuristic = constructionHeuristic;
	}

	public List<PhaseConfig> getPhaseConfigList() {
		return phaseConfigList;
	}

	public void setPhaseConfigList(List<PhaseConfig> phaseConfigList) {
		this.phaseConfigList = phaseConfigList;
	}

	protected ThreadPoolExecutor threadPoolExector;

	public ThreadPoolExecutor getThreadPoolExector() {
		return threadPoolExector;
	}

	public void setThreadPoolExector(ThreadPoolExecutor threadPoolExector) {
		this.threadPoolExector = threadPoolExector;
	}

	protected MultiLevelProvider<Solution_, V> multilevelProvider;

	protected PhaseLifecycleListener<Solution_> sorting; // ??

	/**
	 * Recoarse and reopt in a difference in solution quality at least {@value}
	 */
	protected double recoarse;

	protected List<PhaseConfig> phaseConfigList;
	protected HeuristicConfigPolicy configPolicy;

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
	public void solve(DefaultSolverScope<Solution_> solverScope) {

		if (multilevelProvider == null)
			throw new IllegalStateException("No MultiLevelProvider");
		// copied
		// TODO change
		Termination termination = new OrCompositeTermination();

		BestSolutionRecaller<Solution_> bestSolutionRecaller = new BestSolutionRecallerConfig()
				.buildBestSolutionRecaller(configPolicy.getEnvironmentMode());

		List<Phase<Solution_>> phases = new ArrayList<>(phaseConfigList.size());

		for (int index = 0; index < phaseConfigList.size(); ++index) {
			PhaseConfig phaseConfig = phaseConfigList.get(index);
			phases.set(index, phaseConfig.buildPhase(index, configPolicy, bestSolutionRecaller, termination));
		}

		solveMultiLevel(solverScope, phases);

	}

	private void solveMultiLevel(DefaultSolverScope<Solution_> scope, List<Phase<Solution_>> phases) {

		// get the level object. in this model there is no possibility to have a
		// multilevelobj:
		// due to a graph partitioning there could be the case, in each level
		// only one edge will be contracted.
		// this level paradigma could be modelled as a list of edges, calculated
		// at the beginning of the phase
		LevelObject<Solution_> levelObj = multilevelProvider.coarse(scope);

		// coarse
		DefaultSolverScope<Solution_> coarseScope = null;
		if (levelObj.getCoarseSolution() == null) {
			return;
		} else {
			coarseScope = scope.createChildThreadSolverScope(ChildThreadType.PART_THREAD);			
			coarseScope.setBestSolution(levelObj.getCoarseSolution());
			
			
			solveMultiLevel(coarseScope, phases);
		}
		assert (coarseScope.isBestSolutionInitialized());
		
		// do fragments if there are no .. no can be executed
		
		List<DefaultSolverScope<Solution_>> fragments = null;
		try {
			List<Callable<DefaultSolverScope<Solution_>>> tasks = new ArrayList<>();
			for (Solution_ s : levelObj.getFragments()) {

				tasks.add(new Callable<DefaultSolverScope<Solution_>>() {

					@Override
					public DefaultSolverScope<Solution_> call() throws Exception {
						DefaultSolverScope<Solution_> fragmentScope = scope
								.createChildThreadSolverScope(ChildThreadType.PART_THREAD);
						fragmentScope.setBestSolution(s);
						fragmentScope.setWorkingSolutionFromBestSolution();
						
						// construction heuristic!!
						if (constructionHeuristic != null && fragmentScope.isBestSolutionInitialized()) 
							constructionHeuristic.solve(fragmentScope);
						
						if (shouldFragmentsBeCoarsed) {
							solveMultiLevel(fragmentScope, phases);
						} else {
							solvePhases(fragmentScope, phases);
						}
						return fragmentScope;
					}
				});
			}

			// invokeAll fragments for us until all submitted tasks in the call
			// complete
			List<Future<DefaultSolverScope<Solution_>>> result = threadPoolExector.invokeAll(tasks);
			fragments = new ArrayList<>(result.size());			
			for (Future<DefaultSolverScope<Solution_>> future : result) {
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

		Solution_ solution = multilevelProvider.extendSolution(levelObj, coarseScope, fragments, scope);		
		DefaultSolverScope<Solution_> mergedScope = scope.createChildThreadSolverScope(ChildThreadType.MOVE_THREAD);
		mergedScope.setBestSolution(solution);
		mergedScope.setWorkingSolutionFromBestSolution();
		mergedScope.calculateScore();
		assert (mergedScope.isBestSolutionInitialized());
		solvePhases(mergedScope, phases);
				
		
		if (!scope.isBestSolutionInitialized() || scope.getBestScore().compareTo(mergedScope) <= 0){
			scope.setBestSolution(mergedScope.getBestSolution());
			scope.setBestScore(mergedScope.getBestScore());
		}
	}
	
	private void solvePhases(DefaultSolverScope<Solution_> solverScope, List<Phase<Solution_>> phases) {
		for (int i = 0; i < phases.size(); ++i) {
			Phase phase = phases.get(i);
			phase.solve(solverScope);
		}
	}

	@Override
	public String getPhaseTypeString() {
		return "Multi-Level Search";
	}

}
