package org.optaplanner.examples.tsp.domain.solver.filter;

import org.optaplanner.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import org.optaplanner.core.impl.heuristic.selector.move.generic.chained.ChainedChangeMove;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.examples.tsp.domain.TspSolution;

public class TspChainMoveFilter implements SelectionFilter<TspSolution, ChainedChangeMove>{

	@Override
	public boolean accept(ScoreDirector<TspSolution> scoreDirector, ChainedChangeMove selection) {
		// TODO Auto-generated method stub		
		return true;
	}

}
