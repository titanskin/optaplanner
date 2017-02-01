package org.optaplanner.examples.tsp.domain.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

import org.optaplanner.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.examples.tsp.domain.Domicile;
import org.optaplanner.examples.tsp.domain.TspSolution;
import org.optaplanner.examples.tsp.domain.Visit;
import org.optaplanner.examples.tsp.domain.location.Location;


public class TSPPartitioner implements SolutionPartitioner<TspSolution>{	

	static class Triple<A,B,C>  {
		A a;
		B b;
		C c;
		
		public Triple(A a, B b, C c) {
			this.a = a;
			this.b = b;
			this.c = c;
		}
	}
	
	@Override
	public List<TspSolution> splitWorkingSolution(ScoreDirector<TspSolution> scoreDirector) {
		TspSolution solution = scoreDirector.getWorkingSolution();
				
		List<Location> locs = solution.getLocationList();
		List<Visit> visits = solution.getVisitList();
		int[] set  = new int[locs.size()];
		for (int i = 0; i < set.length; ++i) {
			set[i] = -1;
		}
		
		List<Triple<Integer,Integer,Long>> distsList= new ArrayList<>();
		long[][] dists = new long[locs.size()][locs.size()];
		for (int i = 0, k = 0; i < locs.size(); ++i) {			
			for (int j = i+1; j < locs.size(); ++j) {
				long dist = locs.get(i).getDistanceTo(locs.get(j));
				dists[i][j] = dist;
				distsList.add(new Triple(i, j, dist));
			}			
		}
		
		distsList.sort(new Comparator<Triple<Integer, Integer, Long>>() {

			@Override
			public int compare(Triple<Integer, Integer, Long> o1, Triple<Integer, Integer, Long> o2) {
				return Long.compare(o1.c, o2.c);
			}
		});
		
		ArrayList<TspSolution> list = new ArrayList<>();

		for (int i = 0, solIdx = 0; i < distsList.size(); ++i) {
			Triple<Integer, Integer, Long> item = distsList.get(i);
			int a = item.a, b = item.b;
			if (set[a] < 0 && set[b] < 0) {
				TspSolution sln = new TspSolution();
				assert (locs.get(a) != null);
				assert (locs.get(b) != null);
				sln.getLocationList().add(locs.get(a));
				sln.getLocationList().add(locs.get(b));
				set[a] = solIdx;
				set[b] = solIdx;
				sln.setDomicile(solution.getDomicile());				
				list.add(sln);				
				solIdx++;
			} else if (set[a] >= 0 && set[b] < 0) {
				TspSolution sln = list.get(set[a]);
				sln.getLocationList().add(locs.get(b));
				set[b] = set[a];				
			} else if (set[a] < 0 && set[b] >= 0) {
				TspSolution sln = list.get(set[b]);
				sln.getLocationList().add(locs.get(a));
				set[a] = set[b];
			} else {
				// both
			}
		}		
		for (Visit v : visits) {
			
			
		}
		
		System.out.println(list.size());
		System.out.println(Arrays.toString(set));
		return list;
	}

}
