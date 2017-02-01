package org.optaplanner.core.impl.multilevelphase;

import java.util.Iterator;
import java.util.List;

import org.optaplanner.core.config.heuristic.selector.common.SelectionCacheType;
import org.optaplanner.core.impl.heuristic.selector.IterableSelector;
import org.optaplanner.core.impl.phase.scope.AbstractPhaseScope;
import org.optaplanner.core.impl.phase.scope.AbstractStepScope;
import org.optaplanner.core.impl.solver.scope.DefaultSolverScope;

public class MultiLevelSelector<Solution_> implements IterableSelector<Solution_>{

	List<Object> sorted;
    
	public MultiLevelSelector() {
		super();
	}

	@Override
	public boolean isCountable() {
		
		return true;
	}

	@Override
	public boolean isNeverEnding() {
		return false;
	}

	@Override
	public SelectionCacheType getCacheType() {
		return SelectionCacheType.PHASE;
	}

	@Override
	public void phaseStarted(AbstractPhaseScope phaseScope) {
		
	}

	@Override
	public void stepStarted(AbstractStepScope stepScope) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stepEnded(AbstractStepScope stepScope) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void phaseEnded(AbstractPhaseScope phaseScope) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void solvingStarted(DefaultSolverScope solverScope) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void solvingEnded(DefaultSolverScope solverScope) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Iterator<Solution_> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getSize() {
		// TODO Auto-generated method stub
		return 0;
	}


	



}
