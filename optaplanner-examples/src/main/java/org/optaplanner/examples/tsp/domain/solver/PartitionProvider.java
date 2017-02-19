package org.optaplanner.examples.tsp.domain.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.optaplanner.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.examples.tsp.domain.Domicile;
import org.optaplanner.examples.tsp.domain.TspSolution;
import org.optaplanner.examples.tsp.domain.Visit;
import org.optaplanner.examples.tsp.domain.location.AirLocation;
import org.optaplanner.examples.tsp.domain.location.Location;
import org.optaplanner.examples.tsp.domain.solver.TSPMultiLevelProvider.Triple;

public class PartitionProvider implements SolutionPartitioner<TspSolution> {

	protected double contractionRate = 0.2;

	private Location getAvgLocation(final List<Location> a, int id) {
		double x = 0, y = 0;
		for (Location location : a) {
			x += location.getLatitude();
			y += location.getLongitude();
		}
		x /= a.size();
		y /= a.size();

		return new AirLocation(id, x, y);
	}
	
	@Override
	public List<TspSolution> splitWorkingSolution(ScoreDirector<TspSolution> scoreDirector) {
		contractionRate = 0.2;
		List<TspSolution> fragments = new ArrayList<>();

		long time = System.currentTimeMillis();

		TspSolution current = scoreDirector.getWorkingSolution();
		List<Location> locations = new ArrayList<>(current.getLocationList());

		if (!locations.contains(current.getDomicile().getLocation()))
			locations.add(current.getDomicile().getLocation());

		UF unionFind = new UF(locations.size());

		final int n = locations.size();
		List<Triple<Double, Location, Location>> compareList = new ArrayList<>(n * n / 2 + 1);
		for (int i = 0; i < locations.size(); ++i) {
			for (int j = i + 1; j < locations.size(); ++j) {
				Location a = locations.get(i), b = locations.get(j);
				compareList.add(new Triple<Double, Location, Location>(a.getAirDistanceDoubleTo(b), a, b));
			}
			locations.get(i).setId((long) i);
		}

		// uses the most time!
		compareList.sort(new Comparator<Triple<Double, Location, Location>>() {
			@Override
			public int compare(Triple<Double, Location, Location> o1, Triple<Double, Location, Location> o2) {
				return Double.compare(o1.k, o2.k);
			}
		});
		for (int i = 0, j = 0; j < Math.ceil(n * contractionRate) + 1 && i < compareList.size(); ++i) {
			Location a = compareList.get(i).g, b = compareList.get(i).v;
			j += (unionFind.connected(a.getId().intValue(), b.getId().intValue()) ? 1 : 0);
			unionFind.union(a.getId().intValue(), b.getId().intValue());
		}

		int[] partitions = unionFind.compact();
		final int sizeCoarseLocations = unionFind.count();
		for (int i = 0; i < sizeCoarseLocations; i++) {
			fragments.add(new TspSolution());
		}

		for (int i = 0; i < partitions.length; i++) {
			TspSolution s = fragments.get(partitions[i]);
			Location loc = locations.get(i);
			s.getLocationList().add(loc);
			s.getVisitList().add(new Visit(i, loc));			
		}
		int ctr = locations.size();
		for (TspSolution tspSolution : fragments) {
			Location dom = getAvgLocation(tspSolution.getLocationList(), ctr);
			tspSolution.setDomicile(new Domicile(ctr, dom));
			tspSolution.getLocationList().add(dom);
			ctr++;
		}

		return fragments;
	}

}
