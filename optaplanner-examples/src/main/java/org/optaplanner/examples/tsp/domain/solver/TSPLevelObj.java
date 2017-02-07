package org.optaplanner.examples.tsp.domain.solver;

import java.util.List;

import org.optaplanner.core.impl.multilevelphase.DefaultLevelObject;
import org.optaplanner.examples.tsp.domain.TspSolution;

public class TSPLevelObj extends DefaultLevelObject<TspSolution>{

	public TSPLevelObj(TspSolution coarseSolution, List<TspSolution> fragmentSolutions) {
		super(coarseSolution, fragmentSolutions);
		// TODO Auto-generated constructor stub
	}
	
	
}
