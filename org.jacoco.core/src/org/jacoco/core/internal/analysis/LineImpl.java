/*******************************************************************************
 * Copyright (c) 2009, 2021 Mountainminds GmbH & Co. KG and Contributors
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.internal.analysis;

import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;

/**
 * Implementation of {@link ILine}.
 */
public abstract class LineImpl implements ILine {

	/** Max instruction counter value for which singletons are created */
	private static final int SINGLETON_INS_LIMIT = 8;

	/** Max branch counter value for which singletons are created */
	private static final int SINGLETON_BRA_LIMIT = 4;

	private static final LineImpl[][][][][] SINGLETONS = new LineImpl[SINGLETON_INS_LIMIT
			+ 1][][][][];

	static {
		for (int i = 0; i <= SINGLETON_INS_LIMIT; i++) {
			SINGLETONS[i] = new LineImpl[SINGLETON_INS_LIMIT + 1][][][];
			for (int j = 0; j <= SINGLETON_INS_LIMIT; j++) {
				SINGLETONS[i][j] = new LineImpl[SINGLETON_INS_LIMIT + 1][][];
				for (int k = 0; k <= SINGLETON_INS_LIMIT; k++) {
					SINGLETONS[i][j][k] = new LineImpl[SINGLETON_BRA_LIMIT
							+ 1][];
					for (int l = 0; l <= SINGLETON_BRA_LIMIT; l++) {
						SINGLETONS[i][j][k][l] = new LineImpl[SINGLETON_BRA_LIMIT
								+ 1];
						for (int m = 0; m <= SINGLETON_BRA_LIMIT; m++) {
							SINGLETONS[i][j][k][l][m] = new Fix(i, j, k, l, m);
						}
					}
				}
			}
		}
	}

	/**
	 * Empty line without instructions or branches.
	 */
	public static final LineImpl EMPTY = SINGLETONS[0][0][0][0][0];

	private static LineImpl getInstance(final CounterImpl instructions,
			final int ec, final CounterImpl branches) {
		final int im = instructions.getMissedCount();
		final int ic = instructions.getCoveredCount();
		final int bm = branches.getMissedCount();
		final int bc = branches.getCoveredCount();
		if (im <= SINGLETON_INS_LIMIT && ic <= SINGLETON_INS_LIMIT
				&& bm <= SINGLETON_BRA_LIMIT && bc <= SINGLETON_BRA_LIMIT
				&& ec <= SINGLETON_INS_LIMIT) {
			return SINGLETONS[im][ic][ec][bm][bc];
		}
		return new Var(instructions, ec, branches);
	}

	/**
	 * Mutable version.
	 */
	private static final class Var extends LineImpl {
		Var(final CounterImpl instructions, final int executions,
				final CounterImpl branches) {
			super(instructions, executions, branches);
		}

		@Override
		public LineImpl increment(final ICounter instructions,
				final int executions, final ICounter branches) {
			this.instructions = this.instructions.increment(instructions);
			// Set the amount of execution on this line on the max between
			// the current executions and those to increment with.
			this.executions = Math.max(executions, this.executions);
			this.branches = this.branches.increment(branches);
			return this;
		}
	}

	/**
	 * Immutable version.
	 */
	private static final class Fix extends LineImpl {
		public Fix(final int im, final int ic, final int ec, final int bm,
				final int bc) {
			super(CounterImpl.getInstance(im, ic), ec,
					CounterImpl.getInstance(bm, bc));
		}

		@Override
		public LineImpl increment(final ICounter instructions,
				final int executions, final ICounter branches) {
			return getInstance(this.instructions.increment(instructions),
					Math.max(this.executions, executions),
					this.branches.increment(branches));
		}
	}

	/** instruction counter */
	protected CounterImpl instructions;

	/** execution count */
	protected int executions;

	/** branch counter */
	protected CounterImpl branches;

	private LineImpl(final CounterImpl instructions, final int executions,
			final CounterImpl branches) {
		this.instructions = instructions;
		this.executions = executions;
		this.branches = branches;
	}

	/**
	 * Adds the given counters to this line.
	 *
	 * @param instructions
	 *            instructions to add
	 * @param executions
	 *            executions to add
	 * @param branches
	 *            branches to add
	 * @return instance with new counter values
	 */
	public abstract LineImpl increment(final ICounter instructions,
			final int executions, final ICounter branches);

	// === ILine implementation ===

	public int getStatus() {
		return instructions.getStatus() | branches.getStatus();
	}

	public ICounter getInstructionCounter() {
		return instructions;
	}

	public int getExecutionCount() {
		return executions;
	}

	public ICounter getBranchCounter() {
		return branches;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 31 * hash + instructions.hashCode();
		hash = 31 * hash + executions;
		hash = 31 * hash + branches.hashCode();
		return hash;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof ILine) {
			final ILine that = (ILine) obj;
			return this.instructions.equals(that.getInstructionCounter())
					&& this.executions == that.getExecutionCount()
					&& this.branches.equals(that.getBranchCounter());
		}
		return false;
	}

}
