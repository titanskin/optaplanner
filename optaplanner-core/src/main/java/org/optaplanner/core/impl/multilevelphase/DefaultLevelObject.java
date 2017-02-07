package org.optaplanner.core.impl.multilevelphase;

import java.util.List;

import org.optaplanner.core.impl.solver.scope.DefaultSolverScope;

public class DefaultLevelObject<T> implements LevelObject<T>{
	
	protected final T coarseSolution;
	protected final List<T> fragmentSolutions;
	
	protected final DefaultSolverScope<T> currentScope;
	
	
	public DefaultLevelObject(T coarseSolution, List<T> fragmentSolutions) {
		super();
		this.coarseSolution = coarseSolution;
		this.fragmentSolutions = fragmentSolutions;
		this.currentScope = null;
	}
	public DefaultLevelObject(T coarseSolution, List<T> fragmentSolutions, DefaultSolverScope<T> currentScope) {
		super();
		this.coarseSolution = coarseSolution;
		this.fragmentSolutions = fragmentSolutions;
		this.currentScope = currentScope;
	}
	@Override
	public T getCoarseSolution() {
		return coarseSolution;
	}
	@Override
	public List<T> getFragments() {
		return fragmentSolutions;
	}
	
	public DefaultSolverScope<T> getCurrentScope() {
		return currentScope;
	}
}
