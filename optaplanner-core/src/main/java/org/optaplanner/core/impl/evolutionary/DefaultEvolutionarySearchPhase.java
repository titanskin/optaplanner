package org.optaplanner.core.impl.evolutionary;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import org.optaplanner.core.config.heuristic.policy.HeuristicConfigPolicy;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchPhaseScope;
import org.optaplanner.core.impl.phase.AbstractPhase;
import org.optaplanner.core.impl.phase.Phase;
import org.optaplanner.core.impl.solver.recaller.BestSolutionRecaller;
import org.optaplanner.core.impl.solver.scope.DefaultSolverScope;
import org.optaplanner.core.impl.solver.termination.Termination;

public class DefaultEvolutionarySearchPhase<Solution_> extends AbstractPhase<Solution_>{

	protected int parentSize, mutationSize;	

	protected List<PhaseConfig> phaseConfigList;
	protected HeuristicConfigPolicy configPolicy;
	
	
	protected Phase<Solution_> mutator;
	
	protected ThreadPoolExecutor threadPoolExector;
	
	public DefaultEvolutionarySearchPhase(int phaseIndex, String logIndentation,
			BestSolutionRecaller<Solution_> bestSolutionRecaller, Termination termination) {
		super(phaseIndex, logIndentation, bestSolutionRecaller, termination);
		// TODO Auto-generated constructor stub
	}

	
	@Override
	public void solve(DefaultSolverScope<Solution_> solverScope) {
	
		LocalSearchPhaseScope<Solution_> phaseScope = new LocalSearchPhaseScope<>(solverScope);
        phaseStarted(phaseScope);
        
        // store parentSize clones                
        
		while (termination.isPhaseTerminated(phaseScope)) {
			
			
			
//			threadPoolExector.invokeAll()
			
			// build up mutations
			
			// and r
			
		}
		
	}

	@Override
	public String getPhaseTypeString() {
		// TODO Auto-generated method stub
		return "Evolutionary";
	}

}
