package org.optaplanner.core.impl.multilevelphase;

import java.util.List;

import org.optaplanner.core.impl.solver.scope.DefaultSolverScope;

public interface MultiLevelProvider<Solution_, V extends LevelObject<Solution_>> {
	
	static class Tuple<A,B> {
		public Tuple(A a, B b) {
			super();
			this.a = a;
			this.b = b;
		}
		public A getA() {
			return a;
		}
		public void setA(A a) {
			this.a = a;
		}
		public B getB() {
			return b;
		}
		public void setB(B b) {
			this.b = b;
		}
		public A a;
		public B b;
	}
	
	/**
	 * Used to adjust fragments by the coarser solution. 
	 * @param levelObject
	 */
	void adjustFragments(LevelObject<Solution_> levelObject, Solution_ coarseSolution);
		
	/**
	 *
	 * @param solverScope
	 * @return if there is no coarse, return parametered solution
	 */
	LevelObject<Solution_> coarse(DefaultSolverScope<Solution_> solverScope); // ich muss eigentlich ein tupel zurück geben: Solution_, V
	
	Solution_ extendSolution(LevelObject<Solution_> levelObj, DefaultSolverScope<Solution_> coarseScope,
			List<DefaultSolverScope<Solution_>> fragments, DefaultSolverScope<Solution_> original);

}
