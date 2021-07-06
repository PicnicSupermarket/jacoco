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
package org.jacoco.core.internal.instr;

import org.jacoco.core.internal.flow.IFrame;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal utility to add probes into the control flow of a method. The code
 * for a probe simply increments a certain slot of the int array. In addition
 * the probe array has to be retrieved at the beginning of the method and stored
 * in a local variable.
 */
class ProbeInserter extends MethodVisitor implements IProbeInserter {

	private final IProbeArrayStrategy arrayStrategy;

	/**
	 * <code>true</code> if method is a class or interface initialization
	 * method.
	 */
	private final boolean clinit;

	/**
	 * Position of the inserted variable.
	 */
	private final int variable;

	/**
	 * Maximum stack usage of the code to access the probe array.
	 */
	private int accessorStackSize;

	/**
	 * Creates a new {@link ProbeInserter}.
	 *
	 * @param access
	 *            access flags of the adapted method
	 * @param name
	 *            the method's name
	 * @param desc
	 *            the method's descriptor
	 * @param mv
	 *            the method visitor to which this adapter delegates calls
	 * @param arrayStrategy
	 *            callback to create the code that retrieves the reference to
	 *            the probe array
	 */
	ProbeInserter(final int access, final String name, final String desc,
			final MethodVisitor mv, final IProbeArrayStrategy arrayStrategy) {
		super(InstrSupport.ASM_API_VERSION, mv);
		this.clinit = InstrSupport.CLINIT_NAME.equals(name);
		this.arrayStrategy = arrayStrategy;
		int pos = (Opcodes.ACC_STATIC & access) == 0 ? 1 : 0;
		for (final Type t : Type.getArgumentTypes(desc)) {
			pos += t.getSize();
		}
		variable = pos;
	}

	/**
	 * Insert bytecode (a probe) to increment the position corresponding to the
	 * given {@code id} in the int[] array
	 *
	 * @param id
	 *            the position to increment
	 */
	public void insertProbe(final int id, final IFrame frame) {
		// Snapshot the current stackmap
		frame.accept(mv);

		// Retrieve the int[] containing coverage information
		mv.visitVarInsn(Opcodes.ALOAD, variable);

		// Stack[0]: [I
		// Pushes the index of the array we want to retrieve on the stack
		InstrSupport.push(mv, id);

		// Stack[1]: I
		// Stack[0]: [I
		// Retrieve the value from the array
		mv.visitInsn(Opcodes.IALOAD);

		// Stack[0]: I
		// Load the max integer value on the stack
		mv.visitLdcInsn(Integer.MAX_VALUE);

		// Stack[1]: I
		// Stack[0]: I
		// If ints are equal, jump (skips incrementing value).
		final Label label = new Label();
		mv.visitJumpInsn(Opcodes.IF_ICMPEQ, label);

		// Retrieve the int[] containing coverage information
		mv.visitVarInsn(Opcodes.ALOAD, variable);

		// Stack[0]: [I
		// Pushes the index of the array we want to retrieve on the stack
		InstrSupport.push(mv, id);

		// Stack[1]: I
		// Stack[0]: [I
		// Duplicate the top two stack items as we want to do both lookup and
		// storage.
		mv.visitInsn(Opcodes.DUP2);

		// Stack[3]: I
		// Stack[2]: [I
		// Stack[1]: I
		// Stack[0]: [I
		// Lookup a value from an integer array
		mv.visitInsn(Opcodes.IALOAD);

		// Stack[2]: I
		// Stack[1]: I
		// Stack[0]: [I
		// Add an integer with value 1 on the stack (the value we will increment
		// with)
		mv.visitInsn(Opcodes.ICONST_1);

		// Stack[3]: I
		// Stack[2]: I
		// Stack[1]: I
		// Stack[0]: [I
		// Add the value from the array and the integer with value 1
		mv.visitInsn(Opcodes.IADD);

		// Stack[2]: I
		// Stack[1]: I
		// Stack[0]: [I
		// Store the summed value in the integer array at the index which we
		// already had on the stack
		mv.visitInsn(Opcodes.IASTORE);

		// Add label to jump to.
		mv.visitLabel(label);

		// Stackmap should be the same as before we inserted the probe
		frame.accept(mv);
	}

	@Override
	public void visitCode() {
		accessorStackSize = arrayStrategy.storeInstance(mv, clinit, variable);
		mv.visitCode();
	}

	@Override
	public final void visitVarInsn(final int opcode, final int var) {
		mv.visitVarInsn(opcode, map(var));
	}

	@Override
	public final void visitIincInsn(final int var, final int increment) {
		mv.visitIincInsn(map(var), increment);
	}

	@Override
	public final void visitLocalVariable(final String name, final String desc,
			final String signature, final Label start, final Label end,
			final int index) {
		mv.visitLocalVariable(name, desc, signature, start, end, map(index));
	}

	@Override
	public AnnotationVisitor visitLocalVariableAnnotation(final int typeRef,
			final TypePath typePath, final Label[] start, final Label[] end,
			final int[] index, final String descriptor, final boolean visible) {
		final int[] newIndex = new int[index.length];
		for (int i = 0; i < newIndex.length; i++) {
			newIndex[i] = map(index[i]);
		}
		return mv.visitLocalVariableAnnotation(typeRef, typePath, start, end,
				newIndex, descriptor, visible);
	}

	@Override
	public void visitMaxs(final int maxStack, final int maxLocals) {
		// Max stack size of the probe code is 4 which can add to the
		// original stack size depending on the probe locations. The accessor
		// stack size is an absolute maximum, as the accessor code is inserted
		// at the very beginning of each method when the stack size is empty.
		final int increasedStack = Math.max(maxStack + 4, accessorStackSize);
		mv.visitMaxs(increasedStack, maxLocals + 1);
	}

	private int map(final int var) {
		if (var < variable) {
			return var;
		} else {
			return var + 1;
		}
	}

	@Override
	public final void visitFrame(final int type, final int nLocal,
			final Object[] local, final int nStack, final Object[] stack) {

		if (type != Opcodes.F_NEW) { // uncompressed frame
			throw new IllegalArgumentException(
					"ClassReader.accept() should be called with EXPAND_FRAMES flag");
		}

		final Object[] newLocal = new Object[Math.max(nLocal, variable) + 1];
		int idx = 0; // Arrays index for existing locals
		int newIdx = 0; // Array index for new locals
		int pos = 0; // Current variable position
		while (idx < nLocal || pos <= variable) {
			if (pos == variable) {
				newLocal[newIdx++] = InstrSupport.DATAFIELD_DESC;
				pos++;
			} else {
				if (idx < nLocal) {
					final Object t = local[idx++];
					newLocal[newIdx++] = t;
					pos++;
					if (t == Opcodes.LONG || t == Opcodes.DOUBLE) {
						pos++;
					}
				} else {
					// Fill unused slots with TOP
					newLocal[newIdx++] = Opcodes.TOP;
					pos++;
				}
			}
		}
		mv.visitFrame(type, newIdx, newLocal, nStack, stack);
	}

	// From https://github.com/gmu-swe/crochet/blob/2096d5d1ea7ca0ddd4b1ff7a2b679f5e6911387a/src/main/java/net/jonbell/crij/instrument/StackElementCapturingMV.java#L57
	private static Object[] removeLongsDoubleTopVal(List<Object> in) {
		ArrayList<Object> ret = new ArrayList<Object>();
		boolean lastWas2Word = false;
		for (Object n : in) {
			if ((n == Opcodes.TOP) && lastWas2Word) {
				// nop
			} else
				ret.add(n);
			if (n == Opcodes.DOUBLE || n == Opcodes.LONG)
				lastWas2Word = true;
			else
				lastWas2Word = false;
		}
		return ret.toArray();
	}

}
