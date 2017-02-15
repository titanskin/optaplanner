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
import org.optaplanner.core.impl.partitionedsearch.PartitionSolver;
import org.optaplanner.core.impl.phase.AbstractPhase;
import org.optaplanner.core.impl.phase.Phase;
import org.optaplanner.core.impl.phase.event.PhaseLifecycleListener;
import org.optaplanner.core.impl.score.director.AbstractScoreDirector;
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

	// protected ConstructionHeuristicPhase<Solution_> constructionHeuristic;
	public final static boolean multithreaded = false;
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
		solvingStarted(currentScope);
		if (multilevelProvider == null)
			throw new IllegalStateException("No MultiLevelProvider");

		if (phases == null) {
			buildPhases();
		}
		
		V levelObj = multilevelProvider.createLevelObject(currentScope);
 
		// TODO trace in debug - time of creating levelObj
		if (levelObj == null) {
			throw new IllegalStateException("Level object is null!");
		}
//		logger.debug("Created levelObj in ({}) ms. Fragment size is  ({}). ", currentScope.calculateTimeMillisSpentUpToNow(), levelObj.getFragments().size());
		
		final ChildThreadPlumbingTermination childThreadPlumbingTermination = new ChildThreadPlumbingTermination();		

		// build coarse solution and recurse with it 
		final Solution_ coarseSolution;
		if (levelObj.getCoarseSolution() == null) {
			System.out.println("Coarsening done!");
			solvePhases(currentScope);
			
			
			// but solving??
			return;
		} else {
			Solver<Solution_> solver = buildSolver(childThreadPlumbingTermination, null, currentScope, true);
			coarseSolution = solver.solve(levelObj.getCoarseSolution());
			// TODO hier ist der fehler! coarseSolution ist nicht initialisiert!
			System.out.println("solving done");
		}

		// build and solve fragments
		List<Solution_> fragments = new ArrayList<>();
		{
			List<Callable<Solution_>> tasks = new ArrayList<>();
			for (Solution_ s : levelObj.getFragments()) {

				tasks.add(new Callable<Solution_>() {

					@Override
					public Solution_ call() throws Exception {
						multilevelProvider.adjustFragments(levelObj, coarseSolution, s);
						// adjust phases !
						Solver<Solution_> solver = buildSolver(childThreadPlumbingTermination, null, currentScope,
								shouldFragmentsBeCoarsed);
						Solution_ fragmentSolution = solver.solve(s);
						return fragmentSolution;
					}
				});
			}
			
						
			if (multithreaded) {
				try {
					List<Future<Solution_>> result = threadPoolExector.invokeAll(tasks);

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

			} else {
				for (Callable<Solution_> callable : tasks) {
					try {
						fragments.add(callable.call());
					} catch (Exception e) {
						e.printStackTrace();
						break;
					}
				}
			}
		}

		// merge solution
		Solution_ mergedSolution = multilevelProvider.extendSolution(levelObj, coarseSolution, fragments,
				currentScope.getWorkingSolution());
		if (mergedSolution == null) {
			throw new IllegalStateException("Merged solution is null!");
		}
		Solver<Solution_> solver = buildSolver(childThreadPlumbingTermination, null, currentScope, false);
		mergedSolution = solver.solve(mergedSolution);
		currentScope.setBestSolution(mergedSolution);
		currentScope.setBestScore(solver.getBestScore());
		currentScope.setWorkingSolutionFromBestSolution();

	}

	private void buildPhases() {
		phases = new ArrayList<>();
		for (int index = 0; index < phaseConfigList.size(); ++index) {
			PhaseConfig phaseConfig = phaseConfigList.get(index);
			Phase<Solution_> phase = phaseConfig.buildPhase(index, configPolicy, this.bestSolutionRecaller,
					this.termination);
			// TODO ist nicht ganz korrekt... aber läuft vorerst!
			phase.setSolverPhaseLifecycleSupport(phaseLifecycleSupport);
			phases.add(phase);
			System.out.println(index + " " + phase);
		}
	}

	private void solvePhases(DefaultSolverScope<Solution_> solverScope) {
		for (int i = 0; i < phases.size(); ++i) {
			Phase<Solution_> phase = phases.get(i);
			phase.solvingStarted(solverScope);
			phase.solve(solverScope);
			phase.solvingEnded(solverScope);
		}
	}

	@Override
	public String getPhaseTypeString() {
		return "Multi-Level Search";
	}

	public PartitionSolver<Solution_> buildSolver(ChildThreadPlumbingTermination childThreadPlumbingTermination,
			Semaphore runnablePartThreadSemaphore, DefaultSolverScope<Solution_> solverScope, boolean multiLevel) {

		// fix it.
		// change parameter.
		Termination partTermination = new OrCompositeTermination(childThreadPlumbingTermination,
				termination.createChildThreadTermination(solverScope, ChildThreadType.PART_THREAD));

		BestSolutionRecaller<Solution_> bestSolutionRecaller = new BestSolutionRecallerConfig()
				.buildBestSolutionRecaller(configPolicy.getEnvironmentMode());

		List<Phase<Solution_>> phaseList = new ArrayList<>();
		if (multiLevel) {
			DefaultMultiLevelSearchPhase<Solution_, V> multiLevelPhase = new DefaultMultiLevelSearchPhase<>(0,
					this.logIndentation, bestSolutionRecaller, partTermination);
			multiLevelPhase.setMultilevelProvider(this.multilevelProvider);
			multiLevelPhase.setPhaseConfigList(phaseConfigList);
			multiLevelPhase.setThreadPoolExector(threadPoolExector);
			multiLevelPhase.setConfigPolicy(configPolicy);
			// TODO some more work ... thread...
			phaseList.add(multiLevelPhase);
		} else {
			for (int index = 0; index < phaseConfigList.size(); ++index) {
				PhaseConfig phaseConfig = phaseConfigList.get(index);
				Phase<Solution_> phase = phaseConfig.buildPhase(index,
						configPolicy.createChildThreadConfigPolicy(ChildThreadType.PART_THREAD), bestSolutionRecaller,
						partTermination);
				// TODO ist nicht ganz korrekt... aber läuft vorerst!
				phase.setSolverPhaseLifecycleSupport(phaseLifecycleSupport);
				phaseList.add(phase);
			}
		}

		// TODO create PartitionSolverScope alternative to deal with 3 layer
		// terminations
		DefaultSolverScope<Solution_> partSolverScope = solverScope
				.createChildThreadSolverScope(ChildThreadType.PART_THREAD);
		partSolverScope.setRunnableThreadSemaphore(runnablePartThreadSemaphore);
		partSolverScope.getScoreDirector().dispose();
		((AbstractScoreDirector) partSolverScope.getScoreDirector()).triggerVariableListeners();

		return new PartitionSolver<>(partTermination, bestSolutionRecaller, phaseList, partSolverScope);
	}

}
