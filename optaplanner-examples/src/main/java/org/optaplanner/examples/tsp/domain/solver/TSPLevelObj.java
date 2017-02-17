package org.optaplanner.examples.tsp.domain.solver;

import java.util.List;

import org.optaplanner.core.impl.multilevelphase.DefaultLevelObject;
import org.optaplanner.examples.tsp.domain.TspSolution;
import org.optaplanner.examples.tsp.domain.location.Location;

public class TSPLevelObj extends DefaultLevelObject<TspSolution>{

	private final int[] partitions;
	private volatile List<Location> coarseList;
	private final List<List<Location>> partitionedLocations;
	public TSPLevelObj(TspSolution coarseSolution, 
			List<TspSolution> fragmentSolutions, 
			int[] partitions, 
			List<List<Location>> partLocs) {
		super(coarseSolution, fragmentSolutions);
		this.partitions = partitions;
		this.partitionedLocations = partLocs;
	}
	public List<List<Location>> getPartitionedLocations() {
		return partitionedLocations;
	}
	public int getPartitionIdOf(Location l) {
		int id = l.getId().intValue();
		return partitions[id];
	}		
	
	public void setCoarseList(List<Location> coarseList) {
		this.coarseList = coarseList;
	}
	
	public List<Location> getCoarseList() {
		return coarseList;
	}
	public int[] getPartitions() {
		return partitions;
	}
}
