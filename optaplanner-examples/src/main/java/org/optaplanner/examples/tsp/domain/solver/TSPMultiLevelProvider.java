package org.optaplanner.examples.tsp.domain.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.optaplanner.core.impl.multilevelphase.DefaultLevelObject;
import org.optaplanner.core.impl.multilevelphase.LevelObject;
import org.optaplanner.core.impl.multilevelphase.MultiLevelProvider;
import org.optaplanner.core.impl.solver.scope.DefaultSolverScope;
import org.optaplanner.examples.tsp.domain.Domicile;
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
	
	static class Triple<K,G,V> {
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

	
	public TSPMultiLevelProvider() {
		
	}

	@Override
	public void adjustFragments(LevelObject<TspSolution> levelObject, TspSolution coarseSolution,
			TspSolution fragment) {
		// TODO adjust fragments
		// since the coarse solution has some restrictions of begin and end, we
		// have to adjust each fragment.
		// in particular we have to perform that the first and last visit is
		// fixed. they shouldn't be moved.
		// => change the domain.

		
		
	}

	@Override
	public LevelObject<TspSolution> createLevelObject(DefaultSolverScope<TspSolution> solverScope) {
		
		if (solverScope.getBestSolution().getLocationList().size() <= minSize) {
			return new DefaultLevelObject<TspSolution>(null, null);
		}
		
		List<TspSolution> fragments = new ArrayList<>();
		TspSolution coarse = null;

		long time = System.currentTimeMillis();

//		System.out.println(contractionRate + " " + iterationCountOfRelax + " " + relaxEpsilonToQuit + " " + relaxSpringForce);

		TspSolution current = solverScope.getWorkingSolution();		
		List<Location> locations = new ArrayList<>(current.getLocationList());

		if (!locations.contains(current.getDomicile().getLocation()))
			locations.add(current.getDomicile().getLocation());

		

		UF unionFind = new UF(locations.size());

		final int n = locations.size();
		List<Triple<Double, Location, Location>> compareList = new ArrayList<>(n * n / 2 + 1);
		for (int i = 0; i < locations.size(); ++i) {
			for (int j = i + 1; j < locations.size(); ++j) {
				Location a = locations.get(i),
						b = locations.get(j);
				compareList.add(new Triple<Double, Location, Location>(a.getAirDistanceDoubleTo(b), a, b));
			}
			locations.get(i).setId((long) i);
		}
		
		// uses the most time!
		compareList.sort(new Comparator<Triple<Double, Location, Location>>() {
			@Override
			public int compare(
					Triple<Double, Location, Location> o1,
					Triple<Double, Location, Location> o2) {
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
		coarse.setName("TspSolution-Coarse");
		{
			Location l = coarseLocations.get(partitions[current.getDomicile().getLocation().getId().intValue()]);
			List<Visit> visitList = new ArrayList<>();
			for (Location location : coarseLocations) {
				if (l != location) {
					visitList.add(new Visit(location));
				}
				
			}
			long fragId = 0;
			for (List<Location> locationsSet : coarseLocationSet) {
				// how to?
				TspSolution fragment =new TspSolution();
				fragment.setId(fragId);
				Location domicileLoc = coarseLocations.get((int)fragId);
				fragment.setLocationList(locationsSet);
				fragment.getLocationList().add(domicileLoc);
				fragment.setVisitList(new ArrayList<>(locationsSet.size()));
				// TODO
				for (Location location : locationsSet) {
					fragment.getVisitList().add(new Visit(location));
				}
				fragment.setDomicile(new Domicile(domicileLoc));
				fragments.add(fragment);
				fragId++;
			}
			// generate fragments
			
			coarse.setVisitList(visitList);
			coarse.setDomicile(new Domicile(l));
		}
		System.out.println("#locations " + locations.size() + " -> "+ coarse.getLocationList().size() + " @ " + (System.currentTimeMillis() - time) + "ms");
		
		
		
		
		return new DefaultLevelObject<TspSolution>(coarse, fragments);
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
					// System.out.println(Math.abs(dist[i][j] - rdist)+ " " +
					// Arrays.toString(xy[i]) + " " + Arrays.toString(xy[j]) + "
					// " +dist[i][j]);
					val += Math.abs(dist[i][j] - rdist);
				}
			}

			if (val < relaxEpsilonToQuit || lastVal - val < relaxEpsilonToQuit) {
//				System.out.println(iter + " " + val);
				return;
			}

			for (int i = 0; i < n; i++) {
				xy[i][0] = xy[i][0] + relaxSpringForce * force[i][0];
				xy[i][1] = xy[i][1] + relaxSpringForce * force[i][1];

				// System.out.println(Arrays.toString(force[i]));
				force[i][0] = 0.0;
				force[i][1] = 0.0;
			}
			lastVal = val;
//			System.out.println(iter + " " + val);
		}
	}

	private void doRelaxation(final List<Location> locations, final double[][] dist) {

		final int n = locations.size();
		double[][] xy = new double[n][2];

		for (int i = 0; i < n; i++) {
			Location l = locations.get(i);
			xy[i][0] = l.getLatitude();
			xy[i][1] = l.getLongitude();
			// System.out.println(i + Arrays.toString(xy[i]));
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

		return new AirLocation(-1, x, y);
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

	@Override
	public TspSolution extendSolution(LevelObject<TspSolution> levelObj, TspSolution coarseScope,
			List<TspSolution> fragments, TspSolution current) {
		return null;
		// TODO Auto-generated method stub

	}

	public void setDoContractUnderPercentage(double doContractUnderPercentage) {
		this.contractionRate = doContractUnderPercentage;
	}

	public double getDoContractUnderPercentage() {
		return contractionRate;
	}

}
