package org.optaplanner.core.impl.multilevelphase;

import java.util.List;

public interface LevelObject<Solution_> {
	public Solution_ getCoarseSolution();
	public List<Solution_> getFragments();	
}
