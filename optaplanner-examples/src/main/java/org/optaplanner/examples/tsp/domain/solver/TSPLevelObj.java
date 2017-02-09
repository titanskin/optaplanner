package org.optaplanner.examples.tsp.domain.solver;

import java.util.List;

import org.optaplanner.core.impl.multilevelphase.DefaultLevelObject;
import org.optaplanner.examples.tsp.domain.TspSolution;
import org.optaplanner.examples.tsp.domain.location.Location;

public class TSPLevelObj extends DefaultLevelObject<TspSolution>{

	private final int[] partitions;
	
	public TSPLevelObj(TspSolution coarseSolution, List<TspSolution> fragmentSolutions, int[] partitions) {
		super(coarseSolution, fragmentSolutions);
		this.partitions = partitions;
	}
	
	public int getPartitionIdOf(Location l) {
		int id = l.getId().intValue();
		return partitions[id];
	}		
}
