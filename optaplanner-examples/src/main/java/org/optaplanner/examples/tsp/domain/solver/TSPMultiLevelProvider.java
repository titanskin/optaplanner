package org.optaplanner.examples.tsp.domain.solver;

import java.util.List;

import org.optaplanner.core.impl.multilevelphase.LevelObject;
import org.optaplanner.core.impl.multilevelphase.MultiLevelProvider;
import org.optaplanner.core.impl.solver.scope.DefaultSolverScope;
import org.optaplanner.examples.tsp.domain.TspSolution;

public class TSPMultiLevelProvider implements MultiLevelProvider<TspSolution, LevelObject<TspSolution>>{

	@Override
	public void adjustFragments(LevelObject<TspSolution> levelObject, TspSolution coarseSolution) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public LevelObject<TspSolution> coarse(DefaultSolverScope<TspSolution> solverScope) {
		
		return null;
	}

	@Override
	public TspSolution extendSolution(LevelObject<TspSolution> levelObj, DefaultSolverScope<TspSolution> coarseScope,
			List<DefaultSolverScope<TspSolution>> fragments, DefaultSolverScope<TspSolution> scope) {
				return null;
		// TODO Auto-generated method stub
		
	}

}
