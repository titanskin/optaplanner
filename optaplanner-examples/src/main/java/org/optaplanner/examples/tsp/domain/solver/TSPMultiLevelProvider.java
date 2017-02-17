
package org.optaplanner.examples.tsp.domain.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.optaplanner.core.impl.multilevelphase.MultiLevelProvider;
import org.optaplanner.core.impl.solver.scope.DefaultSolverScope;
import org.optaplanner.examples.tsp.domain.Domicile;
import org.optaplanner.examples.tsp.domain.Standstill;
import org.optaplanner.examples.tsp.domain.TspSolution;
import org.optaplanner.examples.tsp.domain.Visit;
import org.optaplanner.examples.tsp.domain.location.AirLocation;
import org.optaplanner.examples.tsp.domain.location.Location;

public class TSPMultiLevelProvider implements MultiLevelProvider<TspSolution, TSPLevelObj> {

	protected double contractionRate;
	protected int iterationCountOfRelax;
	protected double relaxSpringForce = 1.0;
	protected double relaxEpsilonToQuit = 1.0;
	protected int minSize;

	static class Triple<K, G, V> {
		K k;
		G g;
		V v;

		public Triple(K k, G g, V v) {
			super();
			this.k = k;
			this.g = g;
			this.v = v;
		}

	}

	public TSPMultiLevelProvider() { }

	@Override
	public void adjustFragments(TSPLevelObj levelObject, TspSolution coarseSolution, TspSolution fragment) {
		// TODO adjust fragments
		// since the coarse solution has some restrictions of begin and end, we
		// have to adjust each fragment.
		// in particular we have to perform that the first and last visit is
		// fixed. they shouldn't be moved.
		// => change the domain.

		int id = fragment.getId().intValue();
		// domicile isn't a standstill ...
		int prevId = getPrevStandstillPartition(levelObject, coarseSolution, id),
				nextId = getNextStandstillPartition(levelObject, coarseSolution, id);

		Tuple<Location, Location> prevTuple = lookForMinDist(levelObject.getPartitionedLocations(), prevId, id),
				nextTuple = lookForMinDist(levelObject.getPartitionedLocations(), id, nextId);

		// domicle is prevTuple.b
		// not movable is nextTuple.a
		List<Visit> visits = new ArrayList<>(fragment.getLocationList().size() - 1);

		for (Location l : fragment.getLocationList()) {
			if (l == prevTuple.b && fragment.getDomicile() == null) {
				// TODO here is the mistake ...there is no case diff iff which one is domicile
				fragment.setDomicile(new Domicile(l.getId(), l));
			} else if (l == nextTuple.a) {
				Visit v = new Visit(l.getId(), l);
				v.setMovable(false);
			} else {
				visits.add(new Visit(l.getId(), l));
			}
		}
		
		/*
		 * if (isDomicleSet && !domLoc.equals(l) && l == nextTuple.a) {
					Visit v = new Visit(l.getId(), l);
					v.setMovable(false);
				} else if (!isDomicleSet && !domLoc.equals(l) && l == nextTuple.a) {
					Visit v = new Visit(l.getId(), l);
					v.setMovable(false);
				} else if (isDomicleSet && !domLoc.equals(l) && l == prevTuple.b) {
					Visit v = new Visit(l.getId(), l);
					v.setMovable(false);
				} else if (!isDomicleSet && !domLoc.equals(l) && l == prevTuple.b) {
					fragment.setDomicile(new Domicile(l.getId(), l));
					isDomicleSet = true;
				} 
		 */
		fragment.setVisitList(visits);
//		System.out.println(id + ": prev=" + prevId + " next=" + nextId + " size=" + fragment.getLocationList().size()+ " vSize=" + fragment.getVisitList().size());
	}

	private Tuple<Location, Location> lookForMinDist(List<List<Location>> locations, int fragment1, int fragment2) {
		assert (fragment1 >= 0 && fragment1 < locations.size() && fragment2 >= 0 && fragment2 < locations.size()
				&& fragment1 != fragment2);
		long min = Long.MAX_VALUE;
		Tuple<Location, Location> ret = new Tuple<Location, Location>(null, null);
		for (Location l1 : locations.get(fragment1)) {
			for (Location l2 : locations.get(fragment2)) {
				if (l1.getDistanceTo(l2) < min) {
					ret.a = l1;
					ret.b = l2;
				}
			}
		}
		// nothing found
		return ret;
	}

	private int getPrevStandstillPartition(TSPLevelObj levelObj, TspSolution coarseSolution, int locId) {
		List<Location> orderedLocList = getCoarseOrderedList(levelObj, coarseSolution);
		int prevId = (locId - 1 + orderedLocList.size()) % orderedLocList.size();
		return orderedLocList.get(prevId).getId().intValue();
	}

	private int getNextStandstillPartition(TSPLevelObj obj, TspSolution coarseSolution, int locId) {
		List<Location> orderedLocList = getCoarseOrderedList(obj, coarseSolution);
		int prevId = (locId + 1) % orderedLocList.size();
		return orderedLocList.get(prevId).getId().intValue();
	}

	private synchronized List<Location> getCoarseOrderedList(TSPLevelObj obj, TspSolution coarseSolution) {
		// make other structure
		// FIXME asserted that index of locationlist equals the ids

		if (obj.getCoarseList() != null)
			return obj.getCoarseList();
		List<Visit> visits = coarseSolution.getVisitList();
		int[] nextLocation = new int[coarseSolution.getLocationList().size()];
		Arrays.fill(nextLocation, -1);
		int endIdx = -1, beginIdx = coarseSolution.getDomicile().getLocation().getId().intValue();
		for (int i = 0; i < visits.size(); ++i) {
			Visit visit = visits.get(i);

			if (visit.getPreviousStandstill() != null) {
				int idx = visit.getPreviousStandstill().getLocation().getId().intValue();
				// here is the fault...
				nextLocation[idx] = visit.getLocation().getId().intValue();
			}
		}
		for (int i = 0; i < nextLocation.length; i++) {
			if (nextLocation[i] == -1) {
				endIdx = i;
				break;
			}
		}
		List<Location> locList = new ArrayList<>(nextLocation.length);
		for (int idx = beginIdx; idx != endIdx; idx = nextLocation[idx]) {
			locList.add(coarseSolution.getLocationList().get(idx));
		}
		locList.add(coarseSolution.getLocationList().get(endIdx));
		obj.setCoarseList(locList);
		return locList;
	}

	@Override
	public TSPLevelObj createLevelObject(DefaultSolverScope<TspSolution> solverScope) {

		if (solverScope.getBestSolution().getLocationList().size() <= minSize) {
			return new TSPLevelObj(null, null, null, null);
		}

		List<TspSolution> fragments = new ArrayList<>();
		TspSolution coarse = null;

		long time = System.currentTimeMillis();

		TspSolution current = solverScope.getWorkingSolution();
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

		List<List<Location>> coarseLocationSet = new ArrayList<>(sizeCoarseLocations);
		for (int i = 0; i < sizeCoarseLocations; ++i) {
			coarseLocationSet.add(new ArrayList<>());
		}
		for (int i = 0; i < n; ++i) {
			coarseLocationSet.get(partitions[i]).add(locations.get(i));
		}

		// zeile stimmt nicht

		List<Location> coarseLocations = new ArrayList<>(coarseLocationSet.size());

		double[][] coarseLocationsDistances = new double[n][n];
		for (int i = 0; i < coarseLocationSet.size(); ++i) {
			for (int j = i + 1; j < coarseLocationSet.size(); ++j) {
				coarseLocationsDistances[i][j] = coarseLocationsDistances[j][i] = getShortestDistance(
						coarseLocationSet.get(i), coarseLocationSet.get(j));

				if (coarseLocationsDistances[i][j] < 1e-5)
					throw new IllegalStateException("too close");
			}
			coarseLocations.add(getAvgLocation(coarseLocationSet.get(i), i));
		}

		doRelaxation(coarseLocations, coarseLocationsDistances);

		// so for now...
		coarse = new TspSolution();
		coarse.setLocationList(coarseLocations);
		coarse.setName(current.getName() + "-c");
		{
			int domicleFragment = partitions[current.getDomicile().getLocation().getId().intValue()];
			Location l = coarseLocations.get(partitions[current.getDomicile().getLocation().getId().intValue()]);
			List<Visit> visitList = new ArrayList<>();
			for (Location location : coarseLocations) {
				if (l != location) {
					visitList.add(new Visit(location.getId(), location));
				}
			}
			long fragId = 0;
			for (List<Location> locationsSet : coarseLocationSet) {
				// how to?

				if (locationsSet.size() > 1) {
					TspSolution fragment = new TspSolution();

					fragment.setId(fragId);
					fragment.setName(current.getName() + "-f" + fragId);
					// don't do that
					fragment.setLocationList(locationsSet);
					if (fragId == domicleFragment) {
						// add domicle
//						Domicile currD = current.getDomicile();
//						fragment.setDomicile(new Domicile(currD.getLocation().getId(), currD.getLocation()));
					}
					
					
					fragments.add(fragment);
				}
				fragId++;
			}
			// generate fragments

			coarse.setVisitList(visitList);
			coarse.setDomicile(new Domicile(l.getId(), l));
		}
		System.out.println("#locations " + locations.size() + " -> " + coarse.getLocationList().size() + " @ "				+ (System.currentTimeMillis() - time) + "ms");

		return new TSPLevelObj(coarse, fragments, partitions, coarseLocationSet);
	}

	private double sq(double a) {
		return a * a;
	}

	private double dist(final double[][] xy, int idx_a, int idx_b) {
		return Math.sqrt(sq(xy[idx_a][0] - xy[idx_b][0]) + sq(xy[idx_a][1] - xy[idx_b][1]));
	}

	// TODO outsource code to a new class.
	private void relax(double[][] xy, final double[][] dist, final int n) {
		double[][] force = new double[n][2];
		double lastVal = Double.MAX_VALUE;
		for (int iter = 0; iter < 20; ++iter) {
			double val = 0;
			for (int i = 0; i < n; i++) {
				for (int j = i + 1; j < n; j++) {
					double rdist = dist(xy, i, j);

					double value = Math.log(rdist / dist[i][j]) / 2.0;
					if (Double.isFinite(value)) {

						force[i][0] += value * (xy[j][0] - xy[i][0]);
						force[i][1] += value * (xy[j][1] - xy[i][1]);

						force[j][0] += value * (xy[i][0] - xy[j][0]);
						force[j][1] += value * (xy[i][1] - xy[j][1]);
					} else {
						System.err.println(i + " " + j + " " + dist[i][j] + " " + rdist);
					}
					val += Math.abs(dist[i][j] - rdist);
				}
			}

			if (val < relaxEpsilonToQuit || lastVal - val < relaxEpsilonToQuit) {
				return;
			}

			for (int i = 0; i < n; i++) {
				xy[i][0] = xy[i][0] + relaxSpringForce * force[i][0];
				xy[i][1] = xy[i][1] + relaxSpringForce * force[i][1];

				force[i][0] = 0.0;
				force[i][1] = 0.0;
			}
			lastVal = val;
		}
	}

	private void doRelaxation(final List<Location> locations, final double[][] dist) {

		final int n = locations.size();
		double[][] xy = new double[n][2];

		for (int i = 0; i < n; i++) {
			Location l = locations.get(i);
			xy[i][0] = l.getLatitude();
			xy[i][1] = l.getLongitude();
		}

		relax(xy, dist, n);
		for (int i = 0; i < n; i++) {
			Location l = locations.get(i);
			l.setLatitude(xy[i][0]);
			l.setLongitude(xy[i][1]);
		}
	}

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

	private double getShortestDistance(List<Location> a, List<Location> b) {
		double result = Double.MAX_VALUE;
		for (Location l1 : a) {
			for (Location l2 : b) {
				result = Math.min(result, l1.getAirDistanceDoubleTo(l2));
			}
		}
		return result;
	}

	private void fillNextList(TspSolution solution, int[] next) {
		for (Visit v : solution.getVisitList()) {
			if (v.getPreviousStandstill() != null) {
				int prevIdx = v.getPreviousStandstill().getLocation().getId().intValue();
				next[prevIdx] = v.getLocation().getId().intValue();
			} else {
				System.out.println("no standstill");
			}
		}
	}
	
	private void fillPrevList(TspSolution solution, int[] prev) {
		for (Visit v : solution.getVisitList()) {
			prev[v.getLocation().getId().intValue()] =  v.getPreviousStandstill().getLocation().getId().intValue();
		}
	}	

	@Override
	public TspSolution extendSolution(TSPLevelObj levelObj, TspSolution coarseSolution, List<TspSolution> fragments,
			TspSolution current) {

		// how to do the mapping		
		
		int[] hasNext = new int[current.getLocationList().size()],
				prevs = new int[current.getLocationList().size()];		
		int[] lastOf = new int[levelObj.getPartitionedLocations().size()],
				beginOf = new int[lastOf.length];
		int[] partitions = levelObj.getPartitions();
		Arrays.fill(hasNext, -1);
		Arrays.fill(beginOf, -1);
		Arrays.fill(lastOf, -1);
		Arrays.fill(prevs, -1);
		for (TspSolution tspSolution : fragments) {
			// map which fragment it is in coarse solution
			fillNextList(tspSolution, hasNext);			
			fillPrevList(tspSolution, prevs);
			beginOf[tspSolution.getId().intValue()] = tspSolution.getDomicile().getLocation().getId().intValue();					
		}
		for (int i = 0; i < hasNext.length; i++) {
			if (hasNext[i] < 0) {
				lastOf[partitions[i]] = i;
			}
			
		}
		for (int i = 0; i < beginOf.length; i++) {
			if (beginOf[i] < 0) {
				beginOf[i] = levelObj.getPartitionedLocations().get(i).get(0).getId().intValue();
			}
		}
		
		// how do I get the previous location of a fragment?
		
		Standstill[] allStandStillOfCurr = new Standstill[current.getLocationList().size()];
		
		{
			for (Visit v : current.getVisitList()) {
				allStandStillOfCurr[v.getLocation().getId().intValue()] = v;
			}
			Domicile d = current.getDomicile();
			allStandStillOfCurr[d.getLocation().getId().intValue()] = d; 
		}
		for (Visit coarseVisit  : coarseSolution.getVisitList()) {

			int fragId = coarseVisit.getLocation().getId().intValue();
			int prevFragId = coarseVisit.getPreviousStandstill().getLocation().getId().intValue();

			prevs[beginOf[fragId]] = lastOf[prevFragId]; 
		}
		{
			int firstFragId = levelObj.getCoarseList().get(0).getId().intValue();
			int lastFragId = levelObj.getCoarseList().get(levelObj.getCoarseList().size() - 1).getId().intValue();
			
			prevs[beginOf[firstFragId]] = lastOf[lastFragId];
		}
		for (Visit v : current.getVisitList()) {
			Standstill prev = null;
			int index = v.getLocation().getId().intValue(),
					prevIdx = prevs[index];						
			prev = allStandStillOfCurr[prevIdx];
			v.setPreviousStandstill(prev);
		}
		
		return current;
		// TODO Auto-generated method stub

	}

	public void setDoContractUnderPercentage(double doContractUnderPercentage) {
		this.contractionRate = doContractUnderPercentage;
	}

	public double getDoContractUnderPercentage() {
		return contractionRate;
	}

}
