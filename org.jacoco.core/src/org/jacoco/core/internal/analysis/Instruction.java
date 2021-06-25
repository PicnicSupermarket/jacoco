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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import org.jacoco.core.analysis.ICounter;

/**
 * Execution status of a single bytecode instruction internally used for
 * coverage analysis. The execution status is recorded separately for each
 * outgoing branch. Each instruction has at least one branch, for example in
 * case of a simple sequence of instructions (by convention branch 0). Instances
 * of this class are used in two steps:
 *
 * <h2>Step 1: Building the CFG</h2>
 *
 * For each bytecode instruction of a method a {@link Instruction} instance is
 * created. In correspondence with the CFG these instances are linked with each
 * other with the <code>addBranch()</code> methods. The executions count is
 * either directly derived from a probe which has been inserted in the execution
 * flow ({@link #addBranch(int, int)}) or indirectly propagated along the
 * CFG edges ({@link #addBranch(Instruction, int)}).
 *
 * <h2>Step 2: Querying the Coverage Status</h2>
 *
 * After all instructions have been created and linked each instruction knows
 * its execution count and can be queried with:
 *
 * <ul>
 * <li>{@link #getLine()}</li>
 * <li>{@link #getInstructionCounter()}</li>
 * <li>{@link #getBranchCounter()}</li>
 * </ul>
 *
 * For the purpose of filtering instructions can be combined to new
 * instructions. Note that these methods create new {@link Instruction}
 * instances and do not modify the existing ones.
 *
 * <ul>
 * <li>{@link #merge(Instruction)}</li>
 * <li>{@link #replaceBranches(Collection)}</li>
 * </ul>
 */
public class Instruction {

	private final int line;

	private int branches;

	private final List<Integer> coveredBranchesCount;

	private Instruction predecessor;

	private int predecessorBranch;

	/**
	 * New instruction at the given line.
	 *
	 * @param line
	 *            source line this instruction belongs to
	 */
	public Instruction(final int line) {
		this.line = line;
		this.branches = 0;
		this.coveredBranchesCount = new ArrayList<>();
	}

	/**
	 * Adds a branch to this instruction which execution status is indirectly
	 * derived from the execution status of the target instruction. In case the
	 * branch is covered the status is propagated also to the predecessors of
	 * this instruction.
	 *
	 * Note: This method is not idempotent and must be called exactly once for
	 * every branch.
	 *
	 * @param target
	 *            target instruction of this branch
	 * @param branch
	 *            branch identifier unique for this instruction
	 */
	public void addBranch(final Instruction target, final int branch) {
		branches++;
		target.predecessor = this;
		target.predecessorBranch = branch;
		if (!target.coveredBranchesCount.isEmpty()) {
			propagateExecutedBranch(this, branch, target.coveredBranchesCount.get(branch));
		}
	}

	/**
	 * Adds a branch to this instruction which execution status is directly
	 * derived from a probe. In case the branch is covered the status is
	 * propagated also to the predecessors of this instruction.
	 *
	 * Note: This method is not idempotent and must be called exactly once for
	 * every branch.
	 *
	 * @param executionCount
	 *            how often the corresponding probe has been executed
	 * @param branch
	 *            branch identifier unique for this instruction
	 */
	public void addBranch(final int executionCount, final int branch) {
		branches++;
		if (executionCount > 0) {
			propagateExecutedBranch(this, branch, executionCount);
		}
	}

	// TODO: Friday 2021-06-25 -- In the process of refactoring this to use int[] instead of bitset
	private static void propagateExecutedBranch(Instruction insn, int branch, int count) {
		// No recursion here, as there can be very long chains of instructions
		while (insn != null) {
			if (!insn.coveredBranchesCount.isEmpty()) {
				insn.coveredBranchesCount.set(branch, insn.coveredBranchesCount.get(branch) + count);
				break;
			}
			insn.coveredBranchesCount.set(branch, count);
			branch = insn.predecessorBranch;
			insn = insn.predecessor;
		}
	}

	/**
	 * Returns the source line this instruction belongs to.
	 *
	 * @return corresponding source line
	 */
	public int getLine() {
		return line;
	}

	/**
	 * Merges information about covered branches of this instruction with
	 * another instruction.
	 *
	 * @param other
	 *            instruction to merge with
	 * @return new instance with merged branches
	 */
	public Instruction merge(final Instruction other) {
		final Instruction result = new Instruction(this.line);
		result.branches = this.branches;

		IntStream.range(0, Math.max(this.coveredBranchesCount.size(), other.coveredBranchesCount.size()))
				.forEach(i -> mergeAtIndex(this.coveredBranchesCount, other.coveredBranchesCount, result.coveredBranchesCount, i));
		return result;
	}

	private void mergeAtIndex(List<Integer> first, List<Integer> second, List<Integer> result, int i) {
		int a = first.size() <= i ? 0 : first.get(i);
		int b = second.size() <= i ? 0 : second.get(i);
		result.set(i, a + b);
	}

	/**
	 * Creates a copy of this instruction where all outgoing branches are
	 * replaced with the given instructions. The coverage status of the new
	 * instruction is derived from the status of the given instructions.
	 *
	 * @param newBranches
	 *            new branches to consider
	 * @return new instance with replaced branches
	 */
	public Instruction replaceBranches(
			final Collection<Instruction> newBranches) {
		final Instruction result = new Instruction(this.line);
		result.branches = newBranches.size();
		int idx = 0;
		for (final Instruction b : newBranches) {
			if (!b.coveredBranchesCount.isEmpty()) {
				result.coveredBranchesCount.set(idx++, b.coveredBranchesCount.stream().mapToInt(a -> a).sum());
			}
		}
		return result;
	}

	/**
	 * Returns the instruction coverage counter of this instruction. It is
	 * always 1 instruction which is covered or not. The counter also indicates the
	 * execution count for this instruction
	 *
	 * @return the instruction coverage counter
	 */
	public ICounter getInstructionCounter() {
		return coveredBranchesCount.isEmpty() ? CounterImpl.COUNTER_1_0
				: CounterImpl.getInstance(0, coveredBranchesCount.stream().mapToInt(a -> a).sum());
	}

	/**
	 * Returns the branch coverage counter of this instruction. Only
	 * instructions with at least 2 outgoing edges report branches.
	 *
	 * @return the branch coverage counter
	 */
	public ICounter getBranchCounter() {
		if (branches < 2) {
			return CounterImpl.COUNTER_0_0;
		}
		final int covered = (int) coveredBranchesCount.stream().filter(count -> count > 0).count();
		return CounterImpl.getInstance(branches - covered, covered);
	}

}
