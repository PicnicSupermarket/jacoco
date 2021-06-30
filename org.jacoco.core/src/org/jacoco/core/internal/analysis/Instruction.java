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

import java.util.*;

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
 * flow ({@link #addBranch(int, int)}) or indirectly propagated along the CFG
 * edges ({@link #addBranch(Instruction, int)}).
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

	private final Map<Integer, Integer> coveredBranches;

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
		this.coveredBranches = new HashMap<Integer, Integer>();
	}

	/**
	 * Adds a branch to this instruction which execution status is indirectly
	 * derived from the execution status of the target instruction. In case the
	 * branch is covered the status is propagated also to the predecessors of
	 * this instruction. For this, we take the sum of the target branches'
	 * execution count.
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
		if (!target.coveredBranches.isEmpty()) {
			// As an instruction can only have one predecessor, we assume
			// that every execution on the target's branches is coming from
			// the current instruction. Therefore, we must sum the target's
			// branch executions.
			propagateExecutedBranch(this, branch,
					getListSum(target.coveredBranches.values()));
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

	private static void propagateExecutedBranch(Instruction insn, int branch,
			int count) {
		// No recursion here, as there can be very long chains of instructions
		while (insn != null) {
			if (!insn.coveredBranches.isEmpty()) {
				// As propagation happens multiple times in the chain, we can
				// not just increment the execution count. Instead, we need to
				// set it to the max of the value that we already have or the
				// value that is coming in from upstream. Otherwise, we will
				// count more executions than actually happened.
				Integer existing = insn.coveredBranches.get(branch);
				insn.coveredBranches.put(branch,
						existing == null ? count : Math.max(existing, count));
				break;
			}
			insn.coveredBranches.put(branch, count);
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
		result.coveredBranches.putAll(this.coveredBranches);

		for (Map.Entry<Integer, Integer> entry : other.coveredBranches
				.entrySet()) {
			if (result.coveredBranches.containsKey(entry.getKey())) {
				// We have already covered the branch before so we need
				// to add the two instructions together.
				result.coveredBranches.put(entry.getKey(),
						result.coveredBranches.get(entry.getKey())
								+ entry.getValue());
			} else {
				// The branch was not covered before, so we can just set it
				// to the value of the other instruction.
				result.coveredBranches.put(entry.getKey(), entry.getValue());
			}
		}

		return result;
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
			if (!b.coveredBranches.isEmpty()) {
				// The instruction that we need to replace the branch with
				// has been executed before. To not lose execution count
				// data, we sum all of its execution counts on every branch
				// and set it to the current instruction's branch that will
				// be replaced.
				result.coveredBranches.put(idx++,
						getListSum(b.coveredBranches.values()));
			}
		}
		return result;
	}

	/**
	 * Returns the instruction coverage counter of this instruction. It is
	 * always 1 instruction which is covered or not.
	 *
	 * @return the instruction coverage counter
	 */
	public ICounter getInstructionCounter() {
		return coveredBranches.isEmpty() ? CounterImpl.COUNTER_1_0
				: CounterImpl.COUNTER_0_1;
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
		int covered = 0;
		for (int count : coveredBranches.values()) {
			if (count > 0) {
				covered++;
			}
		}
		return CounterImpl.getInstance(branches - covered, covered);
	}

	/**
	 * Returns the count indicating how often this instruction has been
	 * executed. The number is a max of all counts on every branch.
	 *
	 * @return the instruction execution count
	 */
	public int getExecutionCount() {
		return coveredBranches.isEmpty() ? 0
				: getListSum(coveredBranches.values());
	}

	private int getListSum(Collection<Integer> list) {
		int sum = 0;
		for (int integer : list) {
			sum += integer;
		}
		return sum;
	}
}
