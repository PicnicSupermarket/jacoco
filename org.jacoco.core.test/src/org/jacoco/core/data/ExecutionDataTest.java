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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link ExecutionData}.
 */
public class ExecutionDataTest {

	@Test
	public void testCreateEmpty() {
		final ExecutionData e = new ExecutionData(5, "Example", 3);
		assertEquals(5, e.getId());
		assertEquals("Example", e.getName());
		assertEquals(3, e.getProbes().length);
		assertFalse(e.getProbes()[0] > 0);
		assertFalse(e.getProbes()[1] > 0);
		assertFalse(e.getProbes()[2] > 0);
	}

	@Test
	public void testGetters() {
		final int[] data = new int[0];
		final ExecutionData e = new ExecutionData(5, "Example", data);
		assertEquals(5, e.getId());
		assertEquals("Example", e.getName());
		assertSame(data, e.getProbes());
	}

	@Test
	public void testReset() {
		final ExecutionData e = new ExecutionData(5, "Example",
				new int[] { 1, 0, 2 });
		e.reset();
		assertFalse(e.getProbes()[0] > 0);
		assertFalse(e.getProbes()[1] > 0);
		assertFalse(e.getProbes()[2] > 0);
	}

	@Test
	public void testHasHits() {
		final int[] probes = new int[] { 0, 0, 0 };
		final ExecutionData e = new ExecutionData(5, "Example", probes);
		assertFalse(e.hasHits());
		probes[1] = 1;
		assertTrue(e.hasHits());
	}

	@Test
	public void testHasHits_empty() {
		final int[] probes = new int[] {};
		final ExecutionData e = new ExecutionData(5, "Example", probes);
		assertFalse(e.hasHits());
	}

	@Test
	public void testMerge() {
		final ExecutionData a = new ExecutionData(5, "Example",
				new int[] { 0, 1, 0, 2 });
		final ExecutionData b = new ExecutionData(5, "Example",
				new int[] { 0, 0, 1, 2 });
		a.merge(b);

		// b is merged into a:
		assertEquals(0, a.getProbes()[0]);
		assertEquals(1, a.getProbes()[1]);
		assertEquals(1, a.getProbes()[2]);
		assertEquals(4, a.getProbes()[3]);

		// b must not be modified:
		assertEquals(0, b.getProbes()[0]);
		assertEquals(0, b.getProbes()[1]);
		assertEquals(1, b.getProbes()[2]);
		assertEquals(2, b.getProbes()[3]);
	}

	@Test
	public void testMergeSubtract() {
		final ExecutionData a = new ExecutionData(5, "Example",
				new int[] { 0, 1, 0, 2 });
		final ExecutionData b = new ExecutionData(5, "Example",
				new int[] { 0, 0, 1, 2 });
		a.merge(b, false);

		// b is subtracted from a:
		assertEquals(0, a.getProbes()[0]);
		assertEquals(1, a.getProbes()[1]);
		assertEquals(0, a.getProbes()[2]);
		assertEquals(0, a.getProbes()[3]);

		// b must not be modified:
		assertEquals(0, b.getProbes()[0]);
		assertEquals(0, b.getProbes()[1]);
		assertEquals(1, b.getProbes()[2]);
		assertEquals(2, b.getProbes()[3]);
	}

	@Test
	public void testAssertCompatibility() {
		final ExecutionData a = new ExecutionData(5, "Example",
				new int[] { 1 });
		a.assertCompatibility(5, "Example", 1);
	}

	@Test(expected = IllegalStateException.class)
	public void testAssertCompatibilityNegative1() {
		final ExecutionData a = new ExecutionData(5, "Example",
				new int[] { 1 });
		a.assertCompatibility(55, "Example", 1);
	}

	@Test(expected = IllegalStateException.class)
	public void testAssertCompatibilityNegative2() {
		final ExecutionData a = new ExecutionData(5, "Example",
				new int[] { 1 });
		a.assertCompatibility(5, "Exxxample", 1);
	}

	@Test(expected = IllegalStateException.class)
	public void testAssertCompatibilityNegative3() {
		final ExecutionData a = new ExecutionData(5, "Example",
				new int[] { 1 });
		a.assertCompatibility(5, "Example", 3);
	}

	@Test
	public void testToString() {
		final ExecutionData a = new ExecutionData(Long.MAX_VALUE, "Example",
				new int[] { 1 });
		assertEquals("ExecutionData[name=Example, id=7fffffffffffffff]",
				a.toString());
	}

}
