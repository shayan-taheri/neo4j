/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.locking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.internal.helpers.collection.Iterators.array;
import static org.neo4j.internal.kernel.api.PropertyIndexQuery.exact;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.ExactPredicate;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

public class IndexEntryResourceTypeTest {
    private static final int labelId = 1;
    private static final int propertyId = 2;
    private static final Value value = Values.of("value");

    @Test
    void shouldProduceBackwardsCompatibleId() {
        long id = ResourceIds.indexEntryResourceId(labelId, exact(propertyId, value));
        assertThat(id).isEqualTo(6676982443481287192L);
    }

    @Test
    void shouldDifferentiateBetweenIndexes() {
        ExactPredicate pred1 = exact(1, "value");
        ExactPredicate pred2 = exact(1, "value2");
        ExactPredicate pred3 = exact(2, "value");
        ExactPredicate pred4 = exact(2, "value2");

        List<Long> ids = Arrays.asList(
                ResourceIds.indexEntryResourceId(1, array(pred1)),
                ResourceIds.indexEntryResourceId(1, array(pred2)),
                ResourceIds.indexEntryResourceId(1, array(pred3)),
                ResourceIds.indexEntryResourceId(1, array(pred4)),
                ResourceIds.indexEntryResourceId(2, array(pred1)),
                ResourceIds.indexEntryResourceId(1, array(pred1, pred2)),
                ResourceIds.indexEntryResourceId(1, array(pred1, pred2, pred3)),
                ResourceIds.indexEntryResourceId(2, array(pred1, pred2, pred3, pred4)));

        Set<Long> uniqueIds = Iterables.asSet(ids);
        assertThat(ids.size()).isEqualTo(uniqueIds.size());
    }

    @Test
    void mustBeAbleToHashAllTypesWith4xHashFunction() {
        verifyCanHashAllTypes(ResourceIds::indexEntryResourceId_4_x);
    }

    private interface IndexEntryHasher {
        long hash(long labelId, ExactPredicate[] predicates);
    }

    @SuppressWarnings({"UnnecessaryBoxing"})
    private static void verifyCanHashAllTypes(IndexEntryHasher hasher) {
        hasher.hash(42, array(exact(1, "")));
        hasher.hash(42, array(exact(1, "a")));
        hasher.hash(42, array(exact(1, new String[] {})));
        hasher.hash(42, array(exact(1, new String[] {""})));
        hasher.hash(42, array(exact(1, new String[] {"a"})));
        hasher.hash(42, array(exact(1, new String[] {"a", "b"})));
        hasher.hash(42, array(exact(1, true)));
        hasher.hash(42, array(exact(1, new boolean[] {})));
        hasher.hash(42, array(exact(1, new boolean[] {true})));
        hasher.hash(42, array(exact(1, new boolean[] {true, false})));
        hasher.hash(42, array(exact(1, Boolean.valueOf(true))));
        hasher.hash(42, array(exact(1, new Boolean[] {})));
        hasher.hash(42, array(exact(1, new Boolean[] {true})));
        hasher.hash(42, array(exact(1, new Boolean[] {true, false})));
        hasher.hash(42, array(exact(1, (byte) 1)));
        hasher.hash(42, array(exact(1, new byte[] {})));
        hasher.hash(42, array(exact(1, new byte[] {1})));
        hasher.hash(42, array(exact(1, new byte[] {1, 2})));
        hasher.hash(42, array(exact(1, Byte.valueOf((byte) 1))));
        hasher.hash(42, array(exact(1, new Byte[] {})));
        hasher.hash(42, array(exact(1, new Byte[] {1})));
        hasher.hash(42, array(exact(1, new Byte[] {1, 2})));
        hasher.hash(42, array(exact(1, (short) 1)));
        hasher.hash(42, array(exact(1, new short[] {})));
        hasher.hash(42, array(exact(1, new short[] {1})));
        hasher.hash(42, array(exact(1, new short[] {1, 2})));
        hasher.hash(42, array(exact(1, Short.valueOf((short) 1))));
        hasher.hash(42, array(exact(1, new Short[] {})));
        hasher.hash(42, array(exact(1, new Short[] {1})));
        hasher.hash(42, array(exact(1, new Short[] {1, 2})));
        hasher.hash(42, array(exact(1, 'a')));
        hasher.hash(42, array(exact(1, new char[] {})));
        hasher.hash(42, array(exact(1, new char[] {'a'})));
        hasher.hash(42, array(exact(1, new char[] {'a', 'b'})));
        hasher.hash(42, array(exact(1, Character.valueOf('a'))));
        hasher.hash(42, array(exact(1, new Character[] {})));
        hasher.hash(42, array(exact(1, new Character[] {'a'})));
        hasher.hash(42, array(exact(1, new Character[] {'a', 'b'})));
        hasher.hash(42, array(exact(1, (float) 1)));
        hasher.hash(42, array(exact(1, new float[] {})));
        hasher.hash(42, array(exact(1, new float[] {1})));
        hasher.hash(42, array(exact(1, new float[] {1, 2})));
        hasher.hash(42, array(exact(1, Float.valueOf(1))));
        hasher.hash(42, array(exact(1, new Float[] {})));
        hasher.hash(42, array(exact(1, new Float[] {1.0f})));
        hasher.hash(42, array(exact(1, new Float[] {1.0f, 2.0f})));
        hasher.hash(42, array(exact(1, 1)));
        hasher.hash(42, array(exact(1, new int[] {})));
        hasher.hash(42, array(exact(1, new int[] {1})));
        hasher.hash(42, array(exact(1, new int[] {1, 2})));
        hasher.hash(42, array(exact(1, Integer.valueOf(1))));
        hasher.hash(42, array(exact(1, new Integer[] {})));
        hasher.hash(42, array(exact(1, new Integer[] {1})));
        hasher.hash(42, array(exact(1, new Integer[] {1, 2})));
        hasher.hash(42, array(exact(1, 1)));
        hasher.hash(42, array(exact(1, new long[] {})));
        hasher.hash(42, array(exact(1, new long[] {1})));
        hasher.hash(42, array(exact(1, new long[] {1, 2})));
        hasher.hash(42, array(exact(1, Long.valueOf(1))));
        hasher.hash(42, array(exact(1, new Long[] {})));
        hasher.hash(42, array(exact(1, new Long[] {1L})));
        hasher.hash(42, array(exact(1, new Long[] {1L, 2L})));
        hasher.hash(42, array(exact(1, 1.0)));
        hasher.hash(42, array(exact(1, new double[] {})));
        hasher.hash(42, array(exact(1, new double[] {1})));
        hasher.hash(42, array(exact(1, new double[] {1, 2})));
        hasher.hash(42, array(exact(1, Double.valueOf(1.0))));
        hasher.hash(42, array(exact(1, new Double[] {})));
        hasher.hash(42, array(exact(1, new Double[] {1.0})));
        hasher.hash(42, array(exact(1, new Double[] {1.0, 2.0})));

        hasher.hash(42, array(exact(1, ""), exact(~1, "")));
        hasher.hash(42, array(exact(1, "a"), exact(~1, "a")));
        hasher.hash(42, array(exact(1, new String[] {}), exact(~1, new String[] {})));
        hasher.hash(42, array(exact(1, new String[] {""}), exact(~1, new String[] {""})));
        hasher.hash(42, array(exact(1, new String[] {"a"}), exact(~1, new String[] {"a"})));
        hasher.hash(42, array(exact(1, new String[] {"a", "b"}), exact(~1, new String[] {"a", "b"})));
        hasher.hash(42, array(exact(1, true), exact(~1, true)));
        hasher.hash(42, array(exact(1, new boolean[] {}), exact(~1, new boolean[] {})));
        hasher.hash(42, array(exact(1, new boolean[] {true}), exact(~1, new boolean[] {true})));
        hasher.hash(42, array(exact(1, new boolean[] {true, false}), exact(~1, new boolean[] {true, false})));
        hasher.hash(42, array(exact(1, Boolean.valueOf(true)), exact(~1, Boolean.valueOf(true))));
        hasher.hash(42, array(exact(1, new Boolean[] {}), exact(~1, new Boolean[] {})));
        hasher.hash(42, array(exact(1, new Boolean[] {true}), exact(~1, new Boolean[] {true})));
        hasher.hash(42, array(exact(1, new Boolean[] {true, false}), exact(~1, new Boolean[] {true, false})));
        hasher.hash(42, array(exact(1, (byte) 1), exact(~1, (byte) 1)));
        hasher.hash(42, array(exact(1, new byte[] {}), exact(~1, new byte[] {})));
        hasher.hash(42, array(exact(1, new byte[] {1}), exact(~1, new byte[] {1})));
        hasher.hash(42, array(exact(1, new byte[] {1, 2}), exact(~1, new byte[] {1, 2})));
        hasher.hash(42, array(exact(1, Byte.valueOf((byte) 1)), exact(~1, Byte.valueOf((byte) 1))));
        hasher.hash(42, array(exact(1, new Byte[] {}), exact(~1, new Byte[] {})));
        hasher.hash(42, array(exact(1, new Byte[] {1}), exact(~1, new Byte[] {1})));
        hasher.hash(42, array(exact(1, new Byte[] {1, 2}), exact(~1, new Byte[] {1, 2})));
        hasher.hash(42, array(exact(1, (short) 1), exact(~1, (short) 1)));
        hasher.hash(42, array(exact(1, new short[] {}), exact(~1, new short[] {})));
        hasher.hash(42, array(exact(1, new short[] {1}), exact(~1, new short[] {1})));
        hasher.hash(42, array(exact(1, new short[] {1, 2}), exact(~1, new short[] {1, 2})));
        hasher.hash(42, array(exact(1, Short.valueOf((short) 1)), exact(~1, Short.valueOf((short) 1))));
        hasher.hash(42, array(exact(1, new Short[] {}), exact(~1, new Short[] {})));
        hasher.hash(42, array(exact(1, new Short[] {1}), exact(~1, new Short[] {1})));
        hasher.hash(42, array(exact(1, new Short[] {1, 2}), exact(~1, new Short[] {1, 2})));
        hasher.hash(42, array(exact(1, 'a'), exact(~1, 'a')));
        hasher.hash(42, array(exact(1, new char[] {}), exact(~1, new char[] {})));
        hasher.hash(42, array(exact(1, new char[] {'a'}), exact(~1, new char[] {'a'})));
        hasher.hash(42, array(exact(1, new char[] {'a', 'b'}), exact(~1, new char[] {'a', 'b'})));
        hasher.hash(42, array(exact(1, Character.valueOf('a')), exact(~1, Character.valueOf('a'))));
        hasher.hash(42, array(exact(1, new Character[] {}), exact(~1, new Character[] {})));
        hasher.hash(42, array(exact(1, new Character[] {'a'}), exact(~1, new Character[] {'a'})));
        hasher.hash(42, array(exact(1, new Character[] {'a', 'b'}), exact(~1, new Character[] {'a', 'b'})));
        hasher.hash(42, array(exact(1, (float) 1), exact(~1, (float) 1)));
        hasher.hash(42, array(exact(1, new float[] {}), exact(~1, new float[] {})));
        hasher.hash(42, array(exact(1, new float[] {1}), exact(~1, new float[] {1})));
        hasher.hash(42, array(exact(1, new float[] {1, 2}), exact(~1, new float[] {1, 2})));
        hasher.hash(42, array(exact(1, Float.valueOf(1)), exact(~1, Float.valueOf(1))));
        hasher.hash(42, array(exact(1, new Float[] {}), exact(~1, new Float[] {})));
        hasher.hash(42, array(exact(1, new Float[] {1.0f}), exact(~1, new Float[] {1.0f})));
        hasher.hash(42, array(exact(1, new Float[] {1.0f, 2.0f}), exact(~1, new Float[] {1.0f, 2.0f})));
        hasher.hash(42, array(exact(1, 1), exact(~1, 1)));
        hasher.hash(42, array(exact(1, new int[] {}), exact(~1, new int[] {})));
        hasher.hash(42, array(exact(1, new int[] {1}), exact(~1, new int[] {1})));
        hasher.hash(42, array(exact(1, new int[] {1, 2}), exact(~1, new int[] {1, 2})));
        hasher.hash(42, array(exact(1, Integer.valueOf(1)), exact(~1, Integer.valueOf(1))));
        hasher.hash(42, array(exact(1, new Integer[] {}), exact(~1, new Integer[] {})));
        hasher.hash(42, array(exact(1, new Integer[] {1}), exact(~1, new Integer[] {1})));
        hasher.hash(42, array(exact(1, new Integer[] {1, 2}), exact(~1, new Integer[] {1, 2})));
        hasher.hash(42, array(exact(1, 1), exact(~1, 1)));
        hasher.hash(42, array(exact(1, new long[] {}), exact(~1, new long[] {})));
        hasher.hash(42, array(exact(1, new long[] {1}), exact(~1, new long[] {1})));
        hasher.hash(42, array(exact(1, new long[] {1, 2}), exact(~1, new long[] {1, 2})));
        hasher.hash(42, array(exact(1, Long.valueOf(1)), exact(~1, Long.valueOf(1))));
        hasher.hash(42, array(exact(1, new Long[] {}), exact(~1, new Long[] {})));
        hasher.hash(42, array(exact(1, new Long[] {1L}), exact(~1, new Long[] {1L})));
        hasher.hash(42, array(exact(1, new Long[] {1L, 2L}), exact(~1, new Long[] {1L, 2L})));
        hasher.hash(42, array(exact(1, 1.0), exact(~1, 1.0)));
        hasher.hash(42, array(exact(1, new double[] {}), exact(~1, new double[] {})));
        hasher.hash(42, array(exact(1, new double[] {1}), exact(~1, new double[] {1})));
        hasher.hash(42, array(exact(1, new double[] {1, 2}), exact(~1, new double[] {1, 2})));
        hasher.hash(42, array(exact(1, Double.valueOf(1.0)), exact(~1, Double.valueOf(1.0))));
        hasher.hash(42, array(exact(1, new Double[] {}), exact(~1, new Double[] {})));
        hasher.hash(42, array(exact(1, new Double[] {1.0}), exact(~1, new Double[] {1.0})));
        hasher.hash(42, array(exact(1, new Double[] {1.0, 2.0}), exact(~1, new Double[] {1.0, 2.0})));
    }
}
