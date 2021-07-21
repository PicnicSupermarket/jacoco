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
package org.jacoco.core.data;

import static java.lang.String.format;

import java.util.Arrays;

/**
 * Execution data for a single Java class. While instances are immutable care
 * has to be taken about the probe data array of type <code>int[]</code> which
 * can be modified.
 */
public final class ExecutionData {

	private final long id;

	private final String name;

	private final int[] probes;

	/**
	 * Creates a new {@link ExecutionData} object with the given probe data.
	 *
	 * @param id
	 *            class identifier
	 * @param name
	 *            VM name
	 * @param probes
	 *            probe data
	 */
	public ExecutionData(final long id, final String name, final int[] probes) {
		this.id = id;
		this.name = name;
		this.probes = probes;
	}

	/**
	 * Creates a new {@link ExecutionData} object with the given probe data
	 * length. All probes are set to <code>false</code>.
	 *
	 * @param id
	 *            class identifier
	 * @param name
	 *            VM name
	 * @param probeCount
	 *            probe count
	 */
	public ExecutionData(final long id, final String name,
			final int probeCount) {
		this.id = id;
		this.name = name;
		this.probes = new int[probeCount];
	}

	/**
	 * Return the unique identifier for this class. The identifier is the CRC64
	 * checksum of the raw class file definition.
	 *
	 * @return class identifier
	 */
	public long getId() {
		return id;
	}

	/**
	 * The VM name of the class.
	 *
	 * @return VM name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the execution data probes. A value greater of <code>0</code>
	 * indicates that the corresponding probe was executed at least once.
	 *
	 * @return probe data
	 */
	public int[] getProbes() {
		return probes;
	}

	/**
	 * Sets all probes to <code>0</code>.
	 */
	public void reset() {
		Arrays.fill(probes, 0);
	}

	/**
	 * Checks whether any probe has been hit.
	 *
	 * @return <code>true</code>, if at least one probe has been hit
	 */
	public boolean hasHits() {
		for (final int p : probes) {
			if (p > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Merges the given execution data into the probe data of this object. I.e.
	 * a probe entry in this object is marked as executed (<code>true</code>) if
	 * this probe or the corresponding other probe was executed. The counts of
	 * those probes are added together. So the result is
	 *
	 * <pre>
	 * A + B
	 * </pre>
	 *
	 * The probe array of the other object is not modified.
	 *
	 * @param other
	 *            execution data to merge
	 */
	public void merge(final ExecutionData other) {
		merge(other, true);
	}

	/**
	 * Merges the given execution data into the probe data of this object. A
	 * probe in this object is added to that of the other probe if
	 * <code>flag</code> was set to true. For <code>flag==true</code> this
	 * corresponds to
	 *
	 * <pre>
	 * A + B
	 * </pre>
	 *
	 * For <code>flag==false</code> this can be considered as a subtraction
	 *
	 * <pre>
	 * A - B
	 * </pre>
	 *
	 * The probe array of the other object is not modified.
	 *
	 * @param other
	 *            execution data to merge
	 * @param flag
	 *            merge mode
	 */
	public void merge(final ExecutionData other, final boolean flag) {
		assertCompatibility(other.getId(), other.getName(),
				other.getProbes().length);
		final int[] otherData = other.getProbes();
		for (int i = 0; i < probes.length; i++) {
			int otherProbe = otherData[i];
			if (otherProbe > 0) {
				if (flag) {
					int sum = probes[i] + otherProbe;
					if (sum == Integer.MAX_VALUE || sum < 0) {
						// Prevent integer overflow by capping at MAX_VALUE - 1
						// Note, we can not allow MAX_VALUE itself either
						// because
						// that would result in the Math.min implementation of
						// the
						// probe to overflow on increment.
						sum = Integer.MAX_VALUE - 1;
					}
					probes[i] = sum;
				} else {
					probes[i] = Math.max(probes[i] - otherProbe, 0);
				}
			}
		}
	}

	/**
	 * Asserts that this execution data object is compatible with the given
	 * parameters. The purpose of this check is to detect a very unlikely class
	 * id collision.
	 *
	 * @param id
	 *            other class id, must be the same
	 * @param name
	 *            other name, must be equal to this name
	 * @param probecount
	 *            probe data length, must be the same as for this data
	 * @throws IllegalStateException
	 *             if the given parameters do not match this instance
	 */
	public void assertCompatibility(final long id, final String name,
			final int probecount) throws IllegalStateException {
		if (this.id != id) {
			throw new IllegalStateException(
					format("Different ids (%016x and %016x).",
							Long.valueOf(this.id), Long.valueOf(id)));
		}
		if (!this.name.equals(name)) {
			throw new IllegalStateException(
					format("Different class names %s and %s for id %016x.",
							this.name, name, Long.valueOf(id)));
		}
		if (this.probes.length != probecount) {
			throw new IllegalStateException(format(
					"Incompatible execution data for class %s with id %016x.",
					name, Long.valueOf(id)));
		}
	}

	@Override
	public String toString() {
		return String.format("ExecutionData[name=%s, id=%016x]", name,
				Long.valueOf(id));
	}

}
