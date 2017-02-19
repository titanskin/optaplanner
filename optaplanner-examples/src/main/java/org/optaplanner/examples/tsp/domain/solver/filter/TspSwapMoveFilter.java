package org.optaplanner.examples.tsp.domain.solver.filter;

import org.optaplanner.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import org.optaplanner.core.impl.heuristic.selector.move.generic.SwapMove;
import org.optaplanner.core.impl.heuristic.selector.move.generic.chained.TailChainSwapMove;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.examples.tsp.domain.Standstill;
import org.optaplanner.examples.tsp.domain.TspSolution;

public class TspSwapMoveFilter implements SelectionFilter<TspSolution, TailChainSwapMove<TspSolution>> {

	@Override
	public boolean accept(ScoreDirector<TspSolution> scoreDirector, TailChainSwapMove<TspSolution> selection) {
		
		
		return true;
	}

	

}
