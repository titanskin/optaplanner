package org.optaplanner.examples.tsp.domain.solver.filter;

import org.optaplanner.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import org.optaplanner.core.impl.heuristic.selector.move.generic.chained.SubChainChangeMove;
import org.optaplanner.core.impl.heuristic.selector.move.generic.chained.SubChainReversingChangeMove;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.examples.tsp.domain.Standstill;
import org.optaplanner.examples.tsp.domain.TspSolution;

public class TspSubChainMoveFilter implements SelectionFilter<TspSolution, Object>{

	@Override
	public boolean accept(ScoreDirector<TspSolution> scoreDirector, Object selection) {
		if (selection instanceof SubChainChangeMove) {
			SubChainChangeMove move = (SubChainChangeMove) selection;
			
			Standstill last = (Standstill)move.getSubChain().getLastEntity();
			
			return last.isMoveable();
		}
		
		if (selection instanceof SubChainReversingChangeMove) {
			SubChainReversingChangeMove move = (SubChainReversingChangeMove) selection;
		}
		return true;
	}

}
