package org.optaplanner.examples.tsp.domain.solver.filter;

import org.optaplanner.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import org.optaplanner.core.impl.heuristic.selector.move.generic.ChangeMove;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.examples.tsp.domain.Standstill;
import org.optaplanner.examples.tsp.domain.TspSolution;

public class TspChangeMoveFilter implements SelectionFilter<TspSolution, ChangeMove>{

	@Override
	public boolean accept(ScoreDirector<TspSolution> scoreDirector, ChangeMove selection) {
		// TODO Auto-generated method stub
		Standstill source = (Standstill)selection.getEntity();
		Standstill target = (Standstill) selection.getToPlanningValue();
		
		
		return source.isMoveable();
	}

}
